#!/bin/bash

DOCKER_USERNAME="devepcam"
IMAGE_NAME="backend-usuarios"
TAG_VERSION="v1.0.0-amd64"
TAG_LATEST="latest"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}Building Backend Go Docker image for AMD64...${NC}"

if ! docker buildx version &> /dev/null; then
    echo -e "${YELLOW}Setting up Docker buildx...${NC}"
    docker buildx create --use --name backend-builder
    docker buildx inspect --bootstrap
fi

echo -e "${YELLOW}Logging into Docker Hub...${NC}"
docker login -u ${DOCKER_USERNAME}

if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to login to Docker Hub${NC}"
    exit 1
fi

echo -e "${GREEN}Building and pushing image...${NC}"

docker buildx build \
    --platform linux/amd64 \
    -t ${DOCKER_USERNAME}/${IMAGE_NAME}:${TAG_VERSION} \
    -t ${DOCKER_USERNAME}/${IMAGE_NAME}:${TAG_LATEST} \
    -f Dockerfile \
    . \
    --push

if [ $? -eq 0 ]; then
    echo -e "${GREEN}Successfully built and pushed!${NC}"
    echo -e "${GREEN}Images available:${NC}"
    echo "  - ${DOCKER_USERNAME}/${IMAGE_NAME}:${TAG_VERSION}"
    echo "  - ${DOCKER_USERNAME}/${IMAGE_NAME}:${TAG_LATEST}"
    echo ""
    echo "To pull: docker pull ${DOCKER_USERNAME}/${IMAGE_NAME}:${TAG_VERSION}"
else
    echo -e "${RED}Build failed${NC}"
    exit 1
fi