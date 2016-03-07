#!/bin/bash
BASEDIR=$(dirname $0)
echo "NOTE: This script assumes you've cloned druid-vagrant-cluster to ~/git/druid-vagrant-cluster"
echo "If it is not in that location, you'll need to edit this script."
scp -i ~/git/druid-vagrant-cluster/.vagrant/machines/druid/virtualbox/private_key  $BASEDIR/../data/data.csv vagrant@192.168.50.4:/home/vagrant/druid/
