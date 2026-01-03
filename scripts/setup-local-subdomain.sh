#!/bin/bash
#
# Setup script for local subdomain development environment
#
# This script sets up:
#   1. dnsmasq for *.local.dev DNS resolution
#   2. mkcert for local SSL certificates
#
# After running this script:
#   - https://auth.local.dev → IDP Server
#   - https://app.local.dev  → App View
#
# Usage:
#   ./scripts/setup-local-subdomain.sh
#
# Then start the services:
#   docker-compose up -d
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CERTS_DIR="$PROJECT_ROOT/docker/nginx/certs"

echo "=== Local Subdomain Development Setup ==="
echo ""

# Check OS
if [[ "$OSTYPE" != "darwin"* ]]; then
    echo "This script is designed for macOS."
    echo "For Linux, please install dnsmasq and mkcert manually."
    exit 1
fi

# Check Homebrew
if ! command -v brew &> /dev/null; then
    echo "Homebrew is required. Please install it first:"
    echo "  /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
    exit 1
fi

echo "Step 1: Installing dnsmasq..."
if ! brew list dnsmasq &> /dev/null; then
    brew install dnsmasq
else
    echo "  dnsmasq is already installed"
fi

echo ""
echo "Step 2: Configuring dnsmasq for *.local.dev..."
DNSMASQ_CONF="/opt/homebrew/etc/dnsmasq.conf"
if [[ ! -f "$DNSMASQ_CONF" ]]; then
    DNSMASQ_CONF="/usr/local/etc/dnsmasq.conf"
fi

if ! grep -q "address=/local.dev/127.0.0.1" "$DNSMASQ_CONF" 2>/dev/null; then
    echo "address=/local.dev/127.0.0.1" >> "$DNSMASQ_CONF"
    echo "  Added local.dev → 127.0.0.1"
else
    echo "  local.dev is already configured"
fi

echo ""
echo "Step 3: Setting up macOS resolver..."
sudo mkdir -p /etc/resolver
echo "nameserver 127.0.0.1" | sudo tee /etc/resolver/local.dev > /dev/null
echo "  Created /etc/resolver/local.dev"

echo ""
echo "Step 4: Starting dnsmasq service..."
sudo brew services restart dnsmasq
echo "  dnsmasq service restarted"

echo ""
echo "Step 5: Installing mkcert..."
if ! command -v mkcert &> /dev/null; then
    brew install mkcert
else
    echo "  mkcert is already installed"
fi

echo ""
echo "Step 6: Installing local CA..."
mkcert -install

echo ""
echo "Step 7: Generating SSL certificates for *.local.dev..."
mkdir -p "$CERTS_DIR"
cd "$CERTS_DIR"
mkcert "*.local.dev" local.dev
echo "  Certificates created in $CERTS_DIR"

echo ""
echo "Step 8: Copying mkcert root CA for Java truststore..."
MKCERT_CAROOT=$(mkcert -CAROOT)
cp "$MKCERT_CAROOT/rootCA.pem" "$CERTS_DIR/"
echo "  Root CA copied to $CERTS_DIR/rootCA.pem"

echo ""
echo "Step 9: Verifying DNS resolution..."
sleep 2
if ping -c 1 auth.local.dev &> /dev/null; then
    echo "  ✓ auth.local.dev resolves to 127.0.0.1"
else
    echo "  ✗ DNS resolution failed. Try restarting dnsmasq:"
    echo "    sudo brew services restart dnsmasq"
fi

if ping -c 1 app.local.dev &> /dev/null; then
    echo "  ✓ app.local.dev resolves to 127.0.0.1"
else
    echo "  ✗ DNS resolution failed for app.local.dev"
fi

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Start the subdomain environment:"
echo "  docker-compose up -d"
echo ""
echo "Access:"
echo "  IDP Server: https://auth.local.dev"
echo "  App View:   https://app.local.dev"
echo ""
echo "Tenant session_config example:"
echo '  {
    "session_config": {
      "cookie_domain": "local.dev",
      "cookie_same_site": "Lax",
      "use_secure_cookie": true,
      "timeout_seconds": 3600
    }
  }'
echo ""
