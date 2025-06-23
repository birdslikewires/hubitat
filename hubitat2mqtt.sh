#!/usr/bin/env bash

## hubitat2mqtt.sh v1.01 (23rd June 2025)
##  Converts the HTTP POST output of Hubitat's Maker API into valid JSON,
##  then publishes that data with the same topic format as Zigbee2MQTT.

broker="192.168.11.10"
mqttuser="hubitat2mqtt"
mqttpass="hubitat2mqtt"

# Lose the wrapping.
data=$(echo "$1" | sed 's/{//g; s/}//g' )
data=${data#content:}

# Split the pairs into an array.
IFS=',' read -r -a pairs <<< "$data"
declare -A arr
for pair in "${pairs[@]}"; do
  IFS=':' read -r key value <<< "$pair"
  arr[$key]=$value
done

# Muddle the array into valid notation.
json="{"
for key in "${!arr[@]}"; do
  value=${arr[$key]}
  if [[ $value =~ ^-?[0-9]+(\.[0-9]+)?$ ]] || [[ $value == "null" ]]; then
    json+="\"$key\":$value,"
  else
    value=$(echo "$value" | sed 's/"/\\"/g')
    json+="\"$key\":\"$value\","
  fi
done
json="${json%,}"
json+="}"

# Grab the device name.
name=$(echo $json | jq -r '.displayName')

# Publish!
mosquitto_pub -h "$broker" -u "$mqttuser" -P "$mqttpass" -t "hubitat2mqtt/$name" -m "$json"

exit 0
