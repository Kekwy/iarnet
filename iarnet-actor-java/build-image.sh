#!/usr/bin/env bash

# iarnet-actor-java 镜像构建脚本
# 使用方式:
#   ./build-image.sh [tag_name]

set -e

# 默认镜像标签
DEFAULT_TAG="iarnet-actor-java:latest"
IMAGE_TAG="${1:-$DEFAULT_TAG}"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}开始构建 iarnet-actor-java Actor 底座镜像...${NC}"

# 获取脚本所在目录和项目根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo -e "${YELLOW}当前模块目录: $SCRIPT_DIR${NC}"
echo -e "${YELLOW}项目根目录: $PROJECT_ROOT${NC}"
echo -e "${YELLOW}目标镜像标签: $IMAGE_TAG${NC}"

# 检查 Docker 是否运行
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}错误: Docker daemon 未运行${NC}"
    exit 1
fi

# 构建可执行 JAR
echo -e "${YELLOW}开始构建可执行 JAR...${NC}"
cd "$PROJECT_ROOT"
mvn -pl iarnet-actor-java -am package

# 构建 Docker 镜像
echo -e "${YELLOW}开始 Docker 构建...${NC}"
cd "$SCRIPT_DIR"
if docker build -t "$IMAGE_TAG" .; then
    echo -e "${GREEN}✅ iarnet-actor-java 镜像构建成功!${NC}"
    echo -e "${GREEN}镜像标签: $IMAGE_TAG${NC}"

    echo -e "${YELLOW}镜像信息:${NC}"
    docker images "$IMAGE_TAG"

    echo -e "${YELLOW}运行示例:${NC}"
    echo "docker run --rm \\"
    echo "  -e ACTOR_SERVER_PORT=9000 \\"
    echo "  -p 9000:9000 \\"
    echo "  $IMAGE_TAG"
else
    echo -e "${RED}❌ iarnet-actor-java 镜像构建失败!${NC}"
    exit 1
fi


