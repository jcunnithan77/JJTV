from flask import Flask, jsonify, request
from flask_cors import CORS
import subprocess
import json

app = Flask(__name__)
CORS(app)

@app.route('/')
def home():
    return jsonify({"status": "online", "message": "JJTV Backend Server"})

@app.route('/api/extract', methods=['GET'])
def extract_video():
    video_id = request.args.get('video_id')
    if not video_id:
        return jsonify({"success": False, "error": "No video_id provided"}), 400

    try:
        cmd = ['yt-dlp', '-f', 'best', '-g', f'https://www.youtube.com/watch?v={video_id}']
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)

        if result.returncode == 0 and result.stdout.strip():
            url = result.stdout.strip().split('\n')[0]
            return jsonify({"success": True, "url": url, "title": "Video"})
        else:
            return jsonify({"success": False, "error": "Could not extract video"}), 500

    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500

if __name__ == '__main__':
    print("Backend server started!")
    app.run(host='127.0.0.1', port=5000, debug=False)
