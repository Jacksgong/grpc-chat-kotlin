#!/bin/bash

kill -9 `cat .process_pid.txt`
rm .process_pid.txt
