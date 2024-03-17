#!/usr/bin/env python3

from datetime import datetime, timedelta
import argparse
import os
import subprocess

def adjust_rotation(rotation, first_user):
    while rotation[0] != first_user:
        rotation = rotation[1:] + rotation[:1]
    return rotation

def format_time(time_str):
    return 'T' + (datetime.strptime(time_str, '%I:%M%p') - timedelta(hours=2)).strftime('%H:%M') + 'Z'

def create_schedule(start_date, end_date, workday_rotation, weekend_rotation):
    schedule = []
    current_date = start_date
    workday_rotation = adjust_rotation(workday_rotation, "Adiel")
    weekend_rotation = adjust_rotation(weekend_rotation, "Moriah")

    while current_date <= end_date:
        day_name = current_date.strftime('%A')
        if day_name in ['Saturday', 'Sunday']:
            assigned_member = weekend_rotation.pop(0)
            weekend_rotation.append(assigned_member)
            schedule.append((current_date, current_date + timedelta(days=1), format_time('8:00AM'), format_time('8:00AM'), assigned_member, 'WEEKEND'))
        else:
            assigned_member = workday_rotation.pop(0)
            workday_rotation.append(assigned_member)
            schedule.append((current_date, current_date + timedelta(days=1), format_time('8:00AM'), format_time('8:00AM'), assigned_member, 'REGULAR'))
        current_date += timedelta(days=1)
    return schedule

def print_schedule(schedule):
    print("| Date From  | Date To    | Time From | Time To   | Team Member | Type      |")
    print("|------------|------------|-----------|-----------|-------------|-----------|")
    for start, end, time_from, time_to, member, shift_type in schedule:
        date_format = "%Y-%m-%d"
        print(f"| {start.strftime(date_format):<10} | {end.strftime(date_format):<10} | {time_from:<9} | {time_to:<9} | {member:<11} | {shift_type:<9} |")

parser = argparse.ArgumentParser()
parser.add_argument('--apply', action='store_true')
args = parser.parse_args()

start_date = datetime(2024, 3, 20)
end_date = datetime(2024, 4, 5)
workday_rotation = ["Dovid", "Yossi", "Tom", "Moriah", "Adiel"]
weekend_rotation = ["Tom", "Adiel", "Yossi", "Dovid", "Moriah"]

schedule = create_schedule(start_date, end_date, workday_rotation, weekend_rotation)
print_schedule(schedule)

if args.apply:
    input_value = input("Do you want to apply the above schedule? (y/n) ")
    if input_value.lower() in ['y', 'yes']:
        for entry in schedule:
            start, end, time_from, time_to, member, _ = entry
            full_date_from = start.strftime("%Y-%m-%d") + time_from
            full_date_to = end.strftime("%Y-%m-%d") + time_to
            os.chdir('/home/yossi/opsgenie')
            print(f"Applying: {member} {full_date_from} {full_date_to}")
            subprocess.run(["./set_rota.sh", member, full_date_from, full_date_to])

