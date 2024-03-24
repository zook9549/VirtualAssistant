#!/bin/sh

export TZ='America/New_York'
export PATH=/apps/ffmpeg/ffmpeg-6.1-amd64-static:$PATH

cd /apps/VirtualAssistant

pkill -f VirtualAssistant
unzip -oq VirtualAssistant-0.0.1-SNAPSHOT.jar
java -Dspring.profiles.active=prod -cp ./:./lib/*:./:./BOOT-INF/classes:./BOOT-INF/lib/* ai.asktheexpert.virtualassistant.VirtualAssistantApplication
