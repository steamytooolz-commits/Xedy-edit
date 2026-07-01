#!/bin/bash
# Alpine RootFS Preparation and Bootstrap Script for Alpine Xed-Editor
# This script sets up a proot-based Alpine Linux rootfs environment on Android.

set -e

WORKSPACE_DIR="/home/alpine/workspace"
ROOTFS_DIR="${WORKSPACE_DIR}/rootfs"
ALPINE_VERSION="3.21.0"
ARCH="aarch64"
ROOTFS_URL="https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/${ARCH}/alpine-minirootfs-${ALPINE_VERSION}-${ARCH}.tar.gz"

echo "🏔️ Preparing Alpine Linux RootFS..."

# Create directories
mkdir -p "${ROOTFS_DIR}"
mkdir -p "${WORKSPACE_DIR}/tmp"

# Check if rootfs already extracted
if [ -f "${ROOTFS_DIR}/bin/sh" ]; then
    echo "✅ Alpine RootFS already exists at ${ROOTFS_DIR}."
    exit 0
fi

echo "📥 Downloading Alpine Mini-RootFS from ${ROOTFS_URL}..."
curl -L "${ROOTFS_URL}" -o "${WORKSPACE_DIR}/tmp/alpine-rootfs.tar.gz"

echo "📦 Extracting RootFS..."
tar -xzf "${WORKSPACE_DIR}/tmp/alpine-rootfs.tar.gz" -C "${ROOTFS_DIR}"

echo "⚙️ Configuring DNS & Environment..."
# Configure resolver
echo "nameserver 8.8.8.8" > "${ROOTFS_DIR}/etc/resolv.conf"
echo "nameserver 1.1.1.1" >> "${ROOTFS_DIR}/etc/resolv.conf"

# Add localhost definition
echo "127.0.0.1 localhost" > "${ROOTFS_DIR}/etc/hosts"

# Setup package repositories
echo "https://dl-cdn.alpinelinux.org/alpine/v3.21/main" > "${ROOTFS_DIR}/etc/apk/repositories"
echo "https://dl-cdn.alpinelinux.org/alpine/v3.21/community" >> "${ROOTFS_DIR}/etc/apk/repositories"

# Cleanup temp files
rm -rf "${WORKSPACE_DIR}/tmp"

echo "🎉 Alpine Linux rootfs bootstrapped successfully!"
