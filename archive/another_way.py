from datetime import datetime, timedelta

# Define team members and their special weekend shift eligibility
team_members = ["Dovid", "Yossi", "Moriah", "Tom", "Adiel", "Gour", "Shay"]
weekend_eligible = ["Yossi", "Tom", "Adiel", "Gour", "Shay"]

# Define the start and end dates of the rotation
start_date = datetime(2024, 4, 8)
end_date = datetime(2024, 5, 31)

# Initialize the rotation
rotation = []

# Initialize counters for each member
shift_counts = {member: 0 for member in team_members}

# Helper function to get the next team member for a shift
def get_next_member(current_member, team_list):
    current_index = team_list.index(current_member)
    return team_list[(current_index + 1) % len(team_list)]

# Start with the first member for weekday and weekend shifts
current_weekday_member = team_members[0]
current_weekend_member = next(member for member in weekend_eligible if member != "Dovid" and member != "Moriah")

# Loop over each day in the period
current_date = start_date
while current_date <= end_date:
    if current_date.weekday() < 4:  # Weekday
        rotation.append((current_date, current_weekday_member))
        shift_counts[current_weekday_member] += 1
        current_weekday_member = get_next_member(current_weekday_member, team_members)
    else:  # Weekend
        if current_weekend_member in ["Dovid", "Moriah"]:  # Special rule for Dovid and Moriah
            # Friday 8am to Friday 2pm
            rotation.append((current_date, current_weekend_member))
            shift_counts[current_weekend_member] += 1
            # Saturday 8pm to Sunday 8am
            next_day = current_date + timedelta(days=1)
            rotation.append((next_day + timedelta(hours=12), current_weekend_member))
            shift_counts[current_weekend_member] += 1
            # Friday 2pm to Saturday 8pm for another member
            next_weekend_member = get_next_member(current_weekend_member, weekend_eligible)
            rotation.append((current_date + timedelta(hours=6), next_weekend_member))
            shift_counts[next_weekend_member] += 1
            current_weekend_member = get_next_member(next_weekend_member, weekend_eligible)
        else:
            # Regular weekend shift
            rotation.append((current_date, current_weekend_member))
            shift_counts[current_weekend_member] += 2  # Counting the whole weekend as 2 shifts
            current_weekend_member = get_next_member(current_weekend_member, weekend_eligible)

    # Move to the next day, skip to Monday if it's Saturday
    if current_date.weekday() == 5:  # Saturday
        current_date += timedelta(days=2)
    else:
        current_date += timedelta(days=1)

# Since the approach above does not fully balance the shifts, especially for the special weekend shifts,
# additional balancing logic might be needed based on the shift counts.

# Let's check the balance of the shifts before we proceed.
shift_counts, rotation[:5]  # Show the first 5 rotations for an idea of the distribution and check balance
for date, member in rotation:
    print(f"Date: {date}, Shift: {member}")

