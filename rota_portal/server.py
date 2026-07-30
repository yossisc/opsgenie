#!/usr/bin/env python3
from __future__ import annotations

import json
import mimetypes
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from config import DEFAULT_ASSIGNABLE_MEMBERS, PORT, ROTATION_NAME, SCHEDULE_NAME, TEAM_MEMBERS
from opsgenie_client import (
    OpsgenieError,
    OverrideRequest,
    create_or_update_override,
    get_schedule_timeline,
    list_overrides,
)
from schedule_engine import team_payload


ROOT = Path(__file__).resolve().parent
STATIC = ROOT / "static"


def json_response(handler: BaseHTTPRequestHandler, status: HTTPStatus, payload: dict) -> None:
    body = json.dumps(payload, indent=2).encode("utf-8")
    handler.send_response(status.value)
    handler.send_header("Content-Type", "application/json")
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()
    handler.wfile.write(body)


def parse_body(handler: BaseHTTPRequestHandler) -> dict:
    length = int(handler.headers.get("Content-Length", "0"))
    raw = handler.rfile.read(length).decode("utf-8") if length else "{}"
    return json.loads(raw)


def parse_year_month(query: dict[str, list[str]]) -> tuple[int, int]:
    now = datetime.now()
    year = int(query.get("year", [str(now.year)])[0])
    month = int(query.get("month", [str(now.month)])[0])
    if month < 1 or month > 12:
        raise ValueError("month must be between 1 and 12")
    return year, month


def make_alias(start: str, end: str, suffix: str = "") -> str:
    compact_start = start.replace(":", "").replace("-", "").replace("T", "-").replace("Z", "")
    compact_end = end.replace(":", "").replace("-", "").replace("T", "-").replace("Z", "")
    base = f"rota-portal-{compact_start}-{compact_end}"
    return f"{base}-{suffix}" if suffix else base


def normalize_iso_z(value: str) -> str:
    try:
        parsed = datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    except ValueError as exc:
        raise ValueError("date must use yyyy-MM-ddTHH:mm:ssZ") from exc
    return parsed.strftime("%Y-%m-%dT%H:%M:%SZ")


def validate_range(start: str, end: str) -> tuple[str, str]:
    start = normalize_iso_z(start)
    end = normalize_iso_z(end)
    start_dt = datetime.strptime(start, "%Y-%m-%dT%H:%M:%SZ")
    end_dt = datetime.strptime(end, "%Y-%m-%dT%H:%M:%SZ")
    if end_dt <= start_dt:
        raise ValueError("end must be after start")
    return start, end


def parse_opsgenie_date(value: str) -> datetime:
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    return datetime.fromisoformat(value).astimezone(timezone.utc)


def month_bounds(year: int, month: int) -> tuple[datetime, datetime]:
    start = datetime(year, month, 1, tzinfo=timezone.utc)
    if month == 12:
        end = datetime(year + 1, 1, 1, tzinfo=timezone.utc)
    else:
        end = datetime(year, month + 1, 1, tzinfo=timezone.utc)
    return start, end


def username_to_member(username: str | None) -> str:
    for member, email in TEAM_MEMBERS.items():
        if email == username:
            return member
    return username or "unknown"


def month_override_payload(year: int, month: int) -> tuple[list[dict], str | None]:
    start_bound, end_bound = month_bounds(year, month)
    try:
        payload = list_overrides()
    except OpsgenieError as exc:
        return [], str(exc)

    overrides = []
    for override in payload.get("data", []):
        try:
            start_dt = parse_opsgenie_date(override["startDate"])
            end_dt = parse_opsgenie_date(override["endDate"])
        except (KeyError, ValueError):
            continue
        if end_dt <= start_bound or start_dt >= end_bound:
            continue
        username = override.get("user", {}).get("username")
        overrides.append({
            "alias": override.get("alias", ""),
            "member": username_to_member(username),
            "username": username,
            "start": start_dt.strftime("%Y-%m-%dT%H:%M:%SZ"),
            "end": end_dt.strftime("%Y-%m-%dT%H:%M:%SZ"),
            "partial": False,
        })
    return overrides, None


