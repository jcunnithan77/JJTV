#!/data/data/com.termux/files/usr/bin/bash
# JJTV Backend Control

cd ~/jjtv-backend

case "$1" in
    start)
        echo "Starting backend..."
        nohup python server.py > backend.log 2>&1 &
        echo $! > backend.pid
        echo "Backend started!"
        ;;
    stop)
        if [ -f backend.pid ]; then
            kill $(cat backend.pid)
            rm backend.pid
            echo "Backend stopped!"
        else
            pkill -f "python server.py"
            echo "Backend stopped!"
        fi
        ;;
    status)
        if ps | grep -q "python server.py"; then
            echo "Backend is running"
        else
            echo "Backend is not running"
        fi
        ;;
    logs)
        tail -f backend.log
        ;;
    *)
        echo "Usage: bash bc.sh {start|stop|status|logs}"
        ;;
esac
