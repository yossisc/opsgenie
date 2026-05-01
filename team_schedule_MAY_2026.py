#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from datetime import datetime, timedelta
import sys, os
import subprocess
import argparse
import time
import tty
import termios
import select

parser = argparse.ArgumentParser()
parser.add_argument('--apply', action='store_true')
args = parser.parse_args()
apply_run = args.apply

################################
#    CHANGE HERE !!!
start_date = datetime(2026, 5, 1)   # yyyy, mo, day
end_date   = datetime(2026, 5, 31)  # yyyy, mo, day
#
################################

first_workday_user = "Gabi"
first_weekend_user = "Gabi"
workday_rotation = ["Yossi", "Nadav", "Gabi", "Gour"]
weekend_rotation = ["Extra1", "Extra2", "Yossi", "Gabi", "Gour"]
weekend_rotation_when_dati = ["Extra1", "Extra2", "Yossi", "Gour", "Gabi"]
holidays = [ "01/04/2026",  # Passover 1st day
             "07/04/2026",  # Passover 7th day
             "21/04/2026",  # Independence
             "21/05/2026",  # Shavuot
             "11/09/2026",  # Rosh Hashanah 1
             "12/09/2026",  # Rosh Hashanah 2
             "20/09/2026",  # Yom Kippur
             "25/09/2026",  # Sukkot 1st day
             "02/10/2026"   # Simchat Torah
    ]

# ─────────────────────────────────────────────────────────────────────────────
# WORKDAY (Sun–Thu) ROTATION — date-anchored, 4-week repeating cycle.
#
# Each week pair is [person_for_Sun_Mon (2 days), person_for_Tue_Wed_Thu (3 days)]
#
#   Week 1: Gabi (Sun,Mon)  +  Yossi (Tue,Wed,Thu)
#   Week 2: Gour (Sun,Mon)  +  Nadav (Tue,Wed,Thu)
#   Week 3: Yossi (Sun,Mon) +  Gabi  (Tue,Wed,Thu)   ← swap of week 1
#   Week 4: Nadav (Sun,Mon) +  Gour  (Tue,Wed,Thu)   ← swap of week 2
#   …repeats every 4 weeks indefinitely.
#
# ANCHOR:  Sunday 03/05/2026 is Week 1.
# This anchor is permanent — do NOT change it for future months. Just update
# start_date / end_date above and the same rotation will continue correctly.
# ─────────────────────────────────────────────────────────────────────────────
WEEKLY_PAIRS = [
    ["Gabi",  "Yossi"],   # Week 1
    ["Gour",  "Nadav"],   # Week 2
    ["Yossi", "Gabi" ],   # Week 3 (swap of week 1)
    ["Nadav", "Gour" ],   # Week 4 (swap of week 2)
]
ANCHOR_SUNDAY = datetime(2026, 5, 3)   # Week 1 anchor — keep permanent

def get_week_pair(current_date):
    """Return [sun_mon_person, tue_wed_thu_person] for the work-week
    containing current_date, using the 4-week date-anchored cycle."""
    # Python weekday(): Mon=0 … Sun=6.  We want Sun=0 … Sat=6.
    days_since_sunday = (current_date.weekday() + 1) % 7
    week_sunday = current_date - timedelta(days=days_since_sunday)
    weeks_since_anchor = (week_sunday - ANCHOR_SUNDAY).days // 7
    return WEEKLY_PAIRS[weeks_since_anchor % len(WEEKLY_PAIRS)]


# All assignable team members (used for cycling in interactive edit)
all_team_members = ["Yossi", "Gabi", "Gour", "Nadav", "Dovid"]


# Helper function to add a day
def add_day(current_date):
    return current_date + timedelta(days=1)

def format_time(time_str):
    time_obj = datetime.strptime(time_str, '%I:%M%p') - timedelta(hours=2)
    return 'T' + time_obj.strftime('%H:%M') + 'Z'

def rotate_and_get_next_person(people_list):
    people_list.append(people_list.pop(0))
    return people_list[-1]