def normalize_timeline_date(value: str) -> str:
    return parse_opsgenie_date(value).strftime("%Y-%m-%dT%H:%M:%SZ")


def month_final_periods(year: int, month: int) -> tuple[list[dict], str | None]:
    """Resolved Opsgenie Final on-call periods for the calendar month."""
    start_bound, end_bound = month_bounds(year, month)
    # Timeline window is timezone-aware in Opsgenie; start a day early so the
    # first local-night periods that spill into the month are included.
    request_date = (start_bound).strftime("%Y-%m-%dT%H:%M:%SZ")
    try:
        payload = get_schedule_timeline(request_date, interval=1, interval_unit="months")
    except OpsgenieError as exc:
        return [], str(exc)

    periods: list[dict] = []
    rotations = payload.get("data", {}).get("finalTimeline", {}).get("rotations", [])
    for rotation in rotations:
        if rotation.get("name") and rotation.get("name") != ROTATION_NAME:
            continue
        for period in rotation.get("periods", []):
            try:
                start = normalize_timeline_date(period["startDate"])
                end = normalize_timeline_date(period["endDate"])
                start_dt = parse_opsgenie_date(start)
                end_dt = parse_opsgenie_date(end)
            except (KeyError, ValueError):
                continue
            if end_dt <= start_bound or start_dt >= end_bound:
                continue
            username = (period.get("recipient") or {}).get("name")
            member = username_to_member(username) if username else "Unassigned"
            period_type = period.get("type") or "default"
            periods.append({
                "id": f"{start}_{end}_{member}_{period_type}".replace(":", ""),
                "member": member,
                "username": username,
                "start": start,
                "end": end,
                "type": period_type,
                "rotationName": rotation.get("name") or ROTATION_NAME,
            })
    periods.sort(key=lambda item: item["start"])
    return merge_contiguous_periods(periods), None


def merge_contiguous_periods(periods: list[dict]) -> list[dict]:
    """Collapse abutting same-person periods so calendar days show one block."""
    if not periods:
        return []
    merged: list[dict] = []
    current = dict(periods[0])
    for period in periods[1:]:
        same_person = current["member"] == period["member"] and current["username"] == period["username"]
        contiguous = current["end"] == period["start"]
        if same_person and contiguous:
            current["end"] = period["end"]
            # Prefer override label when either side was an override.
            if period["type"] == "override" or current["type"] == "override":
                current["type"] = "override"
            current["id"] = (
                f"{current['start']}_{current['end']}_{current['member']}_{current['type']}".replace(":", "")
            )
            continue
        merged.append(current)
        current = dict(period)
    merged.append(current)
    return merged


