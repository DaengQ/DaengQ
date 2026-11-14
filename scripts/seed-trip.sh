#!/usr/bin/env bash
# Opens the trip result screen on the emulator with a made-up trip, so the result screen can be checked without driving.
# It replaces the app's records, trip state and dog list.
#
#   scripts/seed-trip.sh <trip-minutes> <minute>:<TYPE> [<minute>:<TYPE> ...]
#   scripts/seed-trip.sh 30 2:CRYING 3:WHINING 22:CRYING 23:HOWLING
#
# TYPE is CRYING, WHINING or HOWLING. Records are spaced north of a point in Pangyo so each gets its own circle.
set -euo pipefail

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PKG=com.example.pawgo
ANDROID_USER="${ANDROID_USER:-10}"
# Pleos Connect emulator, 2560x1440: the first dog button on the start screen
DOG_BUTTON_X=1398
DOG_BUTTON_Y=1050

usage() { sed -n '2,8p' "$0" >&2; exit 2; }
[[ $# -ge 2 ]] || usage
minutes=$1; shift
[[ $minutes =~ ^[0-9]+$ && $minutes -gt 0 ]] || { echo "trip-minutes must be a positive number: $minutes" >&2; exit 2; }

now=$(( $(date +%s) * 1000 ))
start=$(( now - minutes * 60000 ))
json="" i=0
for spec in "$@"; do
  minute=${spec%%:*}
  type=${spec#*:}
  [[ $spec == *:* && $minute =~ ^[0-9]+$ ]] || { echo "expected <minute>:<TYPE>, got: $spec" >&2; exit 2; }
  case $type in CRYING|WHINING|HOWLING) ;; *) echo "TYPE must be CRYING, WHINING or HOWLING, got: '$type'" >&2; exit 2;; esac
  (( minute <= minutes )) || { echo "minute $minute is after the trip ends at $minutes" >&2; exit 2; }
  latitude=$(awk -v i="$i" 'BEGIN { printf "%.4f", 37.4020 + i * 0.0006 }')
  json+="${json:+,}{\"time\":$(( start + minute * 60000 )),\"behavior\":\"$type\",\"naviStatus\":null,\"address\":null,\"latitude\":$latitude,\"longitude\":127.1080,\"detected\":false}"
  i=$(( i + 1 ))
done

run_as() { "$ADB" shell "run-as $PKG --user $ANDROID_USER sh -c '$1'"; }

"$ADB" shell am force-stop --user "$ANDROID_USER" "$PKG"
echo "[$json]" | run_as "cat > files/pet_records.json"
run_as "mkdir -p shared_prefs"
printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n<long name="pendingStart" value="%s" />\n<long name="pendingEnd" value="%s" />\n</map>\n' "$start" "$now" \
  | run_as "cat > shared_prefs/trip.xml"
printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n<string name="names">[&quot;봄&quot;]</string>\n<string name="selected">봄</string>\n</map>\n' \
  | run_as "cat > shared_prefs/dogs.xml"

stored=$(run_as "cat files/pet_records.json" | grep -o '"behavior":"[A-Z]*"' | wc -l | tr -d ' ')
[[ $stored -eq $# ]] || { echo "wrote $# records but $stored have a valid behavior" >&2; exit 1; }

"$ADB" shell am start --user "$ANDROID_USER" -n "$PKG/.StartActivity" >/dev/null
sleep 3
"$ADB" shell input tap "$DOG_BUTTON_X" "$DOG_BUTTON_Y"
echo "seeded a ${minutes}-minute trip with $# records; the result screen should be open"
