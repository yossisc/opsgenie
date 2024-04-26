for p2 in Dovid Yossi Moriah Tom Adiel Gour Shay; do 
	for p1 in Dovid Yossi Moriah Tom Adiel Gour Shay; do
	echo "first_workday $p1"
	echo "first_weekend $p2"
	./team_schedule_1_1_2024_special_list_test_moriah.py --first_workday $p1 --first_weekend $p2 | awk '{print $2}' |sort | uniq -c | awk '{print $1}' | sort | uniq -c 
	echo "-----------------------------"
	echo
done; done
