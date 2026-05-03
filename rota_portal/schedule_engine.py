from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from typing import Iterable

from config import DEFAULT_ASSIGNABLE_MEMBERS, EXTRA_MEMBER_SUBSTITUTIONS


FIRST_WORKDAY_USER = "Gabi"
FIRST_WEEKEND_USER = "Gabi"
WORKDAY_ROTATION = ["Yossi", "Nadav", "Gabi", "Gour"]
WEEKEND_ROTATION = ["Extra1", "Extra2", "Yossi", "Gabi", "Gour"]
WEEKEND_ROTATION_WHEN_DATI = ["Extra1", "Extra2", "Yossi", "Gour", "Gabi"]
WEEKLY_PAIRS = [
    ["Gabi", "Yossi"],
    ["Gour", "Nadav"],
    ["Yossi", "Gabi"],
    ["Nadav", "Gour"],
]
ANCHOR_SUNDAY = datetime(2026, 5, 3)

HOLIDAYS = {
    "01/04/2026",
    "07/04/2026",
    "21/04/2026",
    "21/05/2026",
    "11/09/2026",
    "12/09/2026",
    "20/09/2026",
    "25/09/2026",
    "02/10/2026",
}


@dataclass(frozen=True)
class ScheduleEntry:
    id: str
    date_from: str
    date_to: str
    time_from: str
    time_to: str
    start: str
    end: str
    member: str
    apply_member: str
    username: str | None
    kind: str
    day_name: str
    start_epoch_ms: int
    end_epoch_ms: int
    spans_days: int


def add_day(current_date: datetime) -> datetime:
    return current_date + timedelta(days=1)


def rotate_and_get_next_person(people: list[str]) -> str:
    people.append(people.pop(0))
    return people[-1]


def is_holiday(current_date: datetime) -> bool:
    return current_date.strftime("%d/%m/%Y") in HOLIDAYS


def format_time(time_str: str) -> str:
    time_obj = datetime.strptime(time_str, "%I:%M%p") - timedelta(hours=2)
    return "T" + time_obj.strftime("%H:%M") + "Z"


def get_week_pair(current_date: datetime) -> list[str]:
    days_since_sunday = (current_date.weekday() + 1) % 7
    week_sunday = current_date - timedelta(days=days_since_sunday)
    weeks_since_anchor = (week_sunday - ANCHOR_SUNDAY).days // 7
    return WEEKLY_PAIRS[weeks_since_anchor % len(WEEKLY_PAIRS)]


def _parse_legacy_datetime(date_value: str, time_value: str) -> datetime:
    return datetime.strptime(date_value + time_value, "%d/%m/%YT%H:%MZ").replace(tzinfo=timezone.utc)


def _entry_tuple_to_model(entry: tuple[str, str, str, str, str, str, str], usernames: dict[str, str]) -> ScheduleEntry:
    date_from, date_to, time_from, time_to, member, kind, day_name = entry
    start_dt = _parse_legacy_datetime(date_from, time_from)
    end_dt = _parse_legacy_datetime(date_to, time_to)
    apply_member = EXTRA_MEMBER_SUBSTITUTIONS.get(member, member)
    start = start_dt.strftime("%Y-%m-%dT%H:%M:%SZ")
    end = end_dt.strftime("%Y-%m-%dT%H:%M:%SZ")
    raw_id = f"{start}_{end}_{member}_{kind}".replace(":", "").replace("+", "")
    return ScheduleEntry(
        id=raw_id,
        date_from=start_dt.strftime("%Y-%m-%d"),
        date_to=end_dt.strftime("%Y-%m-%d"),
        time_from=start_dt.strftime("%H:%M"),
        time_to=end_dt.strftime("%H:%M"),
        start=start,
        end=end,
        member=member,
        apply_member=apply_member,
        username=usernames.get(apply_member),
        kind=kind,
        day_name=day_name,
        start_epoch_ms=int(start_dt.timestamp() * 1000),
        end_epoch_ms=int(end_dt.timestamp() * 1000),
        spans_days=max(1, (end_dt.date() - start_dt.date()).days),
    )


