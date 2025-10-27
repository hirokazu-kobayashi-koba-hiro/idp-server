#!/bin/bash
# scripts/release.sh
# Unified release script for idp-server
# Usage: ./scripts/release.sh <version>
# Example: ./scripts/release.sh 0.9.0

set -e

VERSION=$1
if [ -z "$VERSION" ]; then
  echo "使用方法: $0 <version>"
  echo "例: $0 0.9.0"
  exit 1
fi

# バージョン形式の検証
if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9]+)?$ ]]; then
  echo "エラー: 無効なバージョン形式。X.Y.Z または X.Y.Z-qualifier を使用してください"
  exit 1
fi

echo "🚀 バージョン $VERSION のリリースプロセスを開始"

# クリーンとビルド
echo "🔨 プロジェクトをビルド中..."
./gradlew clean build -x test -PreleaseVersion=$VERSION

# リリースディレクトリ作成
RELEASE_DIR="build/release"
RELEASE_ARTIFACTS_DIR="$RELEASE_DIR/artifacts"
rm -rf $RELEASE_DIR
mkdir -p $RELEASE_ARTIFACTS_DIR

# メインアプリケーションJARの収集
echo ""
echo "📦 メインアプリケーションを収集中..."
MAIN_JAR="app/build/libs/idp-server-$VERSION.jar"
if [ -f "$MAIN_JAR" ]; then
  # artifacts ディレクトリとリリースディレクトリ両方にコピー
  cp "$MAIN_JAR" $RELEASE_ARTIFACTS_DIR/
  cp "$MAIN_JAR" $RELEASE_DIR/
  echo "  ✓ idp-server-$VERSION.jar"
else
  echo "  ❌ エラー: メインアプリケーションJARが見つかりません: $MAIN_JAR"
  exit 1
fi

# ライブラリJARの収集（バージョン一致のみ）
echo ""
echo "📚 ライブラリモジュールを収集中..."
COLLECTED_COUNT=0
SKIPPED_COUNT=0

find libs -name "*.jar" -path "*/build/libs/*" -not -name "*-plain.jar" | sort | while read jar; do
  JAR_NAME=$(basename "$jar")
  # JARファイル名からバージョンを抽出
  if [[ "$jar" =~ -${VERSION}\.jar$ ]]; then
    cp "$jar" $RELEASE_ARTIFACTS_DIR/
    echo "  ✓ $JAR_NAME"
    COLLECTED_COUNT=$((COLLECTED_COUNT + 1))
  else
    echo "  ⊗ スキップ $JAR_NAME (バージョン不一致)"
    SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
  fi
done

# 収集されたJARの検証
JAR_COUNT=$(ls $RELEASE_ARTIFACTS_DIR/*.jar 2>/dev/null | wc -l | tr -d ' ')
if [ "$JAR_COUNT" -eq 0 ]; then
  echo ""
  echo "❌ エラー: バージョン $VERSION に一致するJARが見つかりません"
  echo "ヒント: 各モジュールの build.gradle で releaseVersion プロパティが正しく設定されているか確認してください"
  exit 1
fi

# DDLスクリプトの収集
echo ""
echo "📜 データベーススクリプトを収集中..."
DB_DIR="$RELEASE_DIR/database"
mkdir -p $DB_DIR

if [ -d "libs/idp-server-database/mysql" ]; then
  cp -r libs/idp-server-database/mysql $DB_DIR/
  echo "  ✓ MySQL DDL scripts"
fi

if [ -d "libs/idp-server-database/postgresql" ]; then
  cp -r libs/idp-server-database/postgresql $DB_DIR/
  echo "  ✓ PostgreSQL DDL scripts"
fi

# ZIPアーカイブの作成
echo ""
echo "📦 ライブラリアーカイブを作成中..."
cd $RELEASE_ARTIFACTS_DIR
zip -q -r "../idp-server-libs-$VERSION.zip" *.jar
cd - > /dev/null
echo "  ✓ idp-server-libs-$VERSION.zip"

# データベーススクリプトのZIP作成
echo ""
echo "📦 データベーススクリプトアーカイブを作成中..."
cd $DB_DIR
zip -q -r "../idp-server-database-$VERSION.zip" mysql postgresql
cd - > /dev/null
echo "  ✓ idp-server-database-$VERSION.zip"

# チェックサムの生成
echo ""
echo "🔐 チェックサムを生成中..."
sha256sum "$RELEASE_DIR/idp-server-$VERSION.jar" > $RELEASE_DIR/checksums.txt
sha256sum "$RELEASE_DIR/idp-server-libs-$VERSION.zip" >> $RELEASE_DIR/checksums.txt
sha256sum "$RELEASE_DIR/idp-server-database-$VERSION.zip" >> $RELEASE_DIR/checksums.txt
echo "  ✓ checksums.txt"

# マニフェストの生成
echo ""
echo "📋 マニフェストを生成中..."
{
  echo "Release Version: $VERSION"
  echo "Build Date: $(date -u +"%Y-%m-%d %H:%M:%S UTC")"
  echo "Build Host: $(hostname)"
  echo ""
  echo "Included JARs ($JAR_COUNT files):"
  echo "================================"
  ls -1 $RELEASE_ARTIFACTS_DIR/*.jar | xargs -n1 basename
} > $RELEASE_DIR/manifest.txt
echo "  ✓ manifest.txt"

echo ""
echo "✅ リリース成果物の作成が完了しました！"
echo ""
echo "📂 出力先: $RELEASE_DIR"
echo "  - idp-server-$VERSION.jar (メインアプリケーション)"
echo "  - idp-server-libs-$VERSION.zip ($JAR_COUNT ライブラリJARs)"
echo "  - idp-server-database-$VERSION.zip (MySQL/PostgreSQL DDLスクリプト)"
echo "  - checksums.txt (SHA256チェックサム)"
echo "  - manifest.txt (含まれるファイル一覧)"
echo "  - artifacts/ ディレクトリ (全JARファイル)"
echo "  - database/ ディレクトリ (mysql/, postgresql/)"
echo ""
echo "🔍 マニフェスト内容:"
cat $RELEASE_DIR/manifest.txt
echo ""
echo "🔐 チェックサム:"
cat $RELEASE_DIR/checksums.txt
