@echo off

set CP=lib/ant/ant.jar;lib/ant/ant-launcher.jar
set CP=%CP%;lib/xerces/xercesImpl.jar;lib/xerces/xml-apis.jar
set CP=%CP%;lib/proguard/proguard.jar
set CP=%CP%;%JAVA_HOME%/lib/tools.jar

java -Dant.home=lib/ant -cp %CP% org.apache.tools.ant.Main -buildfile build.xml %1 %2 %3 %4