#!/bin/bash





set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SUPERVISION_DIR="$SCRIPT_DIR/supervision"

echo "📁 Build directory: $SUPERVISION_DIR"


echo ""
echo "🔍 Checking required files..."

REQUIRED_FILES=(
    "$SUPERVISION_DIR/Dockerfile.supervision-app"
    "$SUPERVISION_DIR/Dockerfile.supervision-ai"
    "$SUPERVISION_DIR/Dockerfile.supervision-web"
    "$SUPERVISION_DIR/Dockerfile.supervision-db"
    "$SUPERVISION_DIR/nginx.conf"
    "$SUPERVISION_DIR/application.properties"
    "$SUPERVISION_DIR/app.jar"
)

for file in "${REQUIRED_FILES[@]}"; do
    if [ ! -f "$file" ]; then
        echo "❌ Missing: $file"
        exit 1
    fi
done

if [ ! -d "$SUPERVISION_DIR/react-build" ]; then
    echo "❌ Missing: $SUPERVISION_DIR/react-build/ (run 'npm run build' first)"
    exit 1
fi

if [ ! -d "$SUPERVISION_DIR/flask-ai" ]; then
    echo "❌ Missing: $SUPERVISION_DIR/flask-ai/"
    exit 1
fi

echo "✅ All required files present"


cd "$SUPERVISION_DIR"

echo ""
echo "Building telecom-supervision-db..."
docker build -t telecom-supervision-db:latest -f Dockerfile.supervision-db .

echo ""
echo "Building telecom-supervision-app..."
docker build -t telecom-supervision-app:latest -f Dockerfile.supervision-app .

echo ""
echo "Building telecom-supervision-ai..."
docker build -t telecom-supervision-ai:latest -f Dockerfile.supervision-ai .

echo ""
echo "Building telecom-supervision-web..."
docker build -t telecom-supervision-web:latest -f Dockerfile.supervision-web .

echo ""
echo "✅ All supervision images built successfully!"
echo ""
docker images | grep telecom-supervision
