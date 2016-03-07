# druid-ingestion

This project demonstrates batch and real-time ingestion for Druid.
Real-time ingestion uses Tranquility.

To run these examples, fire up the druid cluster using:
https://github.com/boneill42/druid-vagrant


## Real-time Ingest

Compile w/ gradle.  The unit test will submit a real-time job to Druid.

You should be able to see a real-time task submitted on:
http://192.168.50.4:8081/

You can query for the results with:
```query_offers.sh```

## Batch Ingest

Simply run ```batch_ingest.sh```
