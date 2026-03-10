#!/bin/bash
#
# Cleanup script for local.dev → local.test migration
#
# This script removes old local.dev configuration that is no longer needed
# after migrating to local.test TLD.
#
# What it does:
#   1. Removes /etc/resolver/local.dev
#   2. Removes dnsmasq address=/local.dev/ entry
#   3. Removes old _wildcard.local.dev+1*.pem certificate files
#
# Usage:
#   ./scripts/cleanup-local-dev.sh
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CERTS_DIR="$PROJECT_ROOT/docker/nginx/certs"

echo "=== local.dev Cleanup ==="
echo ""

# Step 1: Remove resolver
if [[ -f /etc/resolver/local.dev ]]; then
    echo "Step 1: Removing /etc/resolver/local.dev..."
    sudo rm /etc/resolver/local.dev
    echo "  Removed"
else
    echo "Step 1: /etc/resolver/local.dev does not exist (already clean)"
fi

# Step 2: Remove dnsmasq entry
echo ""
echo "Step 2: Removing dnsmasq local.dev entry..."
DNSMASQ_CONF="/opt/homebrew/etc/dnsmasq.conf"
if [[ ! -f "$DNSMASQ_CONF" ]]; then
    DNSMASQ_CONF="/usr/local/etc/dnsmasq.conf"
fi

if grep -q "address=/local.dev/" "$DNSMASQ_CONF" 2>/dev/null; then
    sed -i '' '/address=\/local\.dev\//d' "$DNSMASQ_CONF"
    echo "  Removed address=/local.dev/ from $DNSMASQ_CONF"
    sudo brew services restart dnsmasq
    echo "  dnsmasq restarted"
else
    echo "  No local.dev entry found in dnsmasq.conf (already clean)"
fi

# Step 3: Remove old certificate files
echo ""
echo "Step 3: Removing old local.dev certificate files..."
REMOVED=0
for f in "$CERTS_DIR"/_wildcard.local.dev+1*.pem; do
    if [[ -f "$f" ]]; then
        rm "$f"
        echo "  Removed $(basename "$f")"
        REMOVED=$((REMOVED + 1))
    fi
done
if [[ $REMOVED -eq 0 ]]; then
    echo "  No old certificate files found (already clean)"
fi

echo ""
echo "=== Cleanup Complete ==="
echo ""
echo "If you haven't already, run the new setup:"
echo "  ./scripts/setup-local-subdomain.sh"
echo ""
