#!/bin/bash
cd ..
cp properties/ target/ -r
#cd target && nohup java -jar m3u8Filter-1.0-SNAPSHOT.jar > capture.file 2>&1 &
cd target && java -jar m3u8Filter-1.0-SNAPSHOT.jar
