
echo convert
java -cp ..\build ConvertDefines

echo backup
move jake2\game\defs.java .\defs.java.bak

echo overwrite
move jake2\game\defs.java.new jake2\game\defs.java

echo done.

