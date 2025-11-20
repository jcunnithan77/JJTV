# JJUTV Backend Server - YouTube Video Extraction API

Backend server for extracting YouTube video streams for the JJUTV Blippi kids app.

## Features

- Extract direct video stream URLs from YouTube
- Fetch Blippi channel videos automatically
- Extract playlist videos
- 1-hour caching to reduce YouTube requests
- REST API for Android app integration

## Quick Start

### 1. Install Python Dependencies

```bash
cd backend
pip install -r requirements.txt
```

### 2. Run the Server Locally

```bash
python server.py
```

Server will start at `http://localhost:5000`

### 3. Test the API

```bash
# Health check
curl http://localhost:5000/

# Extract a video
curl "http://localhost:5000/api/extract?video_id=YOUR_VIDEO_ID"

# Get Blippi videos
curl "http://localhost:5000/api/channel/blippi?max_results=20"
```

## API Endpoints

### `GET /`
Health check

### `GET /api/extract?video_id=VIDEO_ID`
Extract video stream URL

**Response:**
```json
{
  "success": true,
  "video_id": "abc123",
  "url": "https://...",
  "title": "Blippi Video",
  "duration": 600,
  "thumbnail": "https://..."
}
```

### `GET /api/channel/blippi?max_results=50`
Get latest Blippi channel videos

**Response:**
```json
{
  "success": true,
  "videos": [...],
  "count": 50
}
```

### `GET /api/playlist?playlist_id=PLAYLIST_ID`
Extract playlist videos

### `POST /api/cache/clear`
Clear the cache

## Deployment Options

### Option A: Deploy on Your Local Network (Easiest)

1. Run server on your computer:
   ```bash
   python server.py
   ```

2. Find your computer's IP address:
   - Windows: `ipconfig` (look for IPv4)
   - Mac/Linux: `ifconfig` or `ip addr`

3. Update Android app to use: `http://YOUR_IP:5000`

4. Make sure your Android TV is on the same network

### Option B: Deploy to Cloud (Recommended for Production)

#### Deploy to Heroku (Free Tier)

1. Install Heroku CLI
2. Create `Procfile`:
   ```
   web: gunicorn server:app
   ```
3. Deploy:
   ```bash
   heroku create jjutv-backend
   heroku git:remote -a jjutv-backend
   git push heroku main
   ```
4. Your API will be at: `https://jjutv-backend.herokuapp.com`

#### Deploy to Railway.app (Free Tier)

1. Connect your GitHub repo to Railway
2. Railway will auto-detect Python and deploy
3. Get your URL from Railway dashboard

#### Deploy to Render.com (Free Tier)

1. Create new Web Service on Render
2. Connect GitHub repo
3. Build command: `pip install -r requirements.txt`
4. Start command: `gunicorn server:app`

#### Deploy to DigitalOcean/AWS/GCP

Standard Flask deployment - use gunicorn with nginx reverse proxy.

## Security Notes

- For production, add API key authentication
- Use HTTPS in production
- Consider rate limiting
- Add request validation

## Troubleshooting

### "yt-dlp extraction failed"
- YouTube may have updated their API
- Update yt-dlp: `pip install --upgrade yt-dlp`
- Check yt-dlp GitHub for issues

### Videos not loading in app
- Verify server is accessible from Android TV
- Check firewall settings
- Ensure same network for local deployment

### Cache issues
- Clear cache: `POST /api/cache/clear`
- Restart server to clear all caches

## Update yt-dlp Regularly

```bash
pip install --upgrade yt-dlp
```

YouTube frequently changes their API, so keep yt-dlp updated.

## Android App Integration

Update your Android app's server URL in `backend_config`:

```kotlin
const val BACKEND_SERVER_URL = "http://YOUR_SERVER_IP:5000"
// or
const val BACKEND_SERVER_URL = "https://your-domain.com"
```

## License

For JJUTV Blippi Kids App
