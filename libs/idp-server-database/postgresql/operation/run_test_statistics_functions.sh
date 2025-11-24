#!/bin/bash
# =====================================================
# DDL関数の動作確認スクリプト実行
# =====================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SQL_FILE="$SCRIPT_DIR/test_statistics_functions.sql"

echo "========================================="
echo "Statistics Functions Test Runner"
echo "========================================="
echo ""

# 実行環境チェック
if command -v docker &> /dev/null && docker ps --filter "name=postgres-primary" --format "{{.Names}}" | grep -q postgres-primary; then
    echo "✓ Docker environment detected"
    echo "Executing via Docker..."
    echo ""
    docker exec -i postgres-primary psql -U idp_app_user -d idpserver < "$SQL_FILE"
elif command -v psql &> /dev/null; then
    echo "✓ Local PostgreSQL environment detected"
    echo "Executing via psql..."
    echo ""
    psql -U idp_app_user -d idpserver -f "$SQL_FILE"
else
    echo "❌ Error: Neither Docker nor psql command found"
    echo "Please ensure PostgreSQL is running and accessible"
    exit 1
fi

echo ""
echo "========================================="
echo "Test execution completed"
echo "========================================="
