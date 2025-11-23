#!/data/data/com.termux/files/usr/bin/bash
# Setup SSH Server in Termux

echo "================================================"
echo "  JJTV - Termux SSH Setup"
echo "================================================"
echo ""

# Install OpenSSH
echo "[1/4] Installing OpenSSH..."
pkg install openssh -y

# Set password for SSH
echo ""
echo "[2/4] Setting password for SSH access..."
echo "Please enter a password for SSH:"
passwd

# Start SSH server
echo ""
echo "[3/4] Starting SSH server..."
sshd

# Get IP address
echo ""
echo "[4/4] Getting connection info..."
IP=$(ifconfig wlan0 2>/dev/null | grep 'inet ' | awk '{print $2}')
PORT=8022

echo ""
echo "================================================"
echo "  SSH Server Started Successfully!"
echo "================================================"
echo ""
echo "Connection Details:"
echo "  IP Address: $IP"
echo "  Port: $PORT"
echo "  Username: $(whoami)"
echo ""
echo "From your PC, connect using:"
echo "  ssh -p $PORT $(whoami)@$IP"
echo ""
echo "Or use PuTTY:"
echo "  Host: $IP"
echo "  Port: $PORT"
echo "  Username: $(whoami)"
echo ""
echo "To start SSH on boot, add this to ~/.bashrc:"
echo "  sshd"
echo ""
echo "================================================"
