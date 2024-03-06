#!/bin/zsh

clear

mvn clean install

rm -rf /Users/fdumont/Downloads/AppD/machineagent-bundle-64bit-linux-aarch64-24.2.1.4062/logs
rm -rf /Users/fdumont/Downloads/AppD/machineagent-bundle-64bit-linux-aarch64-24.2.1.4062/monitors/VMWareTagExtension
rm -rf /Users/fdumont/Downloads/AppD/machineagent-bundle-64bit-linux-aarch64-24.2.1.4062/monitors/VMWareTagExtension-1.0.zip

unzip ./target/VMWareTagExtension-1.0.zip -d /Users/fdumont/Downloads/AppD/machineagent-bundle-64bit-linux-aarch64-24.2.1.4062/monitors/

( cd /Users/fdumont/Downloads/AppD/machineagent-bundle-64bit-linux-aarch64-24.2.1.4062 && java -Dappdynamics.agent.maxMetrics=50000 -jar machineagent.jar)