@echo off
cd /d "%~dp0"
java -cp "dist\test-lib\*;dist\lib\*;dist\drs-enhanced-1.0.0.jar;dist\test-classes" org.junit.platform.console.ConsoleLauncher execute --scan-classpath --reports-dir=local-test-results
if errorlevel 1 pause