def is_holiday(current_date, holidays):
    date_str = current_date.strftime("%d/%m/%Y")
    return date_str in holidays

def get_key():
    """Read one keypress in raw mode. Uses os.read() for reliable byte-by-byte input."""
    fd = sys.stdin.fileno()
    old = termios.tcgetattr(fd)
    try:
        tty.setraw(fd)
        ch = os.read(fd, 1)
        if ch == b'\x1b':
            # Wait briefly for the bracket byte of an escape sequence
            if select.select([sys.stdin], [], [], 0.1)[0]:
                ch2 = os.read(fd, 1)
                if ch2 == b'[':
                    # Wait briefly for the final byte (A/B/C/D)
                    if select.select([sys.stdin], [], [], 0.1)[0]:
                        ch3 = os.read(fd, 1)
                        if ch3 == b'A': return 'UP'
                        if ch3 == b'B': return 'DOWN'
                        if ch3 == b'C': return 'RIGHT'
                        if ch3 == b'D': return 'LEFT'
            return 'ESC'
        return ch.decode('utf-8', errors='replace')
    finally:
        termios.tcsetattr(fd, termios.TCSADRAIN, old)


def interactive_edit(entries, team_members):
    """
    Full-screen interactive editor for schedule entries.
    ↑ ↓  navigate rows
    E    (Shift+E) cycle the Team Member for the selected row
    C    (Shift+C) commit changes and exit
    Ctrl+C  abort without saving
    Returns the (possibly modified) list of entry tuples.
    """
    rows = [list(e) for e in entries]
    cursor = 0

    def render():
        print('\033[2J\033[H', end='', flush=True)
        print("┌────────────────────────────────────────────────────────────────────────────┐")
        print("│  EDIT MODE  │  ↑ ↓ navigate  │  E = cycle member  │  C = commit & done    │")
        print("└────────────────────────────────────────────────────────────────────────────┘")
        print()
        print(f"  {'Date From':<12} {'Date To':<12} {'Time From':<12} {'Time To':<12} {'Team Member':<14} Day")
        print("  " + "─" * 74)
        for i, row in enumerate(rows):
            date_from, date_to, time_from, time_to, member, _, day_name = row
            dfmt = datetime.strptime(date_from, "%d/%m/%Y").strftime("%Y-%m-%d")
            dtmt = datetime.strptime(date_to,   "%d/%m/%Y").strftime("%Y-%m-%d")
            line = f"  {dfmt:<12} {dtmt:<12} {time_from:<12} {time_to:<12} {member:<14} {day_name}"
            if i == cursor:
                print(f"\033[7m▶{line[1:]}\033[0m")
            else:
                print(line)
        print()

    while True:
        render()
        key = get_key()
        if key == 'UP':
            cursor = max(0, cursor - 1)
        elif key == 'DOWN':
            cursor = min(len(rows) - 1, cursor + 1)
        elif key == 'E':
            current = rows[cursor][4]
            idx = team_members.index(current) if current in team_members else -1
            rows[cursor][4] = team_members[(idx + 1) % len(team_members)]
        elif key == 'C':
            print('\033[2J\033[H', end='', flush=True)
            break
        elif key == '\x03':  # Ctrl+C — abort, return originals unchanged
            print('\033[2J\033[H', end='', flush=True)
            print("Edit cancelled — original schedule restored.\n")
            return entries

    return [tuple(r) for r in rows]


