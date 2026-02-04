#!/usr/bin/env bash
set -e

# Test script for starter-app jetty 10+ and jetty 9 setups
# Runs each server sequentially, opens browser for manual verification.
#
# Usage: 
#   ./test-jetty-setups.sh [OPTIONS] [-- EXTRA_DEPS_ALIASES...]
#
# Options:
#   --no-browser    Skip opening browsers (just check HTTP headers)
#   --help          Show this help
#
# Examples:
#   ./test-jetty-setups.sh                      # Run with default aliases (dev)
#   ./test-jetty-setups.sh -- :private          # Add :private alias
#   ./test-jetty-setups.sh -- :private :foo     # Add multiple aliases
#   ./test-jetty-setups.sh --no-browser         # Automated mode, no browser

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

OPEN_BROWSER=true
EXTRA_ALIASES=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --no-browser)
            OPEN_BROWSER=false
            shift
            ;;
        --help)
            head -20 "$0" | tail -n +2 | sed 's/^# //' | sed 's/^#//'
            exit 0
            ;;
        --)
            shift
            EXTRA_ALIASES="$*"
            break
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage"
            exit 1
            ;;
    esac
done

USER_CLJ="src-dev/user.clj"
PORT=8080

SERVER_PID=""

cleanup() {
    echo ""
    echo "Cleaning up..."
    
    # Kill server
    if [[ -n "$SERVER_PID" ]]; then
        kill $SERVER_PID 2>/dev/null || true
        wait $SERVER_PID 2>/dev/null || true
    fi
    
    # Restore user.clj
    if [[ -f "${USER_CLJ}.orig" ]]; then
        mv "${USER_CLJ}.orig" "$USER_CLJ"
    fi
    
    echo "Done."
}
trap cleanup EXIT

wait_for_server() {
    local max_attempts=60
    local attempt=0
    
    echo "Waiting for server on port $PORT..."
    while ! curl -s -o /dev/null -w "%{http_code}" http://localhost:$PORT 2>/dev/null | grep -q "200"; do
        sleep 1
        attempt=$((attempt + 1))
        if [[ $attempt -ge $max_attempts ]]; then
            echo "FAIL: Server did not start within ${max_attempts}s"
            return 1
        fi
        if (( attempt % 10 == 0 )); then
            echo "  Still waiting... (${attempt}s)"
        fi
    done
    echo "Server is up!"
}

check_jetty_version() {
    local expected_pattern="$1"
    
    local server_header=$(curl -sI http://localhost:$PORT 2>/dev/null | grep -i "^Server:" | tr -d '\r')
    echo "  $server_header"
    
    if echo "$server_header" | grep -q "$expected_pattern"; then
        echo "  Header check: PASS ✓"
        return 0
    else
        echo "  Header check: FAIL ✗ (expected $expected_pattern)"
        return 1
    fi
}

stop_server() {
    if [[ -n "$SERVER_PID" ]]; then
        echo "Stopping server..."
        kill $SERVER_PID 2>/dev/null || true
        wait $SERVER_PID 2>/dev/null || true
        SERVER_PID=""
        sleep 2  # Give port time to be released
    fi
}

# Build aliases string
BASE_ALIASES="dev"
if [[ -n "$EXTRA_ALIASES" ]]; then
    for alias in $EXTRA_ALIASES; do
        alias="${alias#:}"  # Strip leading colon if present
        BASE_ALIASES="${BASE_ALIASES}:${alias}"
    done
fi

echo "========================================"
echo "Starter App Jetty Setup Tests"
echo "========================================"
echo "Aliases: $BASE_ALIASES"
echo "Browser: $OPEN_BROWSER"
echo ""

# Restore from previous interrupted run if needed, then backup
if [[ -f "${USER_CLJ}.orig" ]]; then
    echo "Restoring user.clj from previous interrupted run..."
    mv "${USER_CLJ}.orig" "$USER_CLJ"
fi
cp "$USER_CLJ" "${USER_CLJ}.orig"

JETTY10_RESULT=0
JETTY9_RESULT=0

# ===========================================
# Test 1: Jetty 10+
# ===========================================
echo "========================================"
echo "Test 1: Jetty 10+ (default)"
echo "========================================"

cat > "$USER_CLJ" << 'USERCLJ'
(ns user)
(print "[user] loading dev... ") (flush)
(require 'dev)
(println "Ready.")
USERCLJ

echo "Starting server with: clj -A:$BASE_ALIASES -X dev/-main"
clj -A:$BASE_ALIASES -X dev/-main &
SERVER_PID=$!

wait_for_server || { echo "Jetty 10+ failed to start"; exit 1; }

check_jetty_version "Jetty(1[0-9]" || JETTY10_RESULT=1

if $OPEN_BROWSER; then
    echo ""
    echo "Opening browser: http://localhost:$PORT"
    open "http://localhost:$PORT" 2>/dev/null || xdg-open "http://localhost:$PORT" 2>/dev/null || true
    echo ""
    echo "Verify the clock is ticking, then press Enter to continue to Jetty 9 test..."
    read -r
fi

stop_server

# ===========================================
# Test 2: Jetty 9
# ===========================================
echo ""
echo "========================================"
echo "Test 2: Jetty 9"
echo "========================================"

cat > "$USER_CLJ" << 'USERCLJ'
(ns user)
(print "[user] loading dev... ") (flush)
(require '[dev-jetty9 :as dev])
(println "Ready.")
USERCLJ

echo "Starting server with: clj -A:${BASE_ALIASES}:jetty9 -X dev/-main"
clj -A:${BASE_ALIASES}:jetty9 -X dev/-main &
SERVER_PID=$!

wait_for_server || { echo "Jetty 9 failed to start"; exit 1; }

check_jetty_version "Jetty(9" || JETTY9_RESULT=1

if $OPEN_BROWSER; then
    echo ""
    echo "Opening browser: http://localhost:$PORT"
    open "http://localhost:$PORT" 2>/dev/null || xdg-open "http://localhost:$PORT" 2>/dev/null || true
    echo ""
    echo "Verify the clock is ticking, then press Enter to finish..."
    read -r
fi

stop_server

# ===========================================
# Summary
# ===========================================
echo ""
echo "========================================"
echo "TEST SUMMARY"
echo "========================================"
echo "Jetty 10+: $([ $JETTY10_RESULT -eq 0 ] && echo 'PASS ✓' || echo 'FAIL ✗')"
echo "Jetty 9:   $([ $JETTY9_RESULT -eq 0 ] && echo 'PASS ✓' || echo 'FAIL ✗')"

exit $((JETTY10_RESULT + JETTY9_RESULT))
