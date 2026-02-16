#!/bin/sh
set -eu -o pipefail

### INSTALLATION

# https://github.com/Shopify/toxiproxy
# brew tap shopify/shopify
# brew install toxiproxy

### RUNNING

# ./toxiproxy.sh
# ./toxiproxy.sh 80 8080 100
# toxiproxy-cli list
# killall toxiproxy-server
# toxiproxy-cli toxic help
# toxiproxy-cli toxic update --toxicName hf_latency_toxic --attribute latency=500 hf_dev_proxy

set -x
PORT=${1:-80}
UPSTREAM_PORT=${2:-8080}
LATENCY=${3:-100}
log_level=fatal # even 'error' is too spammy
set +x

LOG_LEVEL=${log_level} toxiproxy-server &
echo "waiting for toxiproxy to be ready:"
while ! nc -z localhost 8474; do # toxiproxy control port
    printf .
    sleep 1
done
toxiproxy-cli create --listen 0.0.0.0:${PORT} --upstream localhost:${UPSTREAM_PORT} hf_dev_proxy && \
toxiproxy-cli toxic add --toxicName hf_latency_toxic --type latency --attribute latency=${LATENCY} hf_dev_proxy