#!/data/data/com.termux/files/usr/bin/bash
# Setup script to enable JJUTV backend auto-start on boot

echo "=========================================="
echo "JJUTV Backend Auto-Start Setup"
echo "=========================================="
echo ""

# Check if backend files exist
if [ ! -d ~/jjtv-backend ]; then
    echo "[ERROR] Backend directory not found at ~/jjtv-backend"
    echo "Please copy files first:"
    echo "  cp -r /sdcard/jjtv-backend ~/jjtv-backend"
    exit 1
fi

echo "[1/4] Creating Termux boot directory..."
mkdir -p ~/.termux/boot
echo "  ✓ Directory created"

echo ""
echo "[2/4] Installing boot script..."
cat > ~/.termux/boot/start-jjtv-backend << 'EOF'
#!/data/data/com.termux/files/usr/bin/sh
# Auto-start JJUTV backend server on boot

# Wait for system to fully boot
sleep 10

# Start the backend server in background
cd ~/jjtv-backend
python server.py > ~/jjtv-backend.log 2>&1 &

# Log the startup
echo "JJUTV Backend started at $(date)" >> ~/jjtv-backend-startup.log
EOF

chmod +x ~/.termux/boot/start-jjtv-backend
echo "  ✓ Boot script installed"

echo ""
echo "[3/4] Creating manual start/stop scripts..."

# Create start script
cat > ~/start-backend.sh << 'EOF'
#!/data/data/com.termux/files/usr/bin/bash
cd ~/jjtv-backend
python server.py
EOF
chmod +x ~/start-backend.sh

# Create stop script
cat > ~/stop-backend.sh << 'EOF'
#!/data/data/com.termux/files/usr/bin/bash
pkill -f "python server.py"
echo "Backend stopped"
EOF
chmod +x ~/stop-backend.sh

# Create status script
cat > ~/check-backend.sh << 'EOF'
#!/data/data/com.termux/files/usr/bin/bash
if pgrep -f "python server.py" > /dev/null; then
    echo "✓ Backend is RUNNING"
    echo ""
    echo "Logs:"
    tail -20 ~/jjtv-backend.log
else
    echo "✗ Backend is NOT running"
fi
EOF
chmod +x ~/check-backend.sh

echo "  ✓ Helper scripts created:"
echo "    ~/start-backend.sh   - Start backend manually"
echo "    ~/stop-backend.sh    - Stop backend"
echo "    ~/check-backend.sh   - Check if backend is running"

echo ""
echo "[4/4] Testing backend..."
if [ -f ~/jjtv-backend/server.py ]; then
    echo "  ✓ Backend server found"
else
    echo "  ✗ server.py not found!"
    exit 1
fi

echo ""
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "What happens now:"
echo "  1. Backend will auto-start when TV boots"
echo "  2. Wait ~10 seconds after boot for server to start"
echo "  3. Check logs: ~/jjtv-backend-startup.log"
echo ""
echo "Manual controls:"
echo "  Start:  ~/start-backend.sh"
echo "  Stop:   ~/stop-backend.sh"
echo "  Status: ~/check-backend.sh"
echo ""
echo "Note: For auto-start to work, you need Termux:Boot app"
echo "      installed. Boot scripts run automatically when"
echo "      Termux:Boot is installed and boot permission granted."
echo ""
echo "To test now, restart your Android TV!"
echo "=========================================="
