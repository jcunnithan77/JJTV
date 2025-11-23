@echo off
REM Start JJTV Backend on Android TV via ADB

set TV_IP=192.168.1.9
set TV_PORT=5555

echo ================================================
echo  Starting JJTV Backend on TV
echo ================================================
echo.

echo Connecting to TV...
adb connect %TV_IP%:%TV_PORT%

echo.
echo Opening Termux on TV...
adb -s %TV_IP%:%TV_PORT% shell "am start -n com.termux/.app.TermuxActivity"

echo.
echo ================================================
echo MANUAL STEP REQUIRED:
echo ================================================
echo.
echo On your TV, Termux should now be open.
echo.
echo Please run these commands in Termux:
echo.
echo   cd ~/jjtv-backend
echo   python server.py
echo.
echo Or to run in background:
echo.
echo   cd ~/jjtv-backend
echo   nohup python server.py ^> backend.log 2^>^&1 ^&
echo.
echo To view logs:
echo   tail -f ~/jjtv-backend/backend.log
echo.
pause
