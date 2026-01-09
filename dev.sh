#!/bin/bash

# Development script to run both backend and frontend
# Usage: ./dev.sh
# Press Ctrl+C to stop both services

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend"
FRONTEND_DIR="$SCRIPT_DIR/frontend"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# PIDs for cleanup
BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
    echo ""
    echo -e "${YELLOW}Shutting down...${NC}"

    # Clean up any remaining processes on the ports
    lsof -ti:8080 | xargs kill -9 2>/dev/null || true
    lsof -ti:4200 | xargs kill -9 2>/dev/null || true

    echo -e "${GREEN}Shutdown complete${NC}"
    exit 0
}

# Set up trap for cleanup on Ctrl+C and other signals
trap cleanup SIGINT SIGTERM EXIT

# Check if ports are available
if lsof -ti:8080 >/dev/null 2>&1; then
    echo -e "${RED}Port 8080 is already in use. Killing existing process...${NC}"
    lsof -ti:8080 | xargs kill -9 2>/dev/null || true
    sleep 1
fi

if lsof -ti:4200 >/dev/null 2>&1; then
    echo -e "${RED}Port 4200 is already in use. Killing existing process...${NC}"
    lsof -ti:4200 | xargs kill -9 2>/dev/null || true
    sleep 1
fi

echo -e "${GREEN}Starting Petbook Development Environment${NC}"
echo "================================================"

# Set JAVA_HOME if needed (for macOS with Homebrew)
if [ -d "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
fi

# Start backend (silent, logs to file)
echo -e "${BLUE}Starting backend on http://localhost:8080${NC}"
cd "$BACKEND_DIR"
./gradlew run --no-daemon --console=plain > /tmp/petbook-backend.log 2>&1 &
BACKEND_PID=$!

# Wait for backend to start
echo -e "${YELLOW}Waiting for backend to start...${NC}"
for i in {1..30}; do
    if curl -s http://localhost:8080/api/health >/dev/null 2>&1; then
        echo -e "${GREEN}Backend is ready${NC}"
        break
    fi
    if ! kill -0 $BACKEND_PID 2>/dev/null; then
        echo -e "${RED}Backend failed to start. Check /tmp/petbook-backend.log${NC}"
        tail -20 /tmp/petbook-backend.log
        exit 1
    fi
    sleep 1
done

if ! curl -s http://localhost:8080/api/health >/dev/null 2>&1; then
    echo -e "${RED}Backend failed to start within 30 seconds${NC}"
    tail -20 /tmp/petbook-backend.log
    exit 1
fi

# Start frontend (silent, logs to file)
echo -e "${BLUE}Starting frontend on http://localhost:4200${NC}"
cd "$FRONTEND_DIR"
npm start > /tmp/petbook-frontend.log 2>&1 &
FRONTEND_PID=$!

# Wait for frontend to start
echo -e "${YELLOW}Waiting for frontend to start...${NC}"
for i in {1..60}; do
    if curl -s http://localhost:4200 >/dev/null 2>&1; then
        echo -e "${GREEN}Frontend is ready${NC}"
        break
    fi
    if ! kill -0 $FRONTEND_PID 2>/dev/null; then
        echo -e "${RED}Frontend failed to start. Check /tmp/petbook-frontend.log${NC}"
        tail -20 /tmp/petbook-frontend.log
        exit 1
    fi
    sleep 1
done

if ! curl -s http://localhost:4200 >/dev/null 2>&1; then
    echo -e "${RED}Frontend failed to start within 60 seconds${NC}"
    tail -20 /tmp/petbook-frontend.log
    exit 1
fi

echo ""
echo -e "${GREEN}================================================${NC}"
echo -e "${GREEN}Petbook is running!${NC}"
echo -e "${GREEN}================================================${NC}"
echo ""
echo -e "  Frontend: ${BLUE}http://localhost:4200${NC}"
echo -e "  Backend:  ${BLUE}http://localhost:8080${NC}"
echo ""
echo -e "  Logs: ${YELLOW}/tmp/petbook-backend.log${NC}"
echo -e "        ${YELLOW}/tmp/petbook-frontend.log${NC}"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
echo ""

# Wait indefinitely - cleanup happens via trap
wait