# Function to create non-overlapping schedule
def create_non_overlapping_schedule(workday_rotation, weekend_rotation):
    weekday_rotation_index = 0
    schedule = []

    while workday_rotation[0] != first_workday_user:
        workday_rotation = workday_rotation[1:] + workday_rotation[:1]

    while weekend_rotation[0] != first_weekend_user:
        weekend_rotation = weekend_rotation[1:] + weekend_rotation[:1]

    # Main loop — generate the whole year so weekend rotation stays stable.
    current_date = datetime(2026, 1, 1)
    while current_date <= datetime(2026, 12, 31):
        day_name = current_date.strftime("%A")

        # ── WEEKEND / HOLIDAY handler (UNCHANGED — keep as is) ──
        if is_holiday(current_date, holidays):
            print(f"Holi {current_date.strftime('%d/%m/%Y')}")
        if day_name == "Friday" or is_holiday(current_date, holidays):
            if day_name == "Friday":
                workday_rotation = workday_rotation[1:] + workday_rotation[:1]
            rotate_and_get_next_person(weekend_rotation)
            assigned_member = weekend_rotation[0]

            # if Thursday name = weekend name then swap Thursday with Wednesday
            Thursday_Name = schedule[-1][4]
            if day_name == "Friday" and Thursday_Name == assigned_member:
                if schedule[-2][5] != "WEEKEND":
                    last_item = list(schedule[-1])
                    second_last_item = list(schedule[-2])
                    Wednesday_Name = second_last_item[4]
                    last_item[4] = Wednesday_Name
                    second_last_item[4] = Thursday_Name
                    schedule[-1] = tuple(last_item)
                    schedule[-2] = tuple(second_last_item)

########################################################################################
            if assigned_member in ("Moriah", "Dovid", "Nadav"):
                # Fri 8am - 2pm
                schedule.append((current_date.strftime("%d/%m/%Y"), (current_date + timedelta(hours=6)).strftime("%d/%m/%Y"), format_time("7:00AM"), format_time("2:00PM"), assigned_member,"WEEKEND","Friday"))
                # Fri 2pm - Sat 8pm — pick the goy_shel_shabat when assigned is Dati
                goy_shel_shabat = rotate_and_get_next_person(weekend_rotation_when_dati)
                schedule.append((current_date.strftime("%d/%m/%Y"), (current_date + timedelta(days=1)).strftime("%d/%m/%Y"), format_time("2:00PM"), format_time("7:00PM"), goy_shel_shabat,"WEEKEND","Saturday"))
                # Sat 8pm - Sun 8am
                schedule.append(((current_date + timedelta(days=1, hours=12)).strftime("%d/%m/%Y"), (current_date + timedelta(days=2)).strftime("%d/%m/%Y"), format_time("7:00PM"), format_time("7:00AM"), assigned_member, "WEEKEND","Friday"))
            else:
                schedule.append((current_date.strftime("%d/%m/%Y"), (current_date + timedelta(days=2)).strftime("%d/%m/%Y"), format_time("7:00AM"), format_time("7:00AM"), assigned_member+"","WEEKEND","Saturday"))
########################################################################################

            current_date = add_day(add_day(current_date))
            if current_date.strftime("%A") == "Saturday":  # Holiday on Thursday skips to Sat; push to Sun
                current_date = add_day(current_date)

        # ── REGULAR WORKDAY handler (Sun–Thu) — NEW date-anchored rotation ──
        else:
            next_day = add_day(current_date)

            current_pair = get_week_pair(current_date)
            if day_name in ("Sunday", "Monday"):
                assigned_member = current_pair[0]
            elif day_name in ("Tuesday", "Wednesday", "Thursday"):
                assigned_member = current_pair[1]
            else:
                # Fallback (should not normally hit — Saturday is consumed by weekend block)
                assigned_member = workday_rotation[weekday_rotation_index % len(workday_rotation)]
                weekday_rotation_index += 1

            if day_name in ("Monday", "Tuesday", "Wednesday", "Thursday"):
                schedule.append((current_date.strftime("%d/%m/%Y"), current_date.strftime("%d/%m/%Y"), format_time("6:00AM"), format_time("7:00PM"), assigned_member, "REGULAR", day_name))
                schedule.append((current_date.strftime("%d/%m/%Y"), next_day.strftime("%d/%m/%Y"), format_time("7:00PM"), format_time("7:00AM"), "Dovid", "REGULAR", day_name))
            else:
                schedule.append((current_date.strftime("%d/%m/%Y"), next_day.strftime("%d/%m/%Y"), format_time("7:00AM"), format_time("7:00AM"), assigned_member, "REGULAR", day_name))

            current_date = next_day
    return schedule


