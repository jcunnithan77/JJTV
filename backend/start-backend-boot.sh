#!/data/data/com.termux/files/usr/bin/sh
# Auto-start JJUTV backend server on boot

# Wait a few seconds for system to fully boot
sleep 10

# Start the backend server in background
cd ~/jjtv-backend
python server.py > ~/jjtv-backend.log 2>&1 &

# Log the startup
echo "JJUTV Backend started at $(date)" >> ~/jjtv-backend-startup.log
