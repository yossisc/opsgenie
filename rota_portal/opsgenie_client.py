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


def list_overrides() -> dict:
    schedule = urllib.parse.quote(SCHEDULE_NAME, safe="")
    return _request("GET", f"/v2/schedules/{schedule}/overrides?scheduleIdentifierType=name")


def _parse_date(value: str) -> datetime:
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    return datetime.fromisoformat(value).astimezone(timezone.utc)


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


def create_or_update_override(override: OverrideRequest) -> tuple[str, str, dict]:
    existing_alias = find_existing_override_alias(override.start, override.end)
    if existing_alias:
        replacement = OverrideRequest(
            member_name=override.member_name,
            username=override.username,
            start=override.start,
            end=override.end,
            alias=existing_alias,
        )
        return "updated", existing_alias, update_override(replacement)

    try:
        return "created", override.alias, create_override(override)
    except OpsgenieError as exc:
        if exc.status_code != 422:
            raise
        return "updated", override.alias, update_override(override)