#################################  MAIN  #################################
print(f"\nSchedule From: {start_date.strftime('%A, %d/%m/%Y')}")
print(f"         Until: {end_date.strftime('%A, %d/%m/%Y')}\n")

# Generate non-overlapping schedule
non_overlapping_schedule = create_non_overlapping_schedule(workday_rotation, weekend_rotation)

# NEXT....
print("| Date From  | Date To    | Time From | Time To     | Team Member |    day   |")
print("|------------|------------|-----------|-------------|-------------|----------|")
for entry in non_overlapping_schedule:
    date_from, date_to, time_from, time_to, member, we_or_reg, day_name = entry
    date_from_dt = datetime.strptime(date_from, "%d/%m/%Y")
    if date_from_dt >= start_date and date_from_dt <= end_date:
        date_from_formatted = date_from_dt.strftime("%Y-%m-%d")
        date_to_formatted = datetime.strptime(date_to, "%d/%m/%Y").strftime("%Y-%m-%d")
        if we_or_reg == "WEEKEND":
            print("\033[38;5;214m", end="")
        else:
            print("\033[1;91m", end="")
        print(f" {date_from_formatted:<12} {date_to_formatted:<12} {time_from:<12} {time_to:<12} {member:<12} {day_name:<12}")

count = 0

if apply_run:
    # Build the filtered + substituted list of entries that will actually be applied.
    apply_entries = []
    for entry in non_overlapping_schedule:
        date_from, date_to, time_from, time_to, member, we_or_reg, day_name = entry
        date_from_dt = datetime.strptime(date_from, "%d/%m/%Y")
        if date_from_dt >= start_date and date_from_dt <= end_date:
            if member == 'Extra1':   member = 'Gabi'
            elif member == 'Extra2': member = 'Gour'
            apply_entries.append((date_from, date_to, time_from, time_to, member, we_or_reg, day_name))

    def print_apply_table(entries):
        print("\033[0m")
        print("| Date From  | Date To    | Time From   | Time To     | Team Member |    day   |")
        print("|------------|------------|-------------|-------------|-------------|----------|")
        for e in entries:
            date_from, date_to, time_from, time_to, member, we_or_reg, day_name = e
            dfmt = datetime.strptime(date_from, "%d/%m/%Y").strftime("%Y-%m-%d")
            dtmt = datetime.strptime(date_to,   "%d/%m/%Y").strftime("%Y-%m-%d")
            color = "\033[38;5;214m" if we_or_reg == "WEEKEND" else "\033[1;91m"
            print(f"{color} {dfmt:<12} {dtmt:<12} {time_from:<12} {time_to:<12} {member:<12} {day_name:<12}\033[0m")
        print()

    first_pass = True
    while True:
        if not first_pass:
            print_apply_table(apply_entries)
        first_pass = False

        answer = input("Do you want to apply the above schedule? (y/n/e) ").strip().lower()

        if answer in ('y', 'yes'):
            for entry in apply_entries:
                date_from, date_to, time_from, time_to, member, we_or_reg, day_name = entry
                if member == 'NULL':
                    continue
                dfmt = datetime.strptime(date_from, "%d/%m/%Y").strftime("%Y-%m-%d")
                dtmt = datetime.strptime(date_to,   "%d/%m/%Y").strftime("%Y-%m-%d")
                full_date_from = dfmt + time_from
                full_date_to   = dtmt + time_to
                os.chdir('/Users/yossi/opsgenie')
                print("Applying: " + member + ' ' + full_date_from + ' ' + full_date_to)
                subprocess.run(["./set_rota.sh", member, full_date_from, full_date_to])
                time.sleep(1)
                print('.')
                count += 1
                if count % 10 == 0:
                    print("⏸ Taking a 30s break to avoid rate limits…")
                    for s in range(30, 0, -1):
                        print(f"   Resuming in {s:02d}s", end='\r', flush=True)
                        time.sleep(1)
                    print(" " * 30, end='\r')
                    print("▶️ Resuming")
            break

        elif answer == 'e':
            apply_entries = interactive_edit(apply_entries, all_team_members)

        elif answer in ('n', 'no'):
            print("Cancelled.")
            break
