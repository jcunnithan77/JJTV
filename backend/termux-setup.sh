#!/data/data/com.termux/files/usr/bin/bash

# Termux Setup Script for JJTV Backend
# This script installs Python and dependencies, then runs the Flask server

echo "========================================="
echo "JJTV Backend Setup for Android TV"
echo "========================================="

# Update package lists
echo "Updating Termux packages..."
pkg update -y

# Install Python
echo "Installing Python..."
pkg install python -y

# Install pip dependencies
echo "Installing Flask and dependencies..."
pip install flask flask-cors yt-dlp

# Copy backend files from sdcard to Termux home
echo "Copying backend files..."
cp -r /sdcard/jjtv-backend ~/jjtv-backend
cd ~/jjtv-backend

# Make server script executable
chmod +x server.py

echo "========================================="
echo "Setup complete!"
echo "========================================="
echo ""
echo "To start the backend server:"
echo "  cd ~/jjtv-backend"
echo "  python server.py"
echo ""
echo "The server will run on http://127.0.0.1:5000"
echo "Your JJTV app is configured to use this URL"
echo ""
echo "To keep it running in background:"
echo "  python server.py &"
echo ""
echo "========================================="
