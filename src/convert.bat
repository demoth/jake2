
echo convert
java4 -cp ..\build ConvertDefines

echo backup
move jake2\Defines.java .\Defines.java.bak

echo overwrite
move jake2\Defines.java.new jake2\Defines.java

echo done.

