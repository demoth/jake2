#!/bin/sh

CP=lib/ant/ant.jar:lib/ant/ant-launcher.jar
CP=$CP:lib/xerces/xercesImpl.jar:lib/xerces/xml-apis.jar
CP=$CP:lib/proguard/proguard.jar
CP=$CP:$JAVA_HOME/lib/tools.jar

java -Dant.home=lib/ant -cp $CP org.apache.tools.ant.Main -buildfile build.xml $@