class Handler(BaseHTTPRequestHandler):
    server_version = "RotaPortal/1.0"

    def log_message(self, fmt: str, *args: object) -> None:
        print(f"{self.address_string()} - {fmt % args}")

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/api/month":
            self.handle_month(parsed.query)
            return
        if parsed.path == "/api/overrides":
            self.handle_overrides()
            return
        self.handle_static(parsed.path)

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/api/override":
            self.handle_override()
            return
        if parsed.path == "/api/override-bulk":
            self.handle_override_bulk()
            return
        json_response(self, HTTPStatus.NOT_FOUND, {"error": "not found"})

    def handle_month(self, query_string: str) -> None:
        try:
            year, month = parse_year_month(parse_qs(query_string))
            periods, timeline_error = month_final_periods(year, month)
            overrides, overrides_error = month_override_payload(year, month)
            json_response(self, HTTPStatus.OK, {
                "scheduleName": SCHEDULE_NAME,
                "rotationName": ROTATION_NAME,
                "year": year,
                "month": month,
                "teamMembers": team_payload(TEAM_MEMBERS),
                "defaultAssignableMembers": DEFAULT_ASSIGNABLE_MEMBERS,
                "periods": periods,
                "overrides": overrides,
                "timelineError": timeline_error,
                "overridesError": overrides_error,
            })
        except Exception as exc:
            json_response(self, HTTPStatus.BAD_REQUEST, {"error": str(exc)})

    def handle_overrides(self) -> None:
        try:
            json_response(self, HTTPStatus.OK, list_overrides())
        except OpsgenieError as exc:
            json_response(self, HTTPStatus.BAD_GATEWAY, {"error": str(exc)})

    def handle_override(self) -> None:
        try:
            body = parse_body(self)
            member_name = body["member"]
            username = TEAM_MEMBERS.get(member_name)
            if not username:
                raise ValueError(f"unknown member: {member_name}")
            start, end = validate_range(body["start"], body["end"])
            suffix = body.get("suffix", "partial" if body.get("partial") else "")
            alias = body.get("alias") or make_alias(start, end, suffix)
            operation, alias, result = create_or_update_override(OverrideRequest(member_name, username, start, end, alias))
            json_response(self, HTTPStatus.OK, {
                "ok": True,
                "alias": alias,
                "operation": operation,
                "override": {
                    "member": member_name,
                    "username": username,
                    "start": start,
                    "end": end,
                    "partial": bool(body.get("partial")),
                },
                "result": result,
            })
        except OpsgenieError as exc:
            json_response(self, HTTPStatus.BAD_GATEWAY, {"ok": False, "error": str(exc)})
        except Exception as exc:
            json_response(self, HTTPStatus.BAD_REQUEST, {"ok": False, "error": str(exc)})

    def handle_override_bulk(self) -> None:
        try:
            body = parse_body(self)
            member_name = body["member"]
            username = TEAM_MEMBERS.get(member_name)
            if not username:
                raise ValueError(f"unknown member: {member_name}")
            shifts = body.get("shifts", [])
            if not isinstance(shifts, list) or not shifts:
                raise ValueError("shifts must be a non-empty list")

            results = []
            for shift in shifts:
                try:
                    start, end = validate_range(shift["start"], shift["end"])
                    alias = make_alias(start, end)
                    operation, alias, result = create_or_update_override(OverrideRequest(member_name, username, start, end, alias))
                    results.append({
                        "ok": True,
                        "entryId": shift.get("id", ""),
                        "alias": alias,
                        "operation": operation,
                        "override": {
                            "member": member_name,
                            "username": username,
                            "start": start,
                            "end": end,
                            "partial": False,
                        },
                        "result": result,
                    })
                except Exception as exc:
                    results.append({
                        "ok": False,
                        "entryId": shift.get("id", ""),
                        "error": str(exc),
                    })

            status = HTTPStatus.OK if all(item["ok"] for item in results) else HTTPStatus.MULTI_STATUS
            json_response(self, status, {
                "ok": all(item["ok"] for item in results),
                "results": results,
            })
        except Exception as exc:
            json_response(self, HTTPStatus.BAD_REQUEST, {"ok": False, "error": str(exc)})

    def handle_static(self, path: str) -> None:
        if path in ("", "/"):
            file_path = STATIC / "index.html"
        else:
            requested = path.lstrip("/")
            file_path = STATIC / requested
        try:
            resolved = file_path.resolve()
            if not str(resolved).startswith(str(STATIC.resolve())) or not resolved.is_file():
                raise FileNotFoundError
            body = resolved.read_bytes()
            content_type = mimetypes.guess_type(str(resolved))[0] or "application/octet-stream"
            self.send_response(HTTPStatus.OK.value)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            if resolved.suffix in {".html", ".js", ".css"}:
                self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            self.wfile.write(body)
        except FileNotFoundError:
            json_response(self, HTTPStatus.NOT_FOUND, {"error": "not found"})


def main() -> None:
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"Rota portal listening on http://127.0.0.1:{PORT}")
    print(f"Opsgenie schedule: {SCHEDULE_NAME}, rotation: {ROTATION_NAME}")
    server.serve_forever()


if __name__ == "__main__":
    main()
