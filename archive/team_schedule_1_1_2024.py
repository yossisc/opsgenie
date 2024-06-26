#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from datetime import datetime, timedelta
import sys, os
import subprocess
import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--apply', action='store_true')
args = parser.parse_args()
apply_run = args.apply

################################
#    CHANGE HERE !!!
start_date = datetime(2024, 3, 31 ) # yyyy, mo, day
end_date = datetime(2024, 4, 7)  # yyyy, mo, day
#
################################

first_workday_user = "Adiel"
first_weekend_user = "Moriah"
workday_rotation = ["Dovid", "Yossi", "Tom", "Moriah", "Adiel"]
weekend_rotation = ["Tom", "Adiel", "Yossi", "Dovid", "Moriah"]
#weekend_rotation_when_dati = ["Tom", "Adiel", "Yossi", "Gour", "Shay"]

# Helper function to add a day
def add_day(current_date):
    return current_date + timedelta(days=1)

def format_time(time_str):
    time_obj = datetime.strptime(time_str, '%I:%M%p') - timedelta(hours=2)
    return 'T' + time_obj.strftime('%H:%M') + 'Z'

# Function to create non-overlapping schedule
def create_non_overlapping_schedule(start_date, end_date, workday_rotation, weekend_rotation):
    current_date = datetime(2024, 1, 1)
    weekday_rotation_index = 0
    weekend_rotation_index = 0
    schedule = []
    first_null_filled = False

    print(f"\nSchedule From: {start_date.strftime('%A, %d/%m/%Y')}")
    print(f"         Until: {end_date.strftime('%A, %d/%m/%Y')}\n")

    while workday_rotation[0] != first_workday_user:
        workday_rotation = workday_rotation[1:] + workday_rotation[:1]

    while weekend_rotation[0] != first_weekend_user:
        weekend_rotation = weekend_rotation[1:] + weekend_rotation[:1]

    while current_date < start_date:
        current_date = add_day(current_date)
        if current_date.strftime("%A") != "Friday": # and current_date.strftime("%A") != "Saturday":
            workday_rotation = workday_rotation[1:] + workday_rotation[:1]
        if current_date.strftime("%A") == "Friday":
            weekend_rotation = weekend_rotation[1:] + weekend_rotation[:1]


    while current_date <= end_date:
        day_name = current_date.strftime("%A")
        # WEEKEND Handler:
        if day_name == "Friday":
            assigned_member = weekend_rotation[weekend_rotation_index % len(weekend_rotation)]
            weekend_rotation_index += 1
            workday_rotation = workday_rotation[1:] + workday_rotation[:1]
      
            # if Thureday name = weekend name then replace Thureday name with Wednesday...
            Thursday_Name = schedule[-1][4]
            if Thursday_Name == assigned_member:
                last_item = list(schedule[-1])
                second_last_item = list(schedule[-2])
                Wednesday_Name = second_last_item[4]
                last_item[4] = Wednesday_Name
                second_last_item[4] = Thursday_Name
                schedule[-1] = tuple(last_item)
                schedule[-2] = tuple(second_last_item)

            if assigned_member == "Moriah" or assigned_member == "Dovid": 
                schedule.append((current_date.strftime("%d/%m/%Y"), (current_date + timedelta(hours=6)).strftime("%d/%m/%Y"), format_time("8:00AM"), format_time("2:00PM"), assigned_member,"WEEKEND"))
                schedule.append((current_date.strftime("%d/%m/%Y"), (current_date + timedelta(days=1)).strftime("%d/%m/%Y"), format_time("2:00PM"), format_time("8:00PM"), "Yaron","WEEKEND"))
                schedule.append(((current_date + timedelta(days=1, hours=12)).strftime("%d/%m/%Y"), (current_date + timedelta(days=2)).strftime("%d/%m/%Y"), format_time("8:00PM"), format_time("8:00AM"), assigned_member, "WEEKEND"))
                
            else:
                # Regular weekend shift
                schedule.append((current_date.strftime("%d/%m/%Y"), (current_date + timedelta(days=2)).strftime("%d/%m/%Y"), format_time("8:00AM"), format_time("8:00AM"), assigned_member+"","WEEKEND"))

            current_date = add_day(add_day(current_date))  # Skip to Sunday

        # OTHER DAYS OF THE WEEK 
        else:
            assigned_member = workday_rotation[weekday_rotation_index % (len(workday_rotation))]
            # print(weekday_rotation_index % (len(workday_rotation)-1))
            weekday_rotation_index += 1
            next_day = add_day(current_date)
            schedule.append((current_date.strftime("%d/%m/%Y"), next_day.strftime("%d/%m/%Y"), format_time("8:00AM"), format_time("8:00AM"), assigned_member,"REGULAR"))

            current_date = next_day
   
    return schedule

# Generate non-overlapping schedule
non_overlapping_schedule = create_non_overlapping_schedule(start_date, end_date, workday_rotation, weekend_rotation)

# NEXT....
print("| Date From  | Date To    | Time From | Time To     | Team Member    |")
print("|------------|------------|-----------|-------------|----------------|")
for entry in non_overlapping_schedule:
    date_from, date_to, time_from, time_to, member, we_or_reg = entry
    date_from_formatted = datetime.strptime(date_from, "%d/%m/%Y").strftime("%Y-%m-%d")
    date_to_formatted = datetime.strptime(date_to, "%d/%m/%Y").strftime("%Y-%m-%d")
    if we_or_reg == "WEEKEND":
        print("\033[38;5;214m", end="")
    else: 
        print("\033[1;91m", end="")
    print(f" {date_from_formatted:<12} {date_to_formatted:<12} {time_from:<12} {time_to:<12} {member}")
    

if apply_run:
    input_value = input("Do you want to apply the above schedule? (y/n) ")
    check = input_value.lower() in ['y', 'yes']
    if check:
        for entry in non_overlapping_schedule:
            date_from, date_to, time_from, time_to, member, we_or_reg = entry
            date_from_formatted = datetime.strptime(date_from, "%d/%m/%Y").strftime("%Y-%m-%d")
            date_to_formatted = datetime.strptime(date_to, "%d/%m/%Y").strftime("%Y-%m-%d")
            if member != 'NULL':
                full_date_from = date_from_formatted + time_from
                full_date_to = date_to_formatted + time_to
                os.chdir('/home/yossi/opsgenie')
                print("Applying: "+ member +' '+ full_date_from +' '+ full_date_to)
                subprocess.run(["./set_rota.sh", member, full_date_from, full_date_to])
                print('.')



