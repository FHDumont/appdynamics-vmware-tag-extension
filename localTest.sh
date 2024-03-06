#!/bin/zsh

clear

kill_process() {
	PID=$(ps aux | grep machineagent | grep -v grep | awk '{print $2}')
	if [ "$PID" != "" ]; then
        	echo Killing Machine Agent
	        kill -9 $PID
	fi
}
kill_process

MACHINE_AGENT_FOLDER=/Users/fdumont/Downloads/AppD/machineagent-bundle-64bit-linux-aarch64-24.2.1.4062

mvn clean install

rm -rf $MACHINE_AGENT_FOLDER/logs/*.*
rm -rf $MACHINE_AGENT_FOLDER/monitors/VMWareTagExtension
rm -rf $MACHINE_AGENT_FOLDER/monitors/VMWareTagExtension*.zip

unzip ./target/VMWareTagExtension-1.0.zip -d $MACHINE_AGENT_FOLDER/monitors/

( cd $MACHINE_AGENT_FOLDER; ./bin/machine-agent -j $JAVA_HOME -d -p pidfile )

echo "Press Ctrl+C to exit and kill machine agent"
trap 'kill_process; exit' INT

sleep 5

( open $MACHINE_AGENT_FOLDER/logs/machine-agent.log )

while true; do
    sleep 1
done
