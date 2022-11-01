#!/usr/bin/env bash

## nut_mqtt.sh v1.01 (1st November 2022)
##  Updates local MQTT broker with values from NUT.


usage() {

	echo "Usage: $0 <\"ups1 [ups2]\">"
	exit 1

}


if [ $# -eq 0 ] || [ $# -gt 1 ] || [[ "$1" == "-h" ]] || [[ "$1" == "--help" ]]; then

	usage

fi


for u in $1; do

	ups=$(upsc $u 2>/dev/null)
	n=1
	values=""
	IFS=$'\n':

	for v in $ups; do

		if [ $n -eq 1 ]; then

			value=$(echo $v | sed "s/\.//g")
			value="\"$value\":"
			n=$((n+1))

		else

			l=$(echo $v | sed -e 's/^[[:space:]]*//')
			value="\"$l\","
			n=1

		fi

		values="${values}${value}"

	done

	values=${values::-1}

	echo "{$values}"

	mosquitto_pub -h 127.0.0.1 -t "ups/$u" -m "{$values}"

done
