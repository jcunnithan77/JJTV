#!/usr/bin/env python3
"""
YouTube Video Extraction Server for JJUTV (Blippi Kids App)
Uses yt-dlp to extract video streams and serves them via REST API
"""

from flask import Flask, jsonify, request, send_file
from flask_cors import CORS
import yt_dlp
import logging
from datetime import datetime, timedelta
from functools import lru_cache
import json
import os
import uuid
import sqlite3
from contextlib import contextmanager

app = Flask(__name__)
CORS(app)  # Enable CORS for Android app

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Blippi channel IDs
BLIPPI_CHANNELS = [
    'UC5PYHgAzJ1jQzoyDQjOA1RA',  # Blippi - Educational Videos for Kids
    'UCqwjm_R3H1F6i8KBYmPh82A'   # Blippi Toys
]

# yt-dlp options for best extraction
YDL_OPTS = {
    # Simpler format selection - get any working stream
    # ExoPlayer can handle various formats including DASH
    'format': 'best',
    'quiet': False,  # Show errors for debugging
    'no_warnings': False,
    'extract_flat': False,
    'user_agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
}

# Add cookie support if running locally (Chrome available)
# On production servers like Render, skip cookies to avoid errors
import os
if os.path.exists(os.path.expanduser('~/.config/google-chrome')) or os.path.exists(os.path.expanduser('~/AppData/Local/Google/Chrome')):
    YDL_OPTS['cookiesfrombrowser'] = ('chrome',)
    logger.info("Chrome detected - using browser cookies for YouTube authentication")
else:
    logger.info("Chrome not detected - skipping cookie authentication")

# Cache for 1 hour to reduce YouTube requests
cache_timeout = timedelta(hours=1)
video_cache = {}

# SQLite database file
DATABASE_FILE = 'jjutv.db'

@contextmanager
def get_db():
    """Context manager for database connections"""
    conn = sqlite3.connect(DATABASE_FILE)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
    finally:
        conn.close()

