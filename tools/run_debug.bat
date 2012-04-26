@echo off
break off

REM let variable K2VERSION exist only while this batch file runs.
setlocal

REM sets a value for variable K2VERSION
call tools\version.bat

if "%1" == "--suspend" (set suspend=y) else (set suspend=n)

java -Dfile.encoding=UTF8 -Xmx512m -XX:MaxPermSize=256m -server -Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=%suspend% -jar app/target/org.sakaiproject.nakamura.app-%K2VERSION%.jar -f - 

endlocal