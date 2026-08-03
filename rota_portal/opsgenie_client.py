from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone

from config import OPS_API_BASE, OPS_API_KEY_FILE, ROTATION_NAME, SCHEDULE_NAME


class OpsgenieError(RuntimeError):
    def __init__(self, message: str, status_code: int | None = None) -> None:
        super().__init__(message)
        self.status_code = status_code


@dataclass(frozen=True)
class OverrideRequest:
    member_name: str
    username: str
    start: str
    end: str
    alias: str


def read_api_key() -> str:
    try:
        return OPS_API_KEY_FILE.read_text(encoding="utf-8").strip()
    except FileNotFoundError as exc:
        raise OpsgenieError(f"Opsgenie API key file not found: {OPS_API_KEY_FILE}") from exc


def _request(method: str, path: str, body: dict | None = None) -> dict:
    api_key = read_api_key()
    url = f"{OPS_API_BASE}{path}"
    data = None
    headers = {
        "Authorization": f"GenieKey {api_key}",
        "Content-Type": "application/json",
    }
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")
        raise OpsgenieError(f"Opsgenie API returned HTTP {exc.code}: {details}", exc.code) from exc
    except urllib.error.URLError as exc:
        raise OpsgenieError(f"Opsgenie API request failed: {exc.reason}") from exc


def _override_body(override: OverrideRequest, include_alias: bool) -> dict:
    body = {
        "user": {
            "type": "user",
            "username": override.username,
        },
        "startDate": override.start,
        "endDate": override.end,
        "rotations": [
            {
                "name": ROTATION_NAME,
            }
        ],
    }
    if include_alias:
        body["alias"] = override.alias
    return body


def create_override(override: OverrideRequest) -> dict:
    schedule = urllib.parse.quote(SCHEDULE_NAME, safe="")
    body = _override_body(override, include_alias=True)
    return _request("POST", f"/v2/schedules/{schedule}/overrides?scheduleIdentifierType=name", body)


def update_override(override: OverrideRequest) -> dict:
    schedule = urllib.parse.quote(SCHEDULE_NAME, safe="")
    alias = urllib.parse.quote(override.alias, safe="")
    body = _override_body(override, include_alias=False)
    return _request("PUT", f"/v2/schedules/{schedule}/overrides/{alias}?scheduleIdentifierType=name", body)


def delete_override(alias: str) -> dict:
    schedule = urllib.parse.quote(SCHEDULE_NAME, safe="")
    quoted = urllib.parse.quote(alias, safe="")
    return _request("DELETE", f"/v2/schedules/{schedule}/overrides/{quoted}?scheduleIdentifierType=name")


def list_overrides() -> dict:
    schedule = urllib.parse.quote(SCHEDULE_NAME, safe="")
    return _request("GET", f"/v2/schedules/{schedule}/overrides?scheduleIdentifierType=name")


def get_schedule_timeline(date: str, interval: int = 1, interval_unit: str = "months") -> dict:
    """Fetch Opsgenie Final timeline for a schedule month/window."""
    schedule = urllib.parse.quote(SCHEDULE_NAME, safe="")
    query = urllib.parse.urlencode({
        "identifierType": "name",
        "interval": str(interval),
        "intervalUnit": interval_unit,
        "date": date,
    })
    return _request("GET", f"/v2/schedules/{schedule}/timeline?{query}")


def _parse_date(value: str) -> datetime:
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    return datetime.fromisoformat(value).astimezone(timezone.utc)


def _format_date(value: datetime) -> str:
    return value.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _alias_for(start: str, end: str, suffix: str = "") -> str:
    compact_start = start.replace(":", "").replace("-", "").replace("T", "-").replace("Z", "")
    compact_end = end.replace(":", "").replace("-", "").replace("T", "-").replace("Z", "")
    base = f"rota-portal-{compact_start}-{compact_end}"
    return f"{base}-{suffix}" if suffix else base


def find_existing_override_alias(start: str, end: str) -> str | None:
    payload = list_overrides()
    requested_start = _parse_date(start)
    requested_end = _parse_date(end)
    for override in payload.get("data", []):
        try:
            existing_start = _parse_date(override["startDate"])
            existing_end = _parse_date(override["endDate"])
        except (KeyError, ValueError):
            continue
        if existing_start == requested_start and existing_end == requested_end:
            return override.get("alias")
    return None


def carve_overlapping_overrides(start: str, end: str) -> list[dict]:
    """Remove/split overrides that would mask [start, end) in Final timeline.

    Opsgenie keeps overlapping overrides; a longer covering override can hide a
    shorter one. For a targeted window we delete each overlapping non-exact
    override and recreate any left/right remainders for the original owner.
    """
    requested_start = _parse_date(start)
    requested_end = _parse_date(end)
    actions: list[dict] = []
    for override in list_overrides().get("data", []):
        alias = override.get("alias")
        username = (override.get("user") or {}).get("username")
        if not alias or not username:
            continue
        try:
            existing_start = _parse_date(override["startDate"])
            existing_end = _parse_date(override["endDate"])
        except (KeyError, ValueError):
            continue
        if existing_end <= requested_start or existing_start >= requested_end:
            continue
        if existing_start == requested_start and existing_end == requested_end:
            continue

        delete_override(alias)
        action: dict = {
            "deletedAlias": alias,
            "originalUser": username,
            "originalStart": _format_date(existing_start),
            "originalEnd": _format_date(existing_end),
            "remainders": [],
        }

        if existing_start < requested_start:
            left_start = _format_date(existing_start)
            left_end = _format_date(requested_start)
            if not find_existing_override_alias(left_start, left_end):
                left = OverrideRequest(
                    member_name=username,
                    username=username,
                    start=left_start,
                    end=left_end,
                    alias=_alias_for(left_start, left_end, "remainder"),
                )
                create_override(left)
                action["remainders"].append({"start": left_start, "end": left_end, "alias": left.alias})
            else:
                action["remainders"].append({
                    "start": left_start,
                    "end": left_end,
                    "alias": None,
                    "keptExisting": True,
                })

        if requested_end < existing_end:
            right_start = _format_date(requested_end)
            right_end = _format_date(existing_end)
            if not find_existing_override_alias(right_start, right_end):
                right = OverrideRequest(
                    member_name=username,
                    username=username,
                    start=right_start,
                    end=right_end,
                    alias=_alias_for(right_start, right_end, "remainder"),
                )
                create_override(right)
                action["remainders"].append({"start": right_start, "end": right_end, "alias": right.alias})
            else:
                action["remainders"].append({
                    "start": right_start,
                    "end": right_end,
                    "alias": None,
                    "keptExisting": True,
                })

        actions.append(action)
    return actions


def create_or_update_override(
    override: OverrideRequest,
    *,
    carve: bool = False,
) -> tuple[str, str, dict, list[dict]]:
    carved: list[dict] = []
    if carve:
        carved = carve_overlapping_overrides(override.start, override.end)

    existing_alias = find_existing_override_alias(override.start, override.end)
    if existing_alias:
        replacement = OverrideRequest(
            member_name=override.member_name,
            username=override.username,
            start=override.start,
            end=override.end,
            alias=existing_alias,
        )
        return "updated", existing_alias, update_override(replacement), carved

    try:
        return "created", override.alias, create_override(override), carved
    except OpsgenieError as exc:
        if exc.status_code != 422:
            raise
        return "updated", override.alias, update_override(override), carved
