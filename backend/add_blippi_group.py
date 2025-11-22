#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script to add Blippi channel as a video group
"""
import requests
import json
import sys

# Fix encoding for Windows console
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

BACKEND_URL = "http://192.168.1.5:5000"

def create_blippi_group():
    """Create Blippi video group from channel"""

    # Blippi main channel handle
    channel_id = "Blippi"  # Using @Blippi handle

    data = {
        "name": "Blippi - Educational Videos for Kids",
        "description": "Fun and educational videos featuring Blippi exploring the world",
        "channel_id": channel_id,
        "max_results": 50
    }

    print(f"Creating Blippi group from channel: {channel_id}")
    print("This may take a moment...")

    try:
        response = requests.post(
            f"{BACKEND_URL}/api/groups",
            json=data,
            timeout=120
        )

        result = response.json()

        if result.get('success'):
            group = result.get('group', {})
            video_count = len(group.get('videos', []))
            print(f"[SUCCESS] Created Blippi group with {video_count} videos")
            print(f"  Group ID: {group.get('id')}")
            print(f"  Group Name: {group.get('name')}")
            return True
        else:
            error = result.get('error', 'Unknown error')
            print(f"[FAILED] Failed to create group: {error}")
            return False

    except requests.exceptions.ConnectionError:
        print("[ERROR] Cannot connect to backend server")
        print(f"  Make sure the server is running at {BACKEND_URL}")
        return False
    except Exception as e:
        print(f"[ERROR] {str(e)}")
        return False

if __name__ == "__main__":
    print("=" * 60)
    print("Adding Blippi Channel to JJUTV")
    print("=" * 60)
    create_blippi_group()
    print("=" * 60)
