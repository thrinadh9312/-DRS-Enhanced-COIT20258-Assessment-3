@echo off
cd /d "%~dp0"
java -cp "dist\drs-enhanced-1.0.0.jar" com.drs.Start %1
if errorlevel 1 pause
