# Backend Server Setup Guide for JJUTV Blippi App

## Quick Start (5 minutes)

### Step 1: Install Python & Dependencies

```bash
# Install Python 3.8+ if you don't have it
# Download from: https://www.python.org/downloads/

# Navigate to backend folder
cd backend

# Install dependencies
pip install -r requirements.txt
```

### Step 2: Start the Backend Server

```bash
python server.py
```

You should see:
```
* Running on http://0.0.0.0:5000
```

**Keep this terminal window open!**

### Step 3: Find Your Computer's IP Address

**Windows:**
```bash
ipconfig
```
Look for "IPv4 Address" (e.g., `192.168.1.100`)

**Mac/Linux:**
```bash
ifconfig
# or
ip addr
```

### Step 4: Update Android App

1. Open `BackendExtractor.kt`
2. Find line 13:
   ```kotlin
   private val BACKEND_URL = "http://192.168.1.5:5000"
   ```
3. Change `192.168.1.5` to YOUR computer's IP address
4. Save the file

### Step 5: Build & Install Android App

```bash
# In the main project directory
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 6: Test!

1. Make sure your Android TV is on the **same WiFi network** as your computer
2. Launch the app on your TV
3. Try to play a Blippi video
4. Watch the backend server terminal - you should see extraction requests!

---

## Troubleshooting

### "Connection refused" or "Backend unreachable"

**Problem:** App can't connect to backend server

**Solutions:**
1. Make sure backend server is running (`python server.py`)
2. Check that you're using the correct IP address
3. Ensure Android TV and computer are on same WiFi
4. Check Windows Firewall:
   - Allow Python through firewall
   - Or temporarily disable firewall for testing

5. Try pinging from Android TV:
   ```bash
   adb shell ping YOUR_COMPUTER_IP
   ```

### "yt-dlp extraction failed"

**Problem:** Backend can't extract videos

**Solutions:**
1. Update yt-dlp:
   ```bash
   pip install --upgrade yt-dlp
   ```

2. Test manually:
   ```bash
   curl "http://localhost:5000/api/extract?video_id=dQw4w9WgXcQ"
   ```

3. Check server logs for specific errors

### Videos not playing

**Problem:** URL extracted but won't play

**Solutions:**
1. The extracted URL might have expired (YouTube URLs expire after ~6 hours)
2. Try another video
3. Check ExoPlayer logs in Android logcat

---

## Production Deployment (Optional)

For a permanent solution, deploy to cloud:

### Deploy to Railway.app (FREE, easiest)

1. Go to https://railway.app
2. Sign up with GitHub
3. Create new project → Deploy from GitHub
4. Select your JJUTV repository
5. Railway auto-detects Python and deploys
6. Get your URL (e.g., `https://jjutv-production.up.railway.app`)
7. Update `BACKEND_URL` in `BackendExtractor.kt`

### Deploy to Render.com (FREE)

1. Go to https://render.com
2. Create new Web Service
3. Connect GitHub repo
4. Build command: `pip install -r backend/requirements.txt`
5. Start command: `cd backend && gunicorn server:app`
6. Get your URL
7. Update Android app

---

## Testing the API

### Test health check
```bash
curl http://localhost:5000/
```

### Extract a video
```bash
curl "http://localhost:5000/api/extract?video_id=dQw4w9WgXcQ"
```

### Get Blippi videos
```bash
curl "http://localhost:5000/api/channel/blippi?max_results=10"
```

---

## Current Setup

✅ Backend server created at: `backend/server.py`
✅ Android extractor created at: `app/src/main/java/com/example/jjutv/BackendExtractor.kt`
✅ PlayerActivity updated to use backend FIRST
✅ Automatic fallback to other extraction methods if backend fails

---

## Next Steps

1. **Start server:** `python backend/server.py`
2. **Update IP in app:** Edit `BackendExtractor.kt` line 13
3. **Build & install app**
4. **Test with Blippi videos!**

The backend server will:
- Extract video streams using yt-dlp (bypasses YouTube restrictions)
- Cache results for 1 hour (reduces YouTube requests)
- Serve direct video URLs to your Android app
- Work with ANY YouTube video, not just Blippi

---

## Important Notes

- Keep `yt-dlp` updated: `pip install --upgrade yt-dlp`
- YouTube changes their API frequently
- Backend server must be running for app to work (unless fallback methods succeed)
- For production, deploy to cloud instead of running on local computer

---

Need help? Check the logs:
- **Backend logs:** Terminal where you ran `python server.py`
- **Android logs:** `adb logcat | grep BackendExtractor`
