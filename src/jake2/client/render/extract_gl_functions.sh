cat *.java | egrep "\.gl" | grep \( |  sed 's/(.*$//g'| grep -v "//" | grep ".gl" | sed 's/^.*\.gl/gl\.gl/g' | sed 's/ //g' | sort | uniq > $1

