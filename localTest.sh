#!/bin/zsh

clear

MACHINE_AGENT_FOLDER=/Users/fdumont/Downloads/AppD/machineagent-bundle-64bit-linux-aarch64-24.2.1.4062

ps -ef | grep machineagent | grep -v grep | awk '{print $2}' | xargs kill -9

mvn clean install

rm -rf $MACHINE_AGENT_FOLDER/logs/*.*
rm -rf $MACHINE_AGENT_FOLDER/monitors/VMWareTagExtension
rm -rf $MACHINE_AGENT_FOLDER/monitors/VMWareTagExtension-1.0.zip

unzip ./target/VMWareTagExtension-1.0.zip -d $MACHINE_AGENT_FOLDER/monitors/

( cd $MACHINE_AGENT_FOLDER && ./bin/machine-agent -j $JAVA_HOME -d -p pidfile )

echo "Press Ctrl+C to exit and kill machine agent"
trap 'ps -ef | grep machineagent | grep -v grep | awk "{print $2}" | xargs kill -9; exit' INT

sleep 2

( open $MACHINE_AGENT_FOLDER/logs/machine-agent.log )

while true; do
    sleep 1
done
