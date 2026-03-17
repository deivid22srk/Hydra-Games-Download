#!/bin/bash
set -e

ASSETS_DIR="core/main/src/main/assets/prebuilt"
mkdir -p "$ASSETS_DIR"

# x86_64
echo "Downloading x86_64 files..."
mkdir -p "$ASSETS_DIR/x86_64"
curl -L "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/x86_64/libtalloc.so.2" -o "$ASSETS_DIR/x86_64/libtalloc.so.2"
curl -L "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/x86_64/proot" -o "$ASSETS_DIR/x86_64/proot"
curl -L "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/x86_64/alpine-minirootfs-3.21.0-x86_64.tar.gz" -o "$ASSETS_DIR/x86_64/alpine.tar.gz"

# arm64-v8a (aarch64)
echo "Downloading arm64-v8a files..."
mkdir -p "$ASSETS_DIR/arm64-v8a"
curl -L "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/aarch64/libtalloc.so.2" -o "$ASSETS_DIR/arm64-v8a/libtalloc.so.2"
curl -L "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/aarch64/proot" -o "$ASSETS_DIR/arm64-v8a/proot"
curl -L "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.0-aarch64.tar.gz" -o "$ASSETS_DIR/arm64-v8a/alpine.tar.gz"

# armeabi-v7a (armhf)
echo "Downloading armeabi-v7a files..."
mkdir -p "$ASSETS_DIR/armeabi-v7a"
curl -L "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/arm/libtalloc.so.2" -o "$ASSETS_DIR/armeabi-v7a/libtalloc.so.2"
curl -L "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/arm/proot" -o "$ASSETS_DIR/armeabi-v7a/proot"
curl -L "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/armhf/alpine-minirootfs-3.21.0-armhf.tar.gz" -o "$ASSETS_DIR/armeabi-v7a/alpine.tar.gz"

echo "Pre-build download complete."
