@echo off
REM JJUTV - Install App and Backend to Android TV
REM Run this script from your Windows PC

echo ================================================
echo  JJUTV - TV Installation Script
echo ================================================
echo.

set TV_IP=192.168.1.9
set TV_PORT=5555

echo Connecting to Android TV at %TV_IP%...
adb connect %TV_IP%:%TV_PORT%

if %errorlevel% neq 0 (
    echo ERROR: Could not connect to Android TV
    echo Make sure:
    echo 1. TV is on the same network
    echo 2. ADB debugging is enabled on TV
    echo 3. IP address %TV_IP% is correct
    pause
    exit /b 1
)

echo.
echo ================================================
echo  STEP 1: Installing JJUTV App
echo ================================================
echo.

echo Installing APK to TV...
adb -s %TV_IP%:%TV_PORT% install -r app\build\outputs\apk\debug\app-debug.apk

if %errorlevel% neq 0 (
    echo ERROR: Failed to install APK
    pause
    exit /b 1
)

echo.
echo SUCCESS: App installed!
echo.

echo ================================================
echo  STEP 2: Transferring Backend Files
echo ================================================
echo.

echo Creating backend directory on TV...
adb -s %TV_IP%:%TV_PORT% shell mkdir -p /sdcard/jjtv-backend

echo Pushing server.py...
adb -s %TV_IP%:%TV_PORT% push backend\server.py /sdcard/jjtv-backend/

echo Pushing admin.html...
adb -s %TV_IP%:%TV_PORT% push backend\admin.html /sdcard/jjtv-backend/

echo Pushing requirements.txt...
adb -s %TV_IP%:%TV_PORT% push backend\requirements.txt /sdcard/jjtv-backend/

echo Pushing add_blippi_group.py...
adb -s %TV_IP%:%TV_PORT% push backend\add_blippi_group.py /sdcard/jjtv-backend/

echo.
echo SUCCESS: Backend files transferred!
echo.

echo ================================================
echo  INSTALLATION COMPLETE!
echo ================================================
echo.
echo Next steps:
echo 1. Open Termux on your Android TV
echo 2. Run these commands:
echo.
echo    pkg update -y
echo    pkg install python -y
echo    cp -r /sdcard/jjtv-backend ~/jjtv-backend
echo    cd ~/jjtv-backend
echo    pip install flask flask-cors yt-dlp
echo    python server.py
echo.
echo 3. Keep Termux running
echo 4. Open the JJUTV app
echo.
echo For detailed instructions, see TV_DEPLOYMENT_GUIDE.md
echo.

pause
