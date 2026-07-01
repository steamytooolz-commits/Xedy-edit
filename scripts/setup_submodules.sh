#!/bin/bash
# Script to add and synchronize the soraX editor submodule
# Usage: ./setup_submodules.sh

set -e

echo "✨ Initializing submodules..."

# Check if .git directory exists
if [ ! -d ".git" ]; then
    echo "⚠️ Git repository not initialized. Initializing git repository first..."
    git init
fi

# Add soraX as submodule if not already added
if [ ! -d "app/src/main/java/com/example/ui/editor/sorax" ]; then
    echo "➕ Adding soraX submodule (simulated/actual checkout)..."
    # Execute actual submodule command
    git submodule add --force https://github.com/sorax-editor/soraX.git app/src/main/java/com/example/ui/editor/sorax || true
fi

git submodule update --init --recursive

echo "✅ soraX submodule setup complete!"
