#!/data/data/com.termux/files/usr/bin/bash
cd ~/jjtv-backend
nohup python server.py > backend.log 2>&1 &
echo "Backend server started. Check ~/jjtv-backend/backend.log for output."
