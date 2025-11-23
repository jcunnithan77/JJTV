# JJUTV - Complete TV Deployment Guide

## Quick Overview

This guide will help you:
1. Install the JJUTV app on your Android TV
2. Set up the backend server to run on your Android TV using Termux
3. Configure everything to work together

## What You Need

- Android TV (IP: 192.168.1.5)
- USB drive or ADB connection to your PC
- Termux app installed on Android TV
- The files from this project

---

## PART 1: Install the JJUTV App on TV

### Method A: Using ADB (Recommended)

From your PC, run:

```bash
adb connect 192.168.1.5:5555
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Method B: Using USB Drive

1. Copy `app\build\outputs\apk\debug\app-debug.apk` to a USB drive
2. Plug USB drive into your Android TV
3. Use a file manager app on TV to install the APK
4. Allow installation from unknown sources if prompted

---

## PART 2: Set Up Backend Server on TV

### Step 1: Transfer Backend Files to TV

**Using ADB (from your PC):**

```bash
# Create directory on TV
adb -s 192.168.1.5:5555 shell mkdir -p /sdcard/jjtv-backend

# Push all backend files
adb -s 192.168.1.5:5555 push backend/server.py /sdcard/jjtv-backend/
adb -s 192.168.1.5:5555 push backend/admin.html /sdcard/jjtv-backend/
adb -s 192.168.1.5:5555 push backend/requirements.txt /sdcard/jjtv-backend/
adb -s 192.168.1.5:5555 push backend/add_blippi_group.py /sdcard/jjtv-backend/
```

**OR Using USB Drive:**

1. Copy the entire `backend` folder to USB drive
2. Plug USB into TV
3. Use file manager to copy to `/sdcard/jjtv-backend`

### Step 2: Install Termux on TV

If not already installed:
1. Download Termux APK from F-Droid or GitHub
2. Install on your Android TV

### Step 3: Set Up Python Backend in Termux

Open Termux on your TV and run these commands:

```bash
# Update packages
pkg update -y

# Install Python
pkg install python -y

# Copy backend files from sdcard to Termux
cp -r /sdcard/jjtv-backend ~/jjtv-backend

# Go to backend directory
cd ~/jjtv-backend

# Install Python dependencies
pip install flask flask-cors yt-dlp

# Start the server
python server.py
```

You should see:
```
Database initialized successfully
 * Running on http://127.0.0.1:5000
```

**Keep Termux running!**

---

## PART 3: Test Everything

### Test the Backend

From Termux, open a new session (swipe right) and run:

```bash
curl http://127.0.0.1:5000/
```

You should see a JSON response with "status": "online"

### Test the App

1. Open the JJUTV app on your TV
2. You should see the main screen with video groups
3. Click on a group and try playing a video

---

## PART 4: Making Backend Start Automatically

### Option 1: Start on Termux Launch

Add to Termux startup:

```bash
echo 'cd ~/jjtv-backend && python server.py &' >> ~/.bashrc
source ~/.bashrc
```

Now the backend starts every time you open Termux.

### Option 2: Keep Running in Background

In Termux, run:

```bash
cd ~/jjtv-backend
python server.py &
```

This runs the server in the background.

---

## PART 5: Access Admin Panel

You can manage video groups from the admin panel:

**From your TV browser:**
```
http://127.0.0.1:5000/admin
```

**From any device on your network:**
```
http://192.168.1.5:5000/admin
```

Here you can:
- Create new video groups from YouTube playlists or channels
- Add/remove videos from groups
- Set up schedules/break times
- Block videos or channels

---

## File Locations Summary

| Item | Location |
|------|----------|
| APK File | `D:\JC\tv\JJUTV\app\build\outputs\apk\debug\app-debug.apk` |
| Backend on PC | `D:\JC\tv\JJUTV\backend\` |
| Backend on TV | `/sdcard/jjtv-backend/` (storage) |
| Backend in Termux | `~/jjtv-backend/` (working directory) |
| Database | `~/jjtv-backend/jjutv.db` (created on first run) |

---

## Common Commands

### Check if Backend is Running
```bash
ps | grep python
```

### Stop Backend
```bash
pkill python
```

### Restart Backend
```bash
cd ~/jjtv-backend && python server.py
```

### Update yt-dlp
```bash
pip install --upgrade yt-dlp
```

### View Backend Logs
```bash
cd ~/jjtv-backend && python server.py
```

---

## Troubleshooting

### App shows "Cannot connect to backend server"
1. Make sure Termux is running
2. Check if server is running: `ps | grep python`
3. Restart the backend: `cd ~/jjtv-backend && python server.py`

### Videos won't play
1. Check internet connection
2. Update yt-dlp: `pip install --upgrade yt-dlp`
3. Check Termux logs for errors

### App doesn't show any groups
1. Access admin panel: `http://127.0.0.1:5000/admin`
2. Create a video group by adding a YouTube playlist or channel
3. Refresh the app

### Backend won't start
1. Make sure Python is installed: `pkg install python -y`
2. Install dependencies: `pip install flask flask-cors yt-dlp`
3. Check for errors in the output

---

## How It All Works

```
┌─────────────────────────────────────────┐
│         Your Android TV                 │
│                                         │
│  ┌──────────────┐    ┌──────────────┐  │
│  │  JJUTV App   │───▶│   Termux     │  │
│  │              │    │              │  │
│  │ (Frontend)   │    │  Python +    │  │
│  │              │    │  Backend     │  │
│  │ ExoPlayer    │◀───│  yt-dlp      │  │
│  └──────────────┘    └──────────────┘  │
│         │                    │          │
│         └────────────────────┘          │
│        http://127.0.0.1:5000           │
│                                         │
└─────────────────────────────────────────┘
                   │
                   ▼
             YouTube.com
         (video streaming)
```

1. **JJUTV App** displays the UI and plays videos
2. **Backend Server** (in Termux) extracts YouTube stream URLs using yt-dlp
3. **SQLite Database** stores video groups, schedules, and settings
4. **Everything runs locally** on your TV - no external server needed!

---

## Next Steps

1. Install the app APK on your TV
2. Set up the backend in Termux
3. Create video groups via the admin panel
4. Enjoy your videos!

---

**All done!** Your JJUTV app and backend are now running on your Android TV.
