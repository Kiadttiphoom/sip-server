@echo off
cd /d "%~dp0"        :: ย้ายมาที่โฟลเดอร์เดียวกับ .bat
cd src
echo === Compiling SIP Server ===
javac -encoding UTF-8 -cp "../libs/*" controller\SipServer.java
if %errorlevel% neq 0 (
    echo Compile failed!
    pause
    exit /b
)
echo === Starting SIP Server ===
java -cp ".;../libs/*" controller.SipServer
pause
