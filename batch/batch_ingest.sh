#!/bin/bash

BASEDIR=$(dirname $0)
curl -X 'POST' -H 'Content-Type:application/json' -d @$BASEDIR/batch/batch_spec.json 192.168.50.4:8080/druid/indexer/v1/task