def create_non_overlapping_schedule(year: int, usernames: dict[str, str]) -> list[ScheduleEntry]:
    workday_rotation = list(WORKDAY_ROTATION)
    weekend_rotation = list(WEEKEND_ROTATION)
    weekend_rotation_when_dati = list(WEEKEND_ROTATION_WHEN_DATI)
    schedule: list[tuple[str, str, str, str, str, str, str]] = []

    while workday_rotation[0] != FIRST_WORKDAY_USER:
        workday_rotation = workday_rotation[1:] + workday_rotation[:1]

    while weekend_rotation[0] != FIRST_WEEKEND_USER:
        weekend_rotation = weekend_rotation[1:] + weekend_rotation[:1]

    current_date = datetime(2026, 1, 1)
    end_date = datetime(year, 12, 31)
    if year < 2026:
        current_date = datetime(year, 1, 1)
        end_date = datetime(year, 12, 31)

    while current_date <= end_date:
        day_name = current_date.strftime("%A")

        if day_name == "Friday" or is_holiday(current_date):
            if day_name == "Friday":
                workday_rotation = workday_rotation[1:] + workday_rotation[:1]
            rotate_and_get_next_person(weekend_rotation)
            assigned_member = weekend_rotation[0]

            if schedule:
                thursday_name = schedule[-1][4]
                if day_name == "Friday" and thursday_name == assigned_member and len(schedule) >= 2:
                    if schedule[-2][5] != "WEEKEND":
                        last_item = list(schedule[-1])
                        second_last_item = list(schedule[-2])
                        wednesday_name = second_last_item[4]
                        last_item[4] = wednesday_name
                        second_last_item[4] = thursday_name
                        schedule[-1] = tuple(last_item)
                        schedule[-2] = tuple(second_last_item)

            if assigned_member in ("Moriah", "Dovid", "Nadav"):
                schedule.append((
                    current_date.strftime("%d/%m/%Y"),
                    (current_date + timedelta(hours=6)).strftime("%d/%m/%Y"),
                    format_time("7:00AM"),
                    format_time("2:00PM"),
                    assigned_member,
                    "WEEKEND",
                    "Friday",
                ))
                goy_shel_shabat = rotate_and_get_next_person(weekend_rotation_when_dati)
                schedule.append((
                    current_date.strftime("%d/%m/%Y"),
                    (current_date + timedelta(days=1)).strftime("%d/%m/%Y"),
                    format_time("2:00PM"),
                    format_time("7:00PM"),
                    goy_shel_shabat,
                    "WEEKEND",
                    "Saturday",
                ))
                schedule.append((
                    (current_date + timedelta(days=1, hours=12)).strftime("%d/%m/%Y"),
                    (current_date + timedelta(days=2)).strftime("%d/%m/%Y"),
                    format_time("7:00PM"),
                    format_time("7:00AM"),
                    assigned_member,
                    "WEEKEND",
                    "Friday",
                ))
            else:
                schedule.append((
                    current_date.strftime("%d/%m/%Y"),
                    (current_date + timedelta(days=2)).strftime("%d/%m/%Y"),
                    format_time("7:00AM"),
                    format_time("7:00AM"),
                    assigned_member,
                    "WEEKEND",
                    "Saturday",
                ))

            current_date = add_day(add_day(current_date))
            if current_date.strftime("%A") == "Saturday":
                current_date = add_day(current_date)
        else:
            next_day = add_day(current_date)
            current_pair = get_week_pair(current_date)
            if day_name in ("Sunday", "Monday"):
                assigned_member = current_pair[0]
            elif day_name in ("Tuesday", "Wednesday", "Thursday"):
                assigned_member = current_pair[1]
            else:
                assigned_member = workday_rotation[0]

            if day_name in ("Monday", "Tuesday", "Wednesday", "Thursday"):
                schedule.append((
                    current_date.strftime("%d/%m/%Y"),
                    current_date.strftime("%d/%m/%Y"),
                    format_time("6:00AM"),
                    format_time("7:00PM"),
                    assigned_member,
                    "REGULAR",
                    day_name,
                ))
                schedule.append((
                    current_date.strftime("%d/%m/%Y"),
                    next_day.strftime("%d/%m/%Y"),
                    format_time("7:00PM"),
                    format_time("7:00AM"),
                    "Dovid",
                    "REGULAR",
                    day_name,
                ))
            else:
                schedule.append((
                    current_date.strftime("%d/%m/%Y"),
                    next_day.strftime("%d/%m/%Y"),
                    format_time("7:00AM"),
                    format_time("7:00AM"),
                    assigned_member,
                    "REGULAR",
                    day_name,
                ))

            current_date = next_day

    return [_entry_tuple_to_model(entry, usernames) for entry in schedule]


def month_entries(year: int, month: int, usernames: dict[str, str]) -> list[ScheduleEntry]:
    first_day = date(year, month, 1)
    if month == 12:
        next_month = date(year + 1, 1, 1)
    else:
        next_month = date(year, month + 1, 1)

    entries = []
    for entry in create_non_overlapping_schedule(year, usernames):
        entry_start = datetime.strptime(entry.date_from, "%Y-%m-%d").date()
        if first_day <= entry_start < next_month:
            entries.append(entry)
    return entries


def find_entry(entries: Iterable[ScheduleEntry], entry_id: str) -> ScheduleEntry | None:
    for entry in entries:
        if entry.id == entry_id:
            return entry
    return None


def team_payload(usernames: dict[str, str]) -> list[dict[str, str]]:
    return [{"name": name, "username": username} for name, username in usernames.items()]


def default_members() -> list[str]:
    return list(DEFAULT_ASSIGNABLE_MEMBERS)
