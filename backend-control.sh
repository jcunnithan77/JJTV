#!/data/data/com.termux/files/usr/bin/bash
# JJTV Backend Control Script

BACKEND_DIR=~/jjtv-backend
LOG_FILE=$BACKEND_DIR/backend.log
PID_FILE=$BACKEND_DIR/backend.pid

case "$1" in
    start)
        echo "Starting JJTV Backend..."
        cd $BACKEND_DIR
        nohup python server.py > $LOG_FILE 2>&1 &
        echo $! > $PID_FILE
        echo "Backend started! PID: $(cat $PID_FILE)"
        echo "View logs: tail -f $LOG_FILE"
        ;;

    stop)
        if [ -f $PID_FILE ]; then
            PID=$(cat $PID_FILE)
            echo "Stopping backend (PID: $PID)..."
            kill $PID
            rm $PID_FILE
            echo "Backend stopped!"
        else
            echo "Backend is not running (no PID file found)"
            pkill -f "python server.py"
        fi
        ;;

    restart)
        $0 stop
        sleep 2
        $0 start
        ;;

    status)
        if [ -f $PID_FILE ]; then
            PID=$(cat $PID_FILE)
            if ps -p $PID > /dev/null; then
                echo "✅ Backend is running (PID: $PID)"
                echo "Log file: $LOG_FILE"
            else
                echo "❌ Backend PID file exists but process is not running"
                rm $PID_FILE
            fi
        else
            if ps | grep -q "python server.py"; then
                echo "⚠️  Backend is running but no PID file found"
            else
                echo "❌ Backend is not running"
            fi
        fi
        ;;

    logs)
        if [ -f $LOG_FILE ]; then
            tail -f $LOG_FILE
        else
            echo "No log file found at $LOG_FILE"
        fi
        ;;

    *)
        echo "Usage: $0 {start|stop|restart|status|logs}"
        echo ""
        echo "Commands:"
        echo "  start   - Start the backend server"
        echo "  stop    - Stop the backend server"
        echo "  restart - Restart the backend server"
        echo "  status  - Check if backend is running"
        echo "  logs    - View live backend logs"
        exit 1
        ;;
esac
