#!/bin/sh

sudo apt install -y qemu-user-static binfmt-support
docker buildx create --use
docker buildx ls

docker buildx build --platform linux/amd64,linux/386,linux/arm64 -t kylelin1998/chatgpt-tg-bot . --push