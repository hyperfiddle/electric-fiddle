#!/usr/bin/env bash
set -e

# Test script for wrap-reject-stale-client functionality
# Tests that client version mismatch triggers rejection and reload
# Runs on both Jetty 10+ and Jetty 9 sequentially
#
# Usage: ./test-reject-stale-client.sh [-- EXTRA_DEPS_ALIASES...]
#
# Examples:
#   ./test-reject-stale-client.sh                # Run with default aliases
#   ./test-reject-stale-client.sh -- :private    # Add :private alias

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

EXTRA_ALIASES=""
while [[ $# -gt 0 ]]; do
    case $1 in
        --)
            shift
            EXTRA_ALIASES="$*"
            break
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Build aliases – electric-fiddle needs fiddle-specific aliases
BUILD_ALIASES="build:prod:electric-tutorial"
RUN_ALIASES_JETTY10="prod:electric-tutorial"
RUN_ALIASES_JETTY9="prod:jetty9:electric-tutorial"
if [[ -n "$EXTRA_ALIASES" ]]; then
    for alias in $EXTRA_ALIASES; do
        alias="${alias#:}"
        BUILD_ALIASES="${BUILD_ALIASES}:${alias}"
        RUN_ALIASES_JETTY10="${RUN_ALIASES_JETTY10}:${alias}"
        RUN_ALIASES_JETTY9="${RUN_ALIASES_JETTY9}:${alias}"
    done
fi

FIDDLE_NS="docs-site.sitemap"

PORT=8080
SERVER_PID=""
VERSION=1

cleanup() {
    echo ""
    echo "Cleaning up..."
    if [[ -n "$SERVER_PID" ]]; then
        kill $SERVER_PID 2>/dev/null || true
        wait $SERVER_PID 2>/dev/null || true
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

build_client() {
    local version="$1"
    echo ""
    echo "Building client with version: $version"
    clj -X:$BUILD_ALIASES build-client :build/fiddle-ns $FIDDLE_NS :version "\"$version\""
    echo "Client built with version $version"
}

start_server() {
    local jetty_version="$1"
    local run_aliases="$2"
    local module="$3"
    
    echo ""
    echo "Starting server ($jetty_version) - version from manifest"
    echo "Command: clj -M:$run_aliases -m $module"
    clj -M:$run_aliases -m $module &
    SERVER_PID=$!
    wait_for_server
}

stop_server() {
    if [[ -n "$SERVER_PID" ]]; then
        echo "Stopping server..."
        kill $SERVER_PID 2>/dev/null || true
        wait $SERVER_PID 2>/dev/null || true
        SERVER_PID=""
        sleep 2
    fi
}

run_jetty_test() {
    local jetty_name="$1"
    local run_aliases="$2"
    local module="$3"
    local old_version="$4"
    local new_version="$5"
    
    echo ""
    echo "========================================"
    echo "TEST: $jetty_name - Version $old_version -> $new_version"
    echo "========================================"
    
    # Step 1: Build and start with old version
    build_client "$old_version"
    start_server "$jetty_name" "$run_aliases" "$module"
    
    echo ""
    echo "Opening browser with version $old_version client..."
    open "http://localhost:$PORT" 2>/dev/null || xdg-open "http://localhost:$PORT" 2>/dev/null || true
    
    echo ""
    echo "Browser opened. Keep it open and watch for reload."
    echo "NOTE: Open DevTools and make sure 'Disable cache' is UNCHECKED."
    echo ""
    echo "Press Enter when ready to deploy new version..."
    read -r
    
    # Step 2: Build new client version
    build_client "$new_version"
    
    # Step 3: Restart server (will use new version from manifest)
    stop_server
    echo ""
    echo "Server stopped. Starting with new version..."
    start_server "$jetty_name" "$run_aliases" "$module"
    
    echo ""
    echo "========================================"
    echo "Server restarted with version $new_version"
    echo "========================================"
    echo ""
    echo "Watch the browser - it should:"
    echo "  1. Show connection rejection in console (code 1008)"
    echo "  2. Automatically reload the page"
    echo ""
    
    while true; do
        echo "Did you see the rejection and reload? (y/n/r)"
        echo "  y = Yes, test passed"
        echo "  n = No, test failed"
        echo "  r = Retry with next version bump"
        read -r response
        
        case "$response" in
            y|Y)
                echo "$jetty_name: PASS ✓"
                return 0
                ;;
            n|N)
                echo "$jetty_name: FAIL ✗"
                return 1
                ;;
            r|R)
                # Bump version and retry
                old_version=$new_version
                new_version=$((new_version + 1))
                VERSION=$new_version
                
                echo "Retrying with version $old_version -> $new_version..."
                stop_server
                
                build_client "$old_version"
                start_server "$jetty_name" "$run_aliases" "$module"
                
                echo ""
                echo "Press Enter when ready to deploy new version..."
                read -r
                
                build_client "$new_version"
                stop_server
                start_server "$jetty_name" "$run_aliases" "$module"
                
                echo ""
                echo "Server restarted with version $new_version"
                echo "Watch the browser for rejection and reload."
                echo ""
                ;;
            *)
                echo "Invalid response, please enter y/n/r"
                ;;
        esac
    done
}

# Main
echo "========================================"
echo "wrap-reject-stale-client Test"
echo "========================================"
echo "Tests that stale clients are rejected and reload."
echo "Runs on both Jetty 10+ and Jetty 9."
echo ""

JETTY10_RESULT=0
JETTY9_RESULT=0

# Test Jetty 10+
OLD_VERSION=$VERSION
NEW_VERSION=$((VERSION + 1))
run_jetty_test "Jetty 10+" "$RUN_ALIASES_JETTY10" "prod" "$OLD_VERSION" "$NEW_VERSION" || JETTY10_RESULT=1
VERSION=$NEW_VERSION
stop_server

# Test Jetty 9 (reuse client, just bump version)
OLD_VERSION=$((VERSION + 1))
NEW_VERSION=$((VERSION + 2))
VERSION=$NEW_VERSION
run_jetty_test "Jetty 9" "$RUN_ALIASES_JETTY9" "prod-jetty9" "$OLD_VERSION" "$NEW_VERSION" || JETTY9_RESULT=1
stop_server

# Summary
echo ""
echo "========================================"
echo "TEST SUMMARY"
echo "========================================"
echo "Jetty 10+: $([ $JETTY10_RESULT -eq 0 ] && echo 'PASS ✓' || echo 'FAIL ✗')"
echo "Jetty 9:   $([ $JETTY9_RESULT -eq 0 ] && echo 'PASS ✓' || echo 'FAIL ✗')"

exit $((JETTY10_RESULT + JETTY9_RESULT))
