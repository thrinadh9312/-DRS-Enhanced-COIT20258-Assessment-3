@echo off
cd /d "%~dp0\.."
call mvn clean package
if errorlevel 1 exit /b 1
copy /y target\drs-enhanced-1.0.0.jar dist\drs-enhanced-1.0.0.jar
