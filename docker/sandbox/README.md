# GodlikeAgents 沙箱镜像

基于 Alpine Linux 的多语言代码执行沙箱镜像。

## 包含组件

| 组件 | 版本 | 说明 |
|------|------|------|
| Python | 3.12 | numpy, pandas, requests, httpx, pyyaml |
| Node.js | Alpine 默认最新 LTS | mathjs, axios, dayjs |
| Bash | 5.x | Shell 脚本执行 |
| Git | 最新 | 代码克隆 |
| curl / wget | 最新 | HTTP 请求 |

## 构建

```bash
# 本地构建
cd docker/sandbox
docker build -t godlikeagents/sandbox:1.0.0 .

# 推送到私有仓库
REGISTRY=registry.example.com bash build.sh
```

## 测试

```bash
# 测试 Python
docker run --rm godlikeagents/sandbox:1.0.0 python3 -c "import numpy; print(numpy.__version__)"

# 测试 Node.js
docker run --rm godlikeagents/sandbox:1.0.0 node -e "console.log(process.version)"

# 测试 Bash
docker run --rm godlikeagents/sandbox:1.0.0 bash -c "echo 'Hello from sandbox'"
```

## 安全配置

沙箱运行时建议的 Docker 参数：

```bash
docker run --rm \
    --network=none \              # 禁用网络
    --memory=512m \               # 内存限制
    --cpus=1.0 \                  # CPU 限制
    --pids-limit=100 \            # 进程数限制
    --read-only \                 # 只读文件系统
    --tmpfs /workspace:size=100m \ # 工作目录用 tmpfs
    --tmpfs /tmp:size=50m \
    --security-opt=no-new-privileges \
    godlikeagents/sandbox:1.0.0
```
