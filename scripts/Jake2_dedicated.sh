#!/bin/bash

cd `dirname $0`

CP=lib/jake2.jar

exec java -Xmx64M -cp $CP jake2.Jake2
