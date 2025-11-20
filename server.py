#!/usr/bin/env python3
"""
YouTube Video Extraction Server for JJUTV (Blippi Kids App)
Uses yt-dlp to extract video streams and serves them via REST API
"""

from flask import Flask, jsonify, request
from flask_cors import CORS
import yt_dlp
import logging
from datetime import datetime, timedelta
from functools import lru_cache
import json
import sqlite3
import os

app = Flask(__name__)
CORS(app)  # Enable CORS for Android app

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Database configuration
DB_PATH = os.path.join(os.path.dirname(__file__), 'channels.db')

def init_db():
    """Initialize SQLite database with channels and video groups tables"""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # Create channels table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS channels (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            channel_id TEXT UNIQUE NOT NULL,
            channel_name TEXT NOT NULL,
            description TEXT,
            thumbnail TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    ''')

    # Create video_groups table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS video_groups (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            group_name TEXT NOT NULL,
            description TEXT,
            thumbnail TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    ''')

    # Create group_videos table (many-to-many relationship)
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS group_videos (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            group_id INTEGER NOT NULL,
            video_id TEXT NOT NULL,
            video_title TEXT NOT NULL,
            video_thumbnail TEXT,
            position INTEGER NOT NULL,
            added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (group_id) REFERENCES video_groups(id) ON DELETE CASCADE,
            UNIQUE(group_id, video_id)
        )
    ''')

    # Insert default Blippi channels if table is empty
    cursor.execute('SELECT COUNT(*) FROM channels')
    if cursor.fetchone()[0] == 0:
        default_channels = [
            ('UC5PYHgAzJ1jQzoyDQjOA1RA', 'Blippi - Educational Videos for Kids', 'Main Blippi channel', ''),
            ('UCqwjm_R3H1F6i8KBYmPh82A', 'Blippi Toys', 'Blippi Toys channel', '')
        ]
        cursor.executemany(
            'INSERT INTO channels (channel_id, channel_name, description, thumbnail) VALUES (?, ?, ?, ?)',
            default_channels
        )
        logger.info('Added default Blippi channels to database')

    conn.commit()
    conn.close()
    logger.info('Database initialized')

# Initialize database on startup
init_db()

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

# Add cookie support for YouTube authentication
# Priority: 1) Environment variable, 2) cookies.txt file, 3) Chrome browser, 4) no cookies
import os
import tempfile

# Check for cookies in environment variable (for Render deployment)
cookies_env = os.environ.get('YOUTUBE_COOKIES')
if cookies_env:
    # Create temporary cookies file from environment variable
    cookies_file = os.path.join(tempfile.gettempdir(), 'yt_cookies.txt')
    with open(cookies_file, 'w') as f:
        f.write(cookies_env)
    YDL_OPTS['cookiefile'] = cookies_file
    logger.info("Using cookies from YOUTUBE_COOKIES environment variable")
else:
    # Check for cookies.txt file
    cookies_file = os.path.join(os.path.dirname(__file__), 'cookies.txt')
    if os.path.exists(cookies_file):
        YDL_OPTS['cookiefile'] = cookies_file
        logger.info(f"Using cookies file: {cookies_file}")
    elif os.path.exists(os.path.expanduser('~/.config/google-chrome')) or os.path.exists(os.path.expanduser('~/AppData/Local/Google/Chrome')):
        YDL_OPTS['cookiesfrombrowser'] = ('chrome',)
        logger.info("Chrome detected - using browser cookies for YouTube authentication")
    else:
        logger.warning("No cookies available - YouTube may block requests with bot detection")

# Cache for 1 hour to reduce YouTube requests
cache_timeout = timedelta(hours=1)
video_cache = {}

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

        # Fetch channels from database
        conn = sqlite3.connect(DB_PATH)
        cursor = conn.cursor()
        cursor.execute('SELECT channel_id FROM channels')
        db_channels = [row[0] for row in cursor.fetchall()]
        conn.close()

        all_videos = []
        channels_to_fetch = db_channels if channel_index is None else [db_channels[int(channel_index)]]

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

# ==================== CHANNEL MANAGEMENT APIs ====================

@app.route('/api/channels', methods=['GET'])
def get_channels():
    """Get all channels from database"""
    try:
        conn = sqlite3.connect(DB_PATH)
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()

        cursor.execute('SELECT * FROM channels ORDER BY created_at DESC')
        rows = cursor.fetchall()

        channels = []
        for row in rows:
            channels.append({
                'id': row['id'],
                'channel_id': row['channel_id'],
                'channel_name': row['channel_name'],
                'description': row['description'],
                'thumbnail': row['thumbnail'],
                'created_at': row['created_at']
            })

        conn.close()

        return jsonify({
            'success': True,
            'channels': channels,
            'count': len(channels)
        })

    except Exception as e:
        logger.error(f"Error fetching channels: {str(e)}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

@app.route('/api/channels', methods=['POST'])
def add_channel():
    """Add a new channel to database"""
    try:
        data = request.get_json()

        if not data:
            return jsonify({
                'success': False,
                'error': 'No data provided'
            }), 400

        channel_id = data.get('channel_id')
        channel_name = data.get('channel_name')

        if not channel_id or not channel_name:
            return jsonify({
                'success': False,
                'error': 'channel_id and channel_name are required'
            }), 400

        description = data.get('description', '')
        thumbnail = data.get('thumbnail', '')

        conn = sqlite3.connect(DB_PATH)
        cursor = conn.cursor()

        try:
            cursor.execute(
                'INSERT INTO channels (channel_id, channel_name, description, thumbnail) VALUES (?, ?, ?, ?)',
                (channel_id, channel_name, description, thumbnail)
            )
            conn.commit()

            new_id = cursor.lastrowid
            conn.close()

            logger.info(f"Added new channel: {channel_name} ({channel_id})")

            return jsonify({
                'success': True,
                'message': 'Channel added successfully',
                'channel': {
                    'id': new_id,
                    'channel_id': channel_id,
                    'channel_name': channel_name,
                    'description': description,
                    'thumbnail': thumbnail
                }
            }), 201

        except sqlite3.IntegrityError:
            conn.close()
            return jsonify({
                'success': False,
                'error': 'Channel already exists'
            }), 409

    except Exception as e:
        logger.error(f"Error adding channel: {str(e)}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

@app.route('/api/channels/<channel_id>', methods=['DELETE'])
def delete_channel(channel_id):
    """Delete a channel from database"""
    try:
        conn = sqlite3.connect(DB_PATH)
        cursor = conn.cursor()

        cursor.execute('DELETE FROM channels WHERE channel_id = ?', (channel_id,))
        rows_affected = cursor.rowcount

        conn.commit()
        conn.close()

        if rows_affected > 0:
            logger.info(f"Deleted channel: {channel_id}")
            return jsonify({
                'success': True,
                'message': 'Channel deleted successfully'
            })
        else:
            return jsonify({
                'success': False,
                'error': 'Channel not found'
            }), 404

    except Exception as e:
        logger.error(f"Error deleting channel: {str(e)}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

# ==================== VIDEO GROUP MANAGEMENT APIs ====================

@app.route('/api/groups', methods=['GET'])
def get_groups():
    """Get all video groups with video counts"""
    try:
        conn = sqlite3.connect(DB_PATH)
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()

        cursor.execute('''
            SELECT
                g.id, g.group_name, g.description, g.thumbnail, g.created_at,
                COUNT(gv.id) as video_count
            FROM video_groups g
            LEFT JOIN group_videos gv ON g.id = gv.group_id
            GROUP BY g.id
            ORDER BY g.created_at DESC
        ''')
        rows = cursor.fetchall()

        groups = []
        for row in rows:
            groups.append({
                'id': row['id'],
                'group_name': row['group_name'],
                'description': row['description'],
                'thumbnail': row['thumbnail'],
                'video_count': row['video_count'],
                'created_at': row['created_at']
            })

        conn.close()

        return jsonify({
            'success': True,
            'groups': groups,
            'count': len(groups)
        })

    except Exception as e:
        logger.error(f"Error fetching groups: {str(e)}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

@app.route('/api/groups', methods=['POST'])
def create_group():
    """Create a new video group"""
    try:
        data = request.get_json()

        if not data:
            return jsonify({
                'success': False,
                'error': 'No data provided'
            }), 400

        group_name = data.get('group_name')

        if not group_name:
            return jsonify({
                'success': False,
                'error': 'group_name is required'
            }), 400

        description = data.get('description', '')

        conn = sqlite3.connect(DB_PATH)
        cursor = conn.cursor()

        cursor.execute(
            'INSERT INTO video_groups (group_name, description, thumbnail) VALUES (?, ?, ?)',
            (group_name, description, '')
        )
        conn.commit()

        new_id = cursor.lastrowid
        conn.close()

        logger.info(f"Created new group: {group_name} (ID: {new_id})")

        return jsonify({
            'success': True,
            'message': 'Group created successfully',
            'group': {
                'id': new_id,
                'group_name': group_name,
                'description': description,
                'thumbnail': '',
                'video_count': 0
            }
        }), 201

    except Exception as e:
        logger.error(f"Error creating group: {str(e)}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

@app.route('/api/groups/<int:group_id>/videos', methods=['GET'])
def get_group_videos(group_id):
    """Get all videos in a group"""
    try:
        conn = sqlite3.connect(DB_PATH)
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()

        # Get group info
        cursor.execute('SELECT * FROM video_groups WHERE id = ?', (group_id,))
        group = cursor.fetchone()

        if not group:
            conn.close()
            return jsonify({
                'success': False,
                'error': 'Group not found'
            }), 404

        # Get videos
        cursor.execute('''
            SELECT * FROM group_videos
            WHERE group_id = ?
            ORDER BY position ASC
        ''', (group_id,))
        video_rows = cursor.fetchall()

        videos = []
        for row in video_rows:
            videos.append({
                'id': row['id'],
                'video_id': row['video_id'],
                'video_title': row['video_title'],
                'video_thumbnail': row['video_thumbnail'],
                'position': row['position'],
                'added_at': row['added_at']
            })

        conn.close()

        return jsonify({
            'success': True,
            'group': {
                'id': group['id'],
                'group_name': group['group_name'],
                'description': group['description'],
                'thumbnail': group['thumbnail']
            },
            'videos': videos,
            'count': len(videos)
        })

    except Exception as e:
        logger.error(f"Error fetching group videos: {str(e)}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

@app.route('/api/groups/<int:group_id>/videos', methods=['POST'])
def add_video_to_group(group_id):
    """Add a video to a group"""
    try:
        data = request.get_json()

        if not data:
            return jsonify({
                'success': False,
                'error': 'No data provided'
            }), 400

        video_id = data.get('video_id')
        video_title = data.get('video_title')

        if not video_id or not video_title:
            return jsonify({
                'success': False,
                'error': 'video_id and video_title are required'
            }), 400

        video_thumbnail = data.get('video_thumbnail', f'https://i.ytimg.com/vi/{video_id}/hqdefault.jpg')

        conn = sqlite3.connect(DB_PATH)
        cursor = conn.cursor()

        # Check if group exists
        cursor.execute('SELECT id FROM video_groups WHERE id = ?', (group_id,))
        if not cursor.fetchone():
            conn.close()
            return jsonify({
                'success': False,
                'error': 'Group not found'
            }), 404

        # Get next position
        cursor.execute('SELECT MAX(position) FROM group_videos WHERE group_id = ?', (group_id,))
        max_pos = cursor.fetchone()[0]
        next_position = (max_pos + 1) if max_pos is not None else 1

        try:
            # Insert video
            cursor.execute('''
                INSERT INTO group_videos (group_id, video_id, video_title, video_thumbnail, position)
                VALUES (?, ?, ?, ?, ?)
            ''', (group_id, video_id, video_title, video_thumbnail, next_position))

            # Update group thumbnail to first video if not set
            if next_position == 1:
                cursor.execute(
                    'UPDATE video_groups SET thumbnail = ? WHERE id = ?',
                    (video_thumbnail, group_id)
                )

            conn.commit()
            new_id = cursor.lastrowid
            conn.close()

            logger.info(f"Added video {video_id} to group {group_id}")

            return jsonify({
                'success': True,
                'message': 'Video added to group successfully',
                'video': {
                    'id': new_id,
                    'video_id': video_id,
                    'video_title': video_title,
                    'video_thumbnail': video_thumbnail,
                    'position': next_position
                }
            }), 201

        except sqlite3.IntegrityError:
            conn.close()
            return jsonify({
                'success': False,
                'error': 'Video already exists in this group'
            }), 409

    except Exception as e:
        logger.error(f"Error adding video to group: {str(e)}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

@app.route('/api/groups/<int:group_id>', methods=['DELETE'])
def delete_group(group_id):
    """Delete a group and all its videos"""
    try:
        conn = sqlite3.connect(DB_PATH)
        cursor = conn.cursor()

        cursor.execute('DELETE FROM video_groups WHERE id = ?', (group_id,))
        rows_affected = cursor.rowcount

        conn.commit()
        conn.close()

        if rows_affected > 0:
            logger.info(f"Deleted group: {group_id}")
            return jsonify({
                'success': True,
                'message': 'Group deleted successfully'
            })
        else:
            return jsonify({
                'success': False,
                'error': 'Group not found'
            }), 404

    except Exception as e:
        logger.error(f"Error deleting group: {str(e)}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

# ===================================
# Admin Panel
# ===================================

@app.route('/admin')
def admin_panel():
    """Serve the admin panel UI"""
    try:
        admin_file = os.path.join(os.path.dirname(__file__), 'admin.html')
        with open(admin_file, 'r', encoding='utf-8') as f:
            return f.read()
    except Exception as e:
        return f"Error loading admin panel: {str(e)}", 500

@app.route('/api/groups/<int:group_id>/videos/<int:video_id>', methods=['DELETE'])
def delete_video_from_group(group_id, video_id):
    """Delete a specific video from a group"""
    try:
        conn = sqlite3.connect(DB_PATH)
        cursor = conn.cursor()

        # Delete the video
        cursor.execute('DELETE FROM group_videos WHERE group_id = ? AND id = ?', (group_id, video_id))
        rows_affected = cursor.rowcount

        # If this was the thumbnail video, update group thumbnail to next video or empty
        cursor.execute('''
            SELECT video_thumbnail FROM group_videos
            WHERE group_id = ?
            ORDER BY position ASC
            LIMIT 1
        ''', (group_id,))

        result = cursor.fetchone()
        new_thumbnail = result[0] if result else ''

        cursor.execute('UPDATE video_groups SET thumbnail = ? WHERE id = ?', (new_thumbnail, group_id))

        conn.commit()
        conn.close()

        if rows_affected > 0:
            logger.info(f"Deleted video {video_id} from group {group_id}")
            return jsonify({
                'success': True,
                'message': 'Video deleted successfully'
            })
        else:
            return jsonify({
                'success': False,
                'error': 'Video not found'
            }), 404

    except Exception as e:
        logger.error(f"Error deleting video: {str(e)}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

if __name__ == '__main__':
    # Run server
    # For production, use gunicorn or similar WSGI server
    app.run(host='0.0.0.0', port=5000, debug=True)
