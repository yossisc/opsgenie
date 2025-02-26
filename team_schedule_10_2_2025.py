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
start_date = datetime(2025, 3, 30 ) # yyyy, mo, day
end_date = datetime(2025, 3, 31)  # yyyy, mo, day
#
################################

first_workday_user = "Shay"
first_weekend_user = "Gour"
workday_rotation = ["Yossi", "Tom", "Moriah", "Gour" ,"Shay"]
weekend_rotation = ["Gour", "Shay", "Yossi", "Yaron", "Tom" ]
weekend_rotation_when_dati = ["Shay", "Yossi", "Gour", "Yaron" ,"Tom"]
holidays = [ "13/04/2025",
             "01/05/2025",
             "02/06/2025",
             "23/09/2025",
             "24/09/2025",
             "02/10/2025",
             "07/10/2025",
             "14/10/2025"
    ]


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

# Function to create non-overlapping schedule
def create_non_overlapping_schedule(workday_rotation, weekend_rotation):
    weekday_rotation_index = 0
    weekend_rotation_index = 0
    schedule = []
    first_null_filled = False

    while workday_rotation[0] != first_workday_user:
        workday_rotation = workday_rotation[1:] + workday_rotation[:1]

    while weekend_rotation[0] != first_weekend_user:
        weekend_rotation = weekend_rotation[1:] + weekend_rotation[:1]

    # Main loop...
    current_date = datetime(2025, 1, 1)
    while current_date <= datetime(2025, 12, 31):
        day_name = current_date.strftime("%A")
        # if start_date < current_date < end_date:
        #     print(f" {schedule[-1]} ")

        # WEEKEND Handler:
        if is_holiday(current_date, holidays):
            print("Holi {}",current_date)
        if day_name == "Friday" or is_holiday(current_date, holidays):
            if day_name == "Friday":
                workday_rotation = workday_rotation[1:] + workday_rotation[:1]
            rotate_and_get_next_person(weekend_rotation)
            assigned_member = weekend_rotation[0]
            
            # if Thureday name = weekend name then replace Thureday name with Wednesday...
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
                # Fri 2pm - Sat 8pm
                # select the Shabat goy_shel_shabat when Dati
                goy_shel_shabat = rotate_and_get_next_person(weekend_rotation_when_dati)
                # if goy_shel_shabat == weekend_rotation[1]:
                #     goy_shel_shabat = rotate_and_get_next_person(weekend_rotation_when_dati)
                # #     rotate_and_get_next_person(weekend_rotation)
                # if goy_shel_shabat == weekend_rotation[-1]:
                #     goy_shel_shabat = rotate_and_get_next_person(weekend_rotation_when_dati)
                #     goy_shel_shabat = rotate_and_get_next_person(weekend_rotation_when_dati)
                # if goy_shel_shabat == weekend_rotation[1]:
                #     rotate_and_get_next_person(weekend_rotation)
                #     while weekend_rotation[0] != "Moriah" and weekend_rotation[0] !=  "Dovid": 
                #         rotate_and_get_next_person(weekend_rotation)
                schedule.append((current_date.strftime("%d/%m/%Y"), (current_date + timedelta(days=1)).strftime("%d/%m/%Y"), format_time("2:00PM"), format_time("7:00PM"), goy_shel_shabat,"WEEKEND","Saturday"))
                #
                # Sat 8pm - Sun 8am
                schedule.append(((current_date + timedelta(days=1, hours=12)).strftime("%d/%m/%Y"), (current_date + timedelta(days=2)).strftime("%d/%m/%Y"), format_time("7:00PM"), format_time("7:00AM"), assigned_member, "WEEKEND","Friday"))
            else:
                schedule.append((current_date.strftime("%d/%m/%Y"), (current_date + timedelta(days=2)).strftime("%d/%m/%Y"), format_time("7:00AM"), format_time("7:00AM"), assigned_member+"","WEEKEND","Saturday"))
########################################################################################    
    
            current_date = add_day(add_day(current_date))  # Skip to Sunday
    
        # OTHER DAYS OF THE WEEK 
        else:
            assigned_member = workday_rotation[weekday_rotation_index % (len(workday_rotation))]
            # print(weekday_rotation_index % (len(workday_rotation)-1))
            weekday_rotation_index += 1
            next_day = add_day(current_date)
            shift_end_time="7:00AM"
            if day_name in ("Monday","Tuesday","Wednesday","Thursday"):
            #if day_name in ("Dovid in holiday"):
                shift_end_time="7:00PM"
                schedule.append((current_date.strftime("%d/%m/%Y"), current_date.strftime("%d/%m/%Y"), format_time("6:00AM"), format_time(shift_end_time), assigned_member,"REGULAR",day_name))
                schedule.append((current_date.strftime("%d/%m/%Y"), next_day.strftime("%d/%m/%Y"), format_time(shift_end_time), format_time("7:00AM"), "Dovid", "REGULAR",day_name))
            else:
                schedule.append((current_date.strftime("%d/%m/%Y"), next_day.strftime("%d/%m/%Y"), format_time("7:00AM"), format_time(shift_end_time), assigned_member,"REGULAR",day_name))

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
    if datetime.strptime(date_from, "%d/%m/%Y") >= start_date and datetime.strptime(date_to, "%d/%m/%Y") <= end_date:
        date_from_formatted = datetime.strptime(date_from, "%d/%m/%Y").strftime("%Y-%m-%d")
        date_to_formatted = datetime.strptime(date_to, "%d/%m/%Y").strftime("%Y-%m-%d")
        if we_or_reg == "WEEKEND":
            print("\033[38;5;214m", end="")
        else: 
            print("\033[1;91m", end="")
        print(f" {date_from_formatted:<12} {date_to_formatted:<12} {time_from:<12} {time_to:<12} {member:<12} {day_name:<12}")
        #print(f"{we_or_reg:<12} {member}")    

if apply_run:
    input_value = input("Do you want to apply the above schedule? (y/n) ")
    check = input_value.lower() in ['y', 'yes']
    if check:
        for entry in non_overlapping_schedule:
            date_from, date_to, time_from, time_to, member, we_or_reg, day_name = entry
            if datetime.strptime(date_from, "%d/%m/%Y") >= start_date and datetime.strptime(date_to, "%d/%m/%Y") <= end_date:
                date_from_formatted = datetime.strptime(date_from, "%d/%m/%Y").strftime("%Y-%m-%d")
                date_to_formatted = datetime.strptime(date_to, "%d/%m/%Y").strftime("%Y-%m-%d")
                if member != 'NULL':
                    full_date_from = date_from_formatted + time_from
                    full_date_to = date_to_formatted + time_to
                    os.chdir('/home/yossi/opsgenie')
                    print("Applying: "+ member +' '+ full_date_from +' '+ full_date_to)
                    subprocess.run(["./set_rota.sh", member, full_date_from, full_date_to])
                    print('.')