def init_database():
    """Initialize SQLite database with required tables"""
    with get_db() as conn:
        cursor = conn.cursor()

        # Groups table
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS groups (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT,
                created_at TEXT NOT NULL
            )
        ''')

        # Videos table (stores videos for each group)
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS videos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_id TEXT NOT NULL,
                video_id TEXT NOT NULL,
                title TEXT NOT NULL,
                thumbnail TEXT,
                duration INTEGER,
                uploader TEXT,
                FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
                UNIQUE(group_id, video_id)
            )
        ''')

        # Schedules table
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS schedules (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                start_time TEXT NOT NULL,
                end_time TEXT NOT NULL,
                message TEXT,
                voice_enabled INTEGER DEFAULT 1,
                voice_repeat INTEGER DEFAULT 1,
                days TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
        ''')

        # Blocked videos table
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS blocked_videos (
                video_id TEXT PRIMARY KEY,
                reason TEXT,
                blocked_at TEXT NOT NULL
            )
        ''')

        # Blocked channels table
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS blocked_channels (
                channel_id TEXT PRIMARY KEY,
                reason TEXT,
                blocked_at TEXT NOT NULL
            )
        ''')

        conn.commit()
        logger.info("Database initialized successfully")

def load_groups():
    """Load video groups from SQLite database"""
    try:
        with get_db() as conn:
            cursor = conn.cursor()

            # Fetch all groups
            cursor.execute('SELECT id, name, description, created_at FROM groups')
            groups = []

            for row in cursor.fetchall():
                group = {
                    'id': row['id'],
                    'name': row['name'],
                    'description': row['description'],
                    'created_at': row['created_at'],
                    'videos': []
                }

                # Fetch videos for this group
                cursor.execute('''
                    SELECT video_id, title, thumbnail, duration, uploader
                    FROM videos
                    WHERE group_id = ?
                ''', (row['id'],))

                for video_row in cursor.fetchall():
                    group['videos'].append({
                        'video_id': video_row['video_id'],
                        'title': video_row['title'],
                        'thumbnail': video_row['thumbnail'],
                        'duration': video_row['duration'],
                        'uploader': video_row['uploader']
                    })

                groups.append(group)

            return {'groups': groups}
    except Exception as e:
        logger.error(f"Error loading groups: {e}")
        return {'groups': []}

def load_schedules():
    """Load schedules from SQLite database"""
    try:
        with get_db() as conn:
            cursor = conn.cursor()
            cursor.execute('''
                SELECT id, name, start_time, end_time, message,
                       voice_enabled, voice_repeat, days, created_at
                FROM schedules
            ''')

            schedules = []
            for row in cursor.fetchall():
                schedules.append({
                    'id': row['id'],
                    'name': row['name'],
                    'start_time': row['start_time'],
                    'end_time': row['end_time'],
                    'message': row['message'],
                    'voice_enabled': bool(row['voice_enabled']),
                    'voice_repeat': row['voice_repeat'],
                    'days': json.loads(row['days']),
                    'created_at': row['created_at']
                })

            return {'schedules': schedules}
    except Exception as e:
        logger.error(f"Error loading schedules: {e}")
        return {'schedules': []}

def save_schedules(data):
    """Save schedules to SQLite database (not used anymore, kept for compatibility)"""
    # This function is no longer needed with SQLite
    # Individual schedule operations are handled by create/delete endpoints
    return True

def load_blocked():
    """Load blocked items from SQLite database"""
    try:
        with get_db() as conn:
            cursor = conn.cursor()

            # Load blocked videos
            cursor.execute('SELECT video_id, reason, blocked_at FROM blocked_videos')
            videos = []
            for row in cursor.fetchall():
                videos.append({
                    'video_id': row['video_id'],
                    'reason': row['reason'],
                    'blocked_at': row['blocked_at']
                })

            # Load blocked channels
            cursor.execute('SELECT channel_id, reason, blocked_at FROM blocked_channels')
            channels = []
            for row in cursor.fetchall():
                channels.append({
                    'channel_id': row['channel_id'],
                    'reason': row['reason'],
                    'blocked_at': row['blocked_at']
                })

            return {'videos': videos, 'channels': channels}
    except Exception as e:
        logger.error(f"Error loading blocked items: {e}")
        return {'videos': [], 'channels': []}

def save_blocked(data):
    """Save blocked items to SQLite database (not used anymore, kept for compatibility)"""
    # This function is no longer needed with SQLite
    # Individual block operations are handled by create/delete endpoints
    return True

def save_groups(data):
    """Save video groups to SQLite database (not used anymore, kept for compatibility)"""
    # This function is no longer needed with SQLite
    # Individual group operations are handled by create/delete endpoints
    return True

def fetch_video_info(video_id):
    """Fetch basic video info from YouTube"""
    try:
        url = f'https://www.youtube.com/watch?v={video_id}'
        opts = YDL_OPTS.copy()
        opts['extract_flat'] = True

        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)
            return {
                'video_id': video_id,
                'title': info.get('title', 'Unknown Title'),
                'thumbnail': info.get('thumbnail', f'https://i.ytimg.com/vi/{video_id}/hqdefault.jpg'),
                'duration': info.get('duration', 0),
                'uploader': info.get('uploader', 'Unknown')
            }
    except Exception as e:
        logger.warning(f"Could not fetch info for {video_id}: {e}")
        return {
            'video_id': video_id,
            'title': 'Unknown Title',
            'thumbnail': f'https://i.ytimg.com/vi/{video_id}/hqdefault.jpg',
            'duration': 0,
            'uploader': 'Unknown'
        }

@app.route('/')
def index():
    """Health check endpoint"""
    return jsonify({
        'status': 'online',
        'service': 'JJUTV Video Extraction Server',
        'version': '1.0',
        'timestamp': datetime.now().isoformat()
    })

@app.route('/api/extract', methods=['GET', 'POST'])
def extract_video():
    """
    Extract video stream URL from YouTube video ID

    Query params:
        video_id: YouTube video ID (required)
        quality: Preferred quality (optional, default: best)

    Returns:
        JSON with video URL, title, thumbnail, etc.
    """
    try:
        # Get video ID from query params or JSON body
        video_id = request.args.get('video_id') or request.json.get('video_id')

        if not video_id:
            return jsonify({'error': 'video_id is required'}), 400

        logger.info(f"Extracting video: {video_id}")

        # Check cache
        cache_key = f"video_{video_id}"
        if cache_key in video_cache:
            cached_data, cache_time = video_cache[cache_key]
            if datetime.now() - cache_time < cache_timeout:
                logger.info(f"Returning cached data for {video_id}")
                return jsonify(cached_data)

        # Extract video info using yt-dlp
        url = f'https://www.youtube.com/watch?v={video_id}'

        with yt_dlp.YoutubeDL(YDL_OPTS) as ydl:
            info = ydl.extract_info(url, download=False)

            # Get best format URL
            video_url = info.get('url')

            # If no direct URL, try formats
            if not video_url and 'formats' in info:
                formats = info['formats']
                # Prefer format with both video and audio
                format_with_audio = next(
                    (f for f in formats if f.get('acodec') != 'none' and f.get('vcodec') != 'none'),
                    None
                )
                if format_with_audio:
                    video_url = format_with_audio.get('url')
                elif formats:
                    video_url = formats[-1].get('url')

            if not video_url:
                return jsonify({'error': 'Could not extract video URL'}), 500

            # Prepare response
            response_data = {
                'success': True,
                'video_id': video_id,
                'url': video_url,
                'title': info.get('title', 'Unknown'),
                'duration': info.get('duration', 0),
                'thumbnail': info.get('thumbnail', ''),
                'description': info.get('description', ''),
                'uploader': info.get('uploader', ''),
                'view_count': info.get('view_count', 0),
                'extracted_at': datetime.now().isoformat()
            }

            # Cache the result
            video_cache[cache_key] = (response_data, datetime.now())

            logger.info(f"Successfully extracted: {info.get('title')}")
            return jsonify(response_data)

    except Exception as e:
        logger.error(f"Error extracting video {video_id}: {str(e)}")
        return jsonify({
            'success': False,
            'error': str(e),
            'video_id': video_id
        }), 500

@app.route('/api/channel/blippi', methods=['GET'])
def get_blippi_videos():
    """
    Fetch latest Blippi channel videos

    Query params:
        max_results: Maximum number of videos (default: 50)
        channel: Specific channel index (0 or 1, default: both)

    Returns:
        JSON array of video objects
    """
    try:
        max_results = int(request.args.get('max_results', 50))
        channel_index = request.args.get('channel')

        logger.info(f"Fetching Blippi videos (max: {max_results})")

        # Check cache
        cache_key = f"blippi_channel_{max_results}_{channel_index}"
        if cache_key in video_cache:
            cached_data, cache_time = video_cache[cache_key]
            if datetime.now() - cache_time < cache_timeout:
                logger.info("Returning cached Blippi videos")
                return jsonify(cached_data)

        all_videos = []
        channels_to_fetch = BLIPPI_CHANNELS if channel_index is None else [BLIPPI_CHANNELS[int(channel_index)]]

        for channel_id in channels_to_fetch:
            logger.info(f"Fetching from channel: {channel_id}")

            url = f'https://www.youtube.com/channel/{channel_id}/videos'

            opts = YDL_OPTS.copy()
            opts['extract_flat'] = True
            opts['playlistend'] = max_results

            with yt_dlp.YoutubeDL(opts) as ydl:
                try:
                    info = ydl.extract_info(url, download=False)

                    if 'entries' in info:
                        for entry in info['entries'][:max_results]:
                            video_id = entry.get('id')
                            if video_id:
                                all_videos.append({
                                    'video_id': video_id,
                                    'title': entry.get('title', 'Blippi Video'),
                                    'thumbnail': entry.get('thumbnail', f'https://i.ytimg.com/vi/{video_id}/hqdefault.jpg'),
                                    'url': f'https://www.youtube.com/watch?v={video_id}',
                                    'duration': entry.get('duration', 0),
                                    'uploader': entry.get('uploader', 'Blippi')
                                })
                except Exception as e:
                    logger.warning(f"Failed to fetch from channel {channel_id}: {str(e)}")
                    continue

        response_data = {
            'success': True,
            'videos': all_videos[:max_results],
            'count': len(all_videos[:max_results]),
            'fetched_at': datetime.now().isoformat()
        }

        # Cache the result
        video_cache[cache_key] = (response_data, datetime.now())

        logger.info(f"Successfully fetched {len(all_videos)} Blippi videos")
        return jsonify(response_data)

    except Exception as e:
        logger.error(f"Error fetching Blippi videos: {str(e)}")
        return jsonify({
            'success': False,
            'error': str(e),
            'videos': []
        }), 500

@app.route('/api/playlist', methods=['GET', 'POST'])
def extract_playlist():
    """
    Extract all videos from a YouTube playlist

    Query params:
        playlist_id: YouTube playlist ID (required)
        max_results: Maximum number of videos (default: 50)

    Returns:
        JSON array of video objects
    """
    try:
        playlist_id = request.args.get('playlist_id') or request.json.get('playlist_id')
        max_results = int(request.args.get('max_results', 50))

        if not playlist_id:
            return jsonify({'error': 'playlist_id is required'}), 400

        logger.info(f"Fetching playlist: {playlist_id}")

        url = f'https://www.youtube.com/playlist?list={playlist_id}'

        opts = YDL_OPTS.copy()
        opts['extract_flat'] = True
        opts['playlistend'] = max_results

        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)

            videos = []
            if 'entries' in info:
                for entry in info['entries'][:max_results]:
                    video_id = entry.get('id')
                    if video_id:
                        videos.append({
                            'video_id': video_id,
                            'title': entry.get('title', 'Video'),
                            'thumbnail': entry.get('thumbnail', f'https://i.ytimg.com/vi/{video_id}/hqdefault.jpg'),
                            'url': f'https://www.youtube.com/watch?v={video_id}',
                            'duration': entry.get('duration', 0)
                        })

            return jsonify({
                'success': True,
                'playlist_id': playlist_id,
                'playlist_title': info.get('title', 'Playlist'),
                'videos': videos,
                'count': len(videos),
                'fetched_at': datetime.now().isoformat()
            })

    except Exception as e:
        logger.error(f"Error fetching playlist {playlist_id}: {str(e)}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

@app.route('/api/cache/clear', methods=['POST'])
def clear_cache():
    """Clear the video cache"""
    global video_cache
    cache_size = len(video_cache)
    video_cache.clear()
    logger.info(f"Cache cleared ({cache_size} entries)")
    return jsonify({
        'success': True,
        'message': f'Cleared {cache_size} cache entries'
    })

# ==================== ADMIN PANEL ROUTES ====================

@app.route('/admin')
def admin_panel():
    """Serve the admin panel HTML"""
    try:
        return send_file('admin.html')
    except Exception as e:
        logger.error(f"Error serving admin panel: {e}")
        return jsonify({'error': 'Admin panel not found'}), 404

@app.route('/api/groups', methods=['GET'])
def get_groups():
    """Get all video groups"""
    try:
        data = load_groups()
        return jsonify(data)
    except Exception as e:
        logger.error(f"Error getting groups: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/groups', methods=['POST'])
def create_group():
    """
    Create a new video group

    JSON body:
        name: Group name (required)
        description: Group description (optional)
        video_ids: List of YouTube video IDs (optional)
        playlist_id: YouTube playlist ID (optional)
        channel_id: YouTube channel ID (optional)
        max_results: Max videos to fetch from playlist/channel (optional, default: 50)
    """
    try:
        data = request.json
        name = data.get('name')
        description = data.get('description', '')
        video_ids = data.get('video_ids', [])
        playlist_id = data.get('playlist_id')
        channel_id = data.get('channel_id')
        max_results = data.get('max_results', 50)

        if not name:
            return jsonify({'success': False, 'error': 'Group name is required'}), 400

        # Create new group
        group_id = str(uuid.uuid4())
        videos = []

        # Fetch videos from playlist if provided
        if playlist_id:
            logger.info(f"Fetching videos from playlist: {playlist_id}")
            try:
                url = f'https://www.youtube.com/playlist?list={playlist_id}'
                opts = YDL_OPTS.copy()
                opts['extract_flat'] = True
                opts['playlistend'] = max_results

                with yt_dlp.YoutubeDL(opts) as ydl:
                    info = ydl.extract_info(url, download=False)
                    if 'entries' in info:
                        for entry in info['entries'][:max_results]:
                            vid_id = entry.get('id')
                            if vid_id:
                                videos.append({
                                    'video_id': vid_id,
                                    'title': entry.get('title', 'Unknown Title'),
                                    'thumbnail': entry.get('thumbnail', f'https://i.ytimg.com/vi/{vid_id}/hqdefault.jpg'),
                                    'duration': entry.get('duration', 0),
                                    'uploader': entry.get('uploader', 'Unknown')
                                })
            except Exception as e:
                logger.error(f"Error fetching playlist: {e}")
                return jsonify({'success': False, 'error': f'Failed to fetch playlist: {str(e)}'}), 500

        # Fetch videos from channel if provided
        elif channel_id:
            logger.info(f"Fetching videos from channel: {channel_id}")
            try:
                # Handle both channel IDs and usernames
                if channel_id.startswith('UC'):
                    url = f'https://www.youtube.com/channel/{channel_id}/videos'
                else:
                    url = f'https://www.youtube.com/@{channel_id}/videos'

                opts = YDL_OPTS.copy()
                opts['extract_flat'] = True
                opts['playlistend'] = max_results

                with yt_dlp.YoutubeDL(opts) as ydl:
                    info = ydl.extract_info(url, download=False)
                    if 'entries' in info:
                        for entry in info['entries'][:max_results]:
                            vid_id = entry.get('id')
                            if vid_id:
                                videos.append({
                                    'video_id': vid_id,
                                    'title': entry.get('title', 'Unknown Title'),
                                    'thumbnail': entry.get('thumbnail', f'https://i.ytimg.com/vi/{vid_id}/hqdefault.jpg'),
                                    'duration': entry.get('duration', 0),
                                    'uploader': entry.get('uploader', 'Unknown')
                                })
            except Exception as e:
                logger.error(f"Error fetching channel: {e}")
                return jsonify({'success': False, 'error': f'Failed to fetch channel: {str(e)}'}), 500

        # Fetch info for manually specified video IDs
        else:
            for video_id in video_ids:
                video_id = video_id.strip()
                if video_id:
                    logger.info(f"Fetching info for video: {video_id}")
                    video_info = fetch_video_info(video_id)
                    videos.append(video_info)

        created_at = datetime.now().isoformat()

        # Save to SQLite database
        try:
            with get_db() as conn:
                cursor = conn.cursor()

                # Insert group
                cursor.execute('''
                    INSERT INTO groups (id, name, description, created_at)
                    VALUES (?, ?, ?, ?)
                ''', (group_id, name, description, created_at))

                # Insert videos
                for video in videos:
                    cursor.execute('''
                        INSERT INTO videos (group_id, video_id, title, thumbnail, duration, uploader)
                        VALUES (?, ?, ?, ?, ?, ?)
                    ''', (
                        group_id,
                        video['video_id'],
                        video['title'],
                        video.get('thumbnail', ''),
                        video.get('duration', 0),
                        video.get('uploader', 'Unknown')
                    ))

                conn.commit()

            new_group = {
                'id': group_id,
                'name': name,
                'description': description,
                'videos': videos,
                'created_at': created_at
            }

            logger.info(f"Created group: {name} with {len(videos)} videos")
            return jsonify({
                'success': True,
                'group': new_group
            })
        except Exception as e:
            logger.error(f"Error saving group to database: {e}")
            return jsonify({'success': False, 'error': f'Failed to save group: {str(e)}'}), 500

    except Exception as e:
        logger.error(f"Error creating group: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/groups/<group_id>', methods=['DELETE'])
def delete_group(group_id):
    """Delete a video group"""
    try:
        with get_db() as conn:
            cursor = conn.cursor()

            # Check if group exists
            cursor.execute('SELECT id FROM groups WHERE id = ?', (group_id,))
            if not cursor.fetchone():
                return jsonify({'success': False, 'error': 'Group not found'}), 404

            # Delete group (videos will be deleted automatically via CASCADE)
            cursor.execute('DELETE FROM groups WHERE id = ?', (group_id,))
            conn.commit()

            logger.info(f"Deleted group: {group_id}")
            return jsonify({'success': True})

    except Exception as e:
        logger.error(f"Error deleting group: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/groups/<group_id>/videos', methods=['POST'])
def add_videos_to_group(group_id):
    """
    Add videos to an existing group

    JSON body:
        video_ids: List of YouTube video IDs (optional)
        playlist_id: YouTube playlist ID (optional)
        channel_id: YouTube channel ID (optional)
        max_results: Max videos to fetch from playlist/channel (optional, default: 50)
    """
    try:
        data = request.json
        video_ids = data.get('video_ids', [])
        playlist_id = data.get('playlist_id')
        channel_id = data.get('channel_id')
        max_results = data.get('max_results', 50)

        # Check if group exists
        with get_db() as conn:
            cursor = conn.cursor()
            cursor.execute('SELECT id FROM groups WHERE id = ?', (group_id,))
            if not cursor.fetchone():
                return jsonify({'success': False, 'error': 'Group not found'}), 404

            # Get existing video IDs to avoid duplicates
            cursor.execute('SELECT video_id FROM videos WHERE group_id = ?', (group_id,))
            existing_ids = {row['video_id'] for row in cursor.fetchall()}

        added_count = 0
        new_videos = []

        # Fetch videos from playlist if provided
        if playlist_id:
            logger.info(f"Adding videos from playlist: {playlist_id}")
            try:
                url = f'https://www.youtube.com/playlist?list={playlist_id}'
                opts = YDL_OPTS.copy()
                opts['extract_flat'] = True
                opts['playlistend'] = max_results

                with yt_dlp.YoutubeDL(opts) as ydl:
                    info = ydl.extract_info(url, download=False)
                    if 'entries' in info:
                        for entry in info['entries'][:max_results]:
                            vid_id = entry.get('id')
                            if vid_id and vid_id not in existing_ids:
                                new_videos.append({
                                    'video_id': vid_id,
                                    'title': entry.get('title', 'Unknown Title'),
                                    'thumbnail': entry.get('thumbnail', f'https://i.ytimg.com/vi/{vid_id}/hqdefault.jpg'),
                                    'duration': entry.get('duration', 0),
                                    'uploader': entry.get('uploader', 'Unknown')
                                })
                                existing_ids.add(vid_id)
                                added_count += 1
            except Exception as e:
                logger.error(f"Error fetching playlist: {e}")
                return jsonify({'success': False, 'error': f'Failed to fetch playlist: {str(e)}'}), 500

        # Fetch videos from channel if provided
        elif channel_id:
            logger.info(f"Adding videos from channel: {channel_id}")
            try:
                # Handle both channel IDs and usernames
                if channel_id.startswith('UC'):
                    url = f'https://www.youtube.com/channel/{channel_id}/videos'
                else:
                    url = f'https://www.youtube.com/@{channel_id}/videos'

                opts = YDL_OPTS.copy()
                opts['extract_flat'] = True
                opts['playlistend'] = max_results

                with yt_dlp.YoutubeDL(opts) as ydl:
                    info = ydl.extract_info(url, download=False)
                    if 'entries' in info:
                        for entry in info['entries'][:max_results]:
                            vid_id = entry.get('id')
                            if vid_id and vid_id not in existing_ids:
                                new_videos.append({
                                    'video_id': vid_id,
                                    'title': entry.get('title', 'Unknown Title'),
                                    'thumbnail': entry.get('thumbnail', f'https://i.ytimg.com/vi/{vid_id}/hqdefault.jpg'),
                                    'duration': entry.get('duration', 0),
                                    'uploader': entry.get('uploader', 'Unknown')
                                })
                                existing_ids.add(vid_id)
                                added_count += 1
            except Exception as e:
                logger.error(f"Error fetching channel: {e}")
                return jsonify({'success': False, 'error': f'Failed to fetch channel: {str(e)}'}), 500

        # Add manually specified video IDs
        else:
            if not video_ids:
                return jsonify({'success': False, 'error': 'video_ids, playlist_id, or channel_id is required'}), 400

            for video_id in video_ids:
                video_id = video_id.strip()
                if video_id and video_id not in existing_ids:
                    logger.info(f"Adding video to group: {video_id}")
                    video_info = fetch_video_info(video_id)
                    new_videos.append(video_info)
                    existing_ids.add(video_id)
                    added_count += 1

        # Save new videos to database
        try:
            with get_db() as conn:
                cursor = conn.cursor()
                for video in new_videos:
                    cursor.execute('''
                        INSERT INTO videos (group_id, video_id, title, thumbnail, duration, uploader)
                        VALUES (?, ?, ?, ?, ?, ?)
                    ''', (
                        group_id,
                        video['video_id'],
                        video['title'],
                        video.get('thumbnail', ''),
                        video.get('duration', 0),
                        video.get('uploader', 'Unknown')
                    ))
                conn.commit()

            # Load updated group
            groups_data = load_groups()
            group = None
            for g in groups_data['groups']:
                if g['id'] == group_id:
                    group = g
                    break

            logger.info(f"Added {added_count} videos to group {group_id}")
            return jsonify({
                'success': True,
                'added_count': added_count,
                'group': group
            })
        except Exception as e:
            logger.error(f"Error adding videos to group: {e}")
            return jsonify({'success': False, 'error': f'Failed to save changes: {str(e)}'}), 500

    except Exception as e:
        logger.error(f"Error adding videos to group: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/groups/<group_id>/videos/<video_id>', methods=['DELETE'])
def delete_video_from_group(group_id, video_id):
    """Remove a video from a group"""
    try:
        with get_db() as conn:
            cursor = conn.cursor()

            # Check if video exists in group
            cursor.execute('''
                SELECT id FROM videos WHERE group_id = ? AND video_id = ?
            ''', (group_id, video_id))

            if not cursor.fetchone():
                return jsonify({'success': False, 'error': 'Video not found in group'}), 404

            # Delete the video
            cursor.execute('''
                DELETE FROM videos WHERE group_id = ? AND video_id = ?
            ''', (group_id, video_id))
            conn.commit()

            logger.info(f"Removed video {video_id} from group {group_id}")
            return jsonify({'success': True})

    except Exception as e:
        logger.error(f"Error removing video from group: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

# ==================== SCHEDULES / BREAK TIMES ====================

@app.route('/api/schedules', methods=['GET'])
def get_schedules():
    """Get all schedules"""
    try:
        data = load_schedules()
        return jsonify(data)
    except Exception as e:
        logger.error(f"Error getting schedules: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/schedules', methods=['POST'])
def create_schedule():
    """
    Create a new schedule

    JSON body:
        name: Schedule name (e.g., "Study Time")
        start_time: Start time (HH:MM format, e.g., "14:00")
        end_time: End time (HH:MM format, e.g., "16:00")
        message: Custom message to display
        voice_enabled: Enable voice announcement (boolean)
        voice_repeat: Number of times to repeat voice (integer)
        days: List of days (e.g., ["monday", "tuesday", ...])
    """
    try:
        data = request.json
        name = data.get('name')
        start_time = data.get('start_time')
        end_time = data.get('end_time')
        message = data.get('message', 'Break time')
        voice_enabled = data.get('voice_enabled', True)
        voice_repeat = data.get('voice_repeat', 1)
        days = data.get('days', ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'])

        if not all([name, start_time, end_time]):
            return jsonify({'success': False, 'error': 'name, start_time, and end_time are required'}), 400

        schedule_id = str(uuid.uuid4())
        created_at = datetime.now().isoformat()

        try:
            with get_db() as conn:
                cursor = conn.cursor()
                cursor.execute('''
                    INSERT INTO schedules (id, name, start_time, end_time, message,
                                         voice_enabled, voice_repeat, days, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ''', (
                    schedule_id, name, start_time, end_time, message,
                    int(voice_enabled), voice_repeat, json.dumps(days), created_at
                ))
                conn.commit()

            new_schedule = {
                'id': schedule_id,
                'name': name,
                'start_time': start_time,
                'end_time': end_time,
                'message': message,
                'voice_enabled': voice_enabled,
                'voice_repeat': voice_repeat,
                'days': days,
                'created_at': created_at
            }

            logger.info(f"Created schedule: {name}")
            return jsonify({'success': True, 'schedule': new_schedule})
        except Exception as e:
            logger.error(f"Error saving schedule: {e}")
            return jsonify({'success': False, 'error': f'Failed to save schedule: {str(e)}'}), 500

    except Exception as e:
        logger.error(f"Error creating schedule: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/schedules/<schedule_id>', methods=['DELETE'])
def delete_schedule(schedule_id):
    """Delete a schedule"""
    try:
        with get_db() as conn:
            cursor = conn.cursor()

            # Check if schedule exists
            cursor.execute('SELECT id FROM schedules WHERE id = ?', (schedule_id,))
            if not cursor.fetchone():
                return jsonify({'success': False, 'error': 'Schedule not found'}), 404

            # Delete schedule
            cursor.execute('DELETE FROM schedules WHERE id = ?', (schedule_id,))
            conn.commit()

            logger.info(f"Deleted schedule: {schedule_id}")
            return jsonify({'success': True})

    except Exception as e:
        logger.error(f"Error deleting schedule: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

# ==================== BLOCKED VIDEOS / CHANNELS ====================

@app.route('/api/blocked', methods=['GET'])
def get_blocked():
    """Get all blocked items"""
    try:
        data = load_blocked()
        return jsonify(data)
    except Exception as e:
        logger.error(f"Error getting blocked items: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/blocked/video', methods=['POST'])
def block_video():
    """
    Block a video

    JSON body:
        video_id: YouTube video ID
        reason: Reason for blocking (optional)
    """
    try:
        data = request.json
        video_id = data.get('video_id')
        reason = data.get('reason', 'Blocked by admin')

        if not video_id:
            return jsonify({'success': False, 'error': 'video_id is required'}), 400

        try:
            with get_db() as conn:
                cursor = conn.cursor()

                # Check if already blocked
                cursor.execute('SELECT video_id FROM blocked_videos WHERE video_id = ?', (video_id,))
                if cursor.fetchone():
                    return jsonify({'success': False, 'error': 'Video already blocked'}), 400

                # Block video
                cursor.execute('''
                    INSERT INTO blocked_videos (video_id, reason, blocked_at)
                    VALUES (?, ?, ?)
                ''', (video_id, reason, datetime.now().isoformat()))
                conn.commit()

                logger.info(f"Blocked video: {video_id}")
                return jsonify({'success': True})
        except Exception as e:
            logger.error(f"Error blocking video: {e}")
            return jsonify({'success': False, 'error': f'Failed to block video: {str(e)}'}), 500

    except Exception as e:
        logger.error(f"Error blocking video: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/blocked/video/<video_id>', methods=['DELETE'])
def unblock_video(video_id):
    """Unblock a video"""
    try:
        with get_db() as conn:
            cursor = conn.cursor()

            # Check if video is blocked
            cursor.execute('SELECT video_id FROM blocked_videos WHERE video_id = ?', (video_id,))
            if not cursor.fetchone():
                return jsonify({'success': False, 'error': 'Video not found in blocked list'}), 404

            # Unblock video
            cursor.execute('DELETE FROM blocked_videos WHERE video_id = ?', (video_id,))
            conn.commit()

            logger.info(f"Unblocked video: {video_id}")
            return jsonify({'success': True})

    except Exception as e:
        logger.error(f"Error unblocking video: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/blocked/channel', methods=['POST'])
def block_channel():
    """
    Block a channel

    JSON body:
        channel_id: YouTube channel ID or handle
        reason: Reason for blocking (optional)
    """
    try:
        data = request.json
        channel_id = data.get('channel_id')
        reason = data.get('reason', 'Blocked by admin')

        if not channel_id:
            return jsonify({'success': False, 'error': 'channel_id is required'}), 400

        try:
            with get_db() as conn:
                cursor = conn.cursor()

                # Check if already blocked
                cursor.execute('SELECT channel_id FROM blocked_channels WHERE channel_id = ?', (channel_id,))
                if cursor.fetchone():
                    return jsonify({'success': False, 'error': 'Channel already blocked'}), 400

                # Block channel
                cursor.execute('''
                    INSERT INTO blocked_channels (channel_id, reason, blocked_at)
                    VALUES (?, ?, ?)
                ''', (channel_id, reason, datetime.now().isoformat()))
                conn.commit()

                logger.info(f"Blocked channel: {channel_id}")
                return jsonify({'success': True})
        except Exception as e:
            logger.error(f"Error blocking channel: {e}")
            return jsonify({'success': False, 'error': f'Failed to block channel: {str(e)}'}), 500

    except Exception as e:
        logger.error(f"Error blocking channel: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/blocked/channel/<channel_id>', methods=['DELETE'])
def unblock_channel(channel_id):
    """Unblock a channel"""
    try:
        with get_db() as conn:
            cursor = conn.cursor()

            # Check if channel is blocked
            cursor.execute('SELECT channel_id FROM blocked_channels WHERE channel_id = ?', (channel_id,))
            if not cursor.fetchone():
                return jsonify({'success': False, 'error': 'Channel not found in blocked list'}), 404

            # Unblock channel
            cursor.execute('DELETE FROM blocked_channels WHERE channel_id = ?', (channel_id,))
            conn.commit()

            logger.info(f"Unblocked channel: {channel_id}")
            return jsonify({'success': True})

    except Exception as e:
        logger.error(f"Error unblocking channel: {e}")
        return jsonify({'success': False, 'error': str(e)}'), 500

if __name__ == '__main__':
    # Initialize database
    init_database()

    # Run server
    # For production, use gunicorn or similar WSGI server
    app.run(host='0.0.0.0', port=5000, debug=True)
