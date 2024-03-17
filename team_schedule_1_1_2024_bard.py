from datetime import datetime, timedelta
import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--apply', action='store_true')
args = parser.parse_args()

start_date = datetime(2024, 3, 20)
end_date = datetime(2024, 4, 5)

first_workday_user = "Adiel"
first_weekend_user = "Moriah"
workday_rotation = ["Dovid", "Yossi", "Tom", "Moriah", "Adiel"]
weekend_rotation = ["Tom", "Adiel", "Yossi", "Dovid", "Moriah"]

def add_day(date):
    return date + timedelta(days=1)

def format_time(time_str):
    time_obj = datetime.strptime(time_str, '%I:%M%p') - timedelta(hours=2)
    return 'T' + time_obj.strftime('%H:%M') + 'Z'

def create_schedule(start_date, end_date, workday_rotation, weekend_rotation):
    schedule = []
    current_date = start_date

    # Align rotations if needed
    while workday_rotation[0] != first_workday_user:
        workday_rotation = workday_rotation[1:] + workday_rotation[:1]
    while weekend_rotation[0] != first_weekend_user:
        weekend_rotation = weekend_rotation[1:] + weekend_rotation[:1]

    while current_date <= end_date:
        day_name = current_date.strftime('%A')

        if day_name == 'Friday':
            member = weekend_rotation[0]
            weekend_rotation = weekend_rotation[1:] + weekend_rotation[:1]

            if member in ("Moriah", "Dovid"):
                schedule.extend([
                    (current_date, add_day(current_date), "8:00AM", "2:00PM", member, "WEEKEND"),
                    (current_date, add_day(current_date), "2:00PM", "8:00PM", "Yaron", "WEEKEND"),
                    (add_day(current_date), add_day(current_date, days=1), "8:00PM", "8:00AM", member, "WEEKEND")
                ])
            else:
                schedule.append((current_date, add_day(current_date, days=2), "8:00AM", "8:00AM", member, "WEEKEND"))

            current_date = add_day(current_date, days=2)  # Skip to Sunday
        else:
            member = workday_rotation[0]
            workday_rotation = workday_rotation[1:] + workday_rotation[:1]
            schedule.append((current_date, add_day(current_date), "8:00AM", "8:00AM", member, "REGULAR"))
            current_date = add_day(current_date)

    return schedule

# Generate and print schedule
schedule = create_schedule(start_date, end_date, workday_rotation.copy(), weekend_rotation.copy())

print(f"\nSchedule From: {start_date.strftime('%A, %d/%m/%Y')}")
print(f"     Until: {end_date.strftime('%A, %d/%m/%Y')}\n")

print("| Date From | Date To  | Time From | Time To   | Team Member  |")
print("|------------|------------|-----------|-------------|----------------|")
for date_from, date_to, time_from, time_to, member, shift_type in schedule:
    date_from_fmt = date_from.strftime("%Y-%m-%d")
    date_to_fmt = date_to.strftime("%Y-%m-%d")
    color = "\033[38;5;214m" if shift_type == "WEEKEND" else "\033[1;91m"
    print(f"{color} {date_from_fmt:<12} {date_to_fmt:<12} {format_time(time_from):<12} {format_time(time_to):<12} {member}")

# ... (rest of your code with 'apply_run' logic remains the same) 
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


