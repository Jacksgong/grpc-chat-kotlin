#!/bin/bash

nohup ./gradlew :server:run > running.log 2>&1 &
echo $! > .process_pid.txt
