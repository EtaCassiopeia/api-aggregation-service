#!/usr/bin/env bash

seq 1 200 | xargs -I $ -n1 -P10 time curl 'http://localhost:8181/aggregation?pricing=CN&track=109347263&shipments=109347263'
