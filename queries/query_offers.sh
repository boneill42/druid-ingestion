#!/bin/bash
BASEDIR=$(dirname $0)
curl -X POST "http://192.168.50.4:8090/druid/v2/?pretty" -H 'content-type: application/json' -d @$BASEDIR/offers_by_account.body
