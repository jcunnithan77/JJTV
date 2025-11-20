# JJUTV Backend API Documentation

## Channel Management APIs

### 1. Get All Channels
Get list of all channels in database.

**Endpoint:** `GET /api/channels`

**Response:**
```json
{
  "success": true,
  "channels": [
    {
      "id": 1,
      "channel_id": "UC5PYHgAzJ1jQzoyDQjOA1RA",
      "channel_name": "Blippi - Educational Videos for Kids",
      "description": "Main Blippi channel",
      "thumbnail": "",
      "created_at": "2025-11-20 11:23:39"
    }
  ],
  "count": 1
}
```

**Example:**
```bash
curl http://192.168.1.10:5000/api/channels
```

---

### 2. Add New Channel
Add a new YouTube channel to database.

**Endpoint:** `POST /api/channels`

**Request Body:**
```json
{
  "channel_id": "UCq6VFHwMzcMXbuKyG7SQYIg",
  "channel_name": "Peppa Pig Official",
  "description": "Peppa Pig Kids Channel",
  "thumbnail": ""
}
```

**Required Fields:**
- `channel_id` - YouTube channel ID (required)
- `channel_name` - Channel name (required)
- `description` - Channel description (optional)
- `thumbnail` - Thumbnail URL (optional)

**Response:**
```json
{
  "success": true,
  "message": "Channel added successfully",
  "channel": {
    "id": 3,
    "channel_id": "UCq6VFHwMzcMXbuKyG7SQYIg",
    "channel_name": "Peppa Pig Official",
    "description": "Peppa Pig Kids Channel",
    "thumbnail": ""
  }
}
```

**Example:**
```bash
curl -X POST http://192.168.1.10:5000/api/channels \
  -H "Content-Type: application/json" \
  -d '{"channel_id":"UCq6VFHwMzcMXbuKyG7SQYIg","channel_name":"Peppa Pig Official","description":"Peppa Pig Kids Channel"}'
```

**Error Responses:**
- `400` - Missing required fields
- `409` - Channel already exists

---

### 3. Delete Channel
Remove a channel from database.

**Endpoint:** `DELETE /api/channels/<channel_id>`

**Response:**
```json
{
  "success": true,
  "message": "Channel deleted successfully"
}
```

**Example:**
```bash
curl -X DELETE http://192.168.1.10:5000/api/channels/UCq6VFHwMzcMXbuKyG7SQYIg
```

**Error Responses:**
- `404` - Channel not found

---

## Video Extraction APIs

### 4. Extract Video Stream
Extract direct stream URL from YouTube video ID.

**Endpoint:** `GET /api/extract?video_id=VIDEO_ID`

**Parameters:**
- `video_id` - YouTube video ID (required)

**Response:**
```json
{
  "success": true,
  "video_id": "dQw4w9WgXcQ",
  "url": "https://manifest.googlevideo.com/api/manifest/hls_playlist/...",
  "title": "Video Title",
  "duration": 600,
  "thumbnail": "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
  "description": "Video description",
  "uploader": "Channel Name",
  "view_count": 1000000,
  "extracted_at": "2025-11-20T12:00:00"
}
```

**Example:**
```bash
curl "http://192.168.1.10:5000/api/extract?video_id=dQw4w9WgXcQ"
```

---

### 5. Get Channel Videos
Fetch latest videos from all channels in database.

**Endpoint:** `GET /api/channel/blippi?max_results=50`

**Parameters:**
- `max_results` - Maximum videos per channel (default: 50)
- `channel` - Specific channel index (optional)

**Response:**
```json
{
  "success": true,
  "videos": [
    {
      "video_id": "abc123",
      "title": "Video Title",
      "thumbnail": "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
      "url": "https://www.youtube.com/watch?v=abc123",
      "duration": 300,
      "uploader": "Channel Name"
    }
  ],
  "count": 50,
  "fetched_at": "2025-11-20T12:00:00"
}
```

**Example:**
```bash
curl "http://192.168.1.10:5000/api/channel/blippi?max_results=20"
```

---

### 6. Get Playlist Videos
Extract videos from a YouTube playlist.

**Endpoint:** `GET /api/playlist?playlist_id=PLAYLIST_ID&max_results=50`

**Parameters:**
- `playlist_id` - YouTube playlist ID (required)
- `max_results` - Maximum videos (default: 50)

**Response:**
```json
{
  "success": true,
  "playlist_id": "PLxyz",
  "playlist_title": "Playlist Name",
  "videos": [...],
  "count": 50,
  "fetched_at": "2025-11-20T12:00:00"
}
```

---

### 7. Clear Cache
Clear the video cache.

**Endpoint:** `POST /api/cache/clear`

**Response:**
```json
{
  "success": true,
  "message": "Cleared 10 cache entries"
}
```

---

## Database

**Location:** `backend/channels.db`

**Schema:**
```sql
CREATE TABLE channels (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_id TEXT UNIQUE NOT NULL,
    channel_name TEXT NOT NULL,
    description TEXT,
    thumbnail TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Default Channels:**
- Blippi - Educational Videos for Kids (`UC5PYHgAzJ1jQzoyDQjOA1RA`)
- Blippi Toys (`UCqwjm_R3H1F6i8KBYmPh82A`)

---

## How to Find YouTube Channel ID

1. Go to the channel page on YouTube
2. Click on the channel name
3. Look at the URL:
   - New format: `youtube.com/@username` → Use YouTube search to find channel ID
   - Old format: `youtube.com/channel/UC...` → The part after `/channel/` is the channel ID

Or use this trick:
1. Go to any video from the channel
2. Right-click → View page source
3. Search for `"channelId":`
4. Copy the ID (starts with `UC`)

---

## Android App Integration

The Android app's `BackendExtractor.kt` connects to this backend at:
```kotlin
private val BACKEND_URL = "http://192.168.1.10:5000"
```

Change this IP to your backend server's address.

---

## Server Status

- **URL:** http://192.168.1.10:5000
- **Status:** Check with `GET /`
- **Dependencies:** Python 3.8+, Flask, yt-dlp
- **Database:** SQLite 3
- **Cache:** 1 hour TTL

---

## Notes

- Keep yt-dlp updated: `pip install --upgrade yt-dlp`
- YouTube changes API frequently
- HLS stream URLs expire after ~6 hours
- Backend must be running for video extraction
- All channels from database are used for video fetching
