# Running JJTV Backend on Android TV

## What We've Done

1. **Installed Termux** on your Android TV
2. **Updated the app** to connect to `http://127.0.0.1:5000` (localhost)
3. **Created setup scripts** for easy installation

## Manual Setup Steps

Since you want the Python backend to run directly on your Android TV, follow these steps:

### Step 1: Copy Backend Files to Android TV

You have two options:

**Option A: Using USB Drive**
1. Copy the entire `D:\JC\tv\JJUTV\backend` folder to a USB drive
2. Plug the USB drive into your Android TV
3. Use a file manager app on Android TV to copy the `backend` folder to `/sdcard/jjtv-backend`

**Option B: Using ADB (from your PC)**
```bash
# Create directory on Android TV
adb -s 192.168.1.5:5555 shell mkdir -p /sdcard/jjtv-backend

# Push each file individually
adb -s 192.168.1.5:5555 push backend/server.py /sdcard/jjtv-backend/
adb -s 192.168.1.5:5555 push backend/requirements.txt /sdcard/jjtv-backend/
adb -s 192.168.1.5:5555 push backend/admin.html /sdcard/jjtv-backend/
```

### Step 2: Open Termux on Android TV

1. On your Android TV, launch the **Termux** app
2. You should see a terminal interface

### Step 3: Install Python and Dependencies

In the Termux terminal, type these commands one by one:

```bash
# Update package lists
pkg update -y

# Install Python
pkg install python -y

# Install pip packages
pip install flask flask-cors yt-dlp
```

### Step 4: Copy Files to Termux Home

```bash
# Copy backend files from sdcard to Termux home directory
cp -r /sdcard/jjtv-backend ~/jjtv-backend

# Go to the backend directory
cd ~/jjtv-backend
```

### Step 5: Start the Backend Server

```bash
# Run the server
python server.py
```

You should see:
```
 * Running on http://127.0.0.1:5000
Backend server started!
```

### Step 6: Test Your App

1. Keep Termux running in the background
2. Open the JJTV app on your Android TV
3. Play a video - it should now use the local backend!

## Important Notes

### Keeping the Server Running

**To keep the server running in the background:**
```bash
python server.py &
```

**To stop the server:**
Press `Ctrl+C` or close Termux

**To start server automatically when Termux opens:**
Add this line to `~/.bashrc`:
```bash
echo "cd ~/jjtv-backend && python server.py &" >> ~/.bashrc
```

### Troubleshooting

**If videos don't play:**
1. Check if Termux is still running
2. In Termux, verify the server is running: `ps | grep python`
3. Test the backend: `curl http://127.0.0.1:5000/`

**If Termux doesn't have Python:**
```bash
pkg install python -y
```

**If pip packages fail to install:**
```bash
pip install --upgrade pip
pip install flask flask-cors yt-dlp
```

## How It Works

1. **Your JJTV App** runs on Android TV (main app)
2. **Termux + Python Backend** runs on the same Android TV (background service)
3. **They communicate locally** via `http://127.0.0.1:5000`
4. **yt-dlp extracts videos** with Chrome cookies for best reliability
5. **ExoPlayer plays the streams** in your app

## File Locations

- Backend files: `~/jjtv-backend/` (inside Termux)
- APK location: `D:\JC\tv\JJUTV\app\build\outputs\apk\debug\app-debug.apk`
- Termux app: Installed on Android TV

## Admin Panel

Access the admin panel from any device on your network:
- If backend is running: `http://192.168.1.5:5000/admin` (replace with your Android TV IP)
- Or from Android TV browser: `http://127.0.0.1:5000/admin`

## Advantages of This Setup

1. **No external server needed** - Everything runs on your Android TV
2. **No internet dependency** - Works offline (except for YouTube streaming)
3. **No cookie issues** - Uses local Chrome cookies
4. **Fast and reliable** - yt-dlp is the best YouTube extractor
5. **Fallback methods** - If backend fails, app uses NewPipe/Piped/Invidious

## Alternative: Autostart with Boot

To make the backend start automatically when Android TV boots, you need to:

1. Install **Termux:Boot** addon (sideload APK)
2. Create autostart script:
```bash
mkdir -p ~/.termux/boot
echo "#!/data/data/com.termux/files/usr/bin/sh" > ~/.termux/boot/start-backend.sh
echo "cd ~/jjtv-backend && python server.py &" >> ~/.termux/boot/start-backend.sh
chmod +x ~/.termux/boot/start-backend.sh
```

---

## Quick Reference Commands

```bash
# Start server
cd ~/jjtv-backend && python server.py

# Start in background
cd ~/jjtv-backend && python server.py &

# Check if running
ps | grep python

# Stop server
pkill python

# View server logs
cd ~/jjtv-backend && python server.py

# Update yt-dlp
pip install --upgrade yt-dlp
```

---

**Your app is now ready!** The APK is installed and configured to use the local backend at `http://127.0.0.1:5000`.
