#!/bin/bash

# Flutter SO包分发服务器启动脚本

SERVER_PORT=1235

# 杀掉占用1234端口的进程
echo "🔍 检查端口$SERVER_PORT占用情况..."
PORT_PID=$(lsof -ti:$SERVER_PORT 2>/dev/null)
if [ ! -z "$PORT_PID" ]; then
    echo "🛑 杀掉占用端口$SERVER_PORT的进程: $PORT_PID"
    kill -9 $PORT_PID
    sleep 1
fi

# 设置ADB反向端口映射
echo "🔗 设置ADB反向端口映射..."
adb reverse tcp:$SERVER_PORT tcp:$SERVER_PORT 2>/dev/null

# 进入脚本目录
cd "$(dirname "$0")"

# 安装依赖
echo "📦 安装依赖..."
dart pub get > /dev/null 2>&1

# 创建SO包目录
mkdir -p so_packages

# 启动服务器
echo "🚀 启动服务器..."
nohup dart run bin/server.dart $SERVER_PORT > server.log 2>&1 &

sleep 2

# 检查服务器是否启动成功
if curl -s http://127.0.0.1:$SERVER_PORT/api/status > /dev/null 2>&1; then
    echo "✅ 服务器启动成功: http://127.0.0.1:$SERVER_PORT"
else
    echo "❌ 服务器启动失败，请查看日志: cat server.log"
fi