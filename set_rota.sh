#!/usr/bin/env bash
GenieKey=$(cat ~/.config/ops_genie_api_key |xargs)
ScheduleName='GB-INFRA-Schedule'
RotationName='normal'

if [ "$#" -ne 3 ]; then
    echo "Error: Exactly 3 arguments required."
    exit 1
fi

case $1 in
  "Yossi") UserName='yossi.schwartz@glassboxdigital.com' ;;
  "Dovid") UserName='dovid.friedman@glassboxdigital.com' ;;
  "Tom") UserName='tom.halo@glassboxdigital.com' ;;
  "Moriah") UserName='moriah.popovsky@glassboxdigital.com' ;;
  "Yaron") UserName='yaron@glassboxdigital.com' ;;
  "Adiel") UserName='adiel.levy@glassboxdigital.com' ;;
  "Gour") UserName='gour.hadad@glassboxdigital.com' ;;
  "Shay") UserName='shay.blanc@glassboxdigital.com' ;;
  "Nadav") UserName='nadav.kosovsky@glassboxdigital.com' ;;
  "Gabi") UserName='gavriel.matatov@glassboxdigital.com' ;;
  *) UserName='NULL' ;;
esac

startDate="$2"
endDate="$3"

curl -X POST  "https://api.opsgenie.com/v2/schedules/${ScheduleName}/overrides?scheduleIdentifierType=name" \
    --header "Authorization: GenieKey ${GenieKey}" \
    --header 'Content-Type: application/json' \
    --data '''{
  "alias" : "Override Alias '"${UserName} ${startDate}"'",
	"user" : {
		"type" : "user",
		"username": "'"${UserName}"'"
	},
	"startDate" : "'"${startDate}"'",
	"endDate" : "'"${endDate}"'",
	"rotations" : [
		{
            "name": "'"${RotationName}"'"
		}
	]
}'''

echo "-------"
