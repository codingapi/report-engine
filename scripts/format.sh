#!/usr/bin/env bash
# 手动格式化前后端代码。
#
# 用法：
#   ./scripts/format.sh            # 格式化全部（前端 + 后端）
#   ./scripts/format.sh frontend   # 仅前端 (prettier)
#   ./scripts/format.sh backend    # 仅后端 (spotless)
#
# 前端：report-frontend 下用 prettier --write . （受 .prettierignore 控制）
# 后端：./mvnw spotless:apply （AOSP 风格 / 4 空格，匹配现有代码）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-all}"

format_frontend() {
	echo "▸ 格式化前端 (prettier)..."
	cd "$ROOT/report-frontend"
	# 用 workspace 本地 prettier，无需全局安装
	if [ ! -x "./node_modules/.bin/prettier" ]; then
		echo "  ✗ 未找到 report-frontend/node_modules/.bin/prettier，请先在 report-frontend 下 pnpm install" >&2
		exit 1
	fi
	./node_modules/.bin/prettier --write .
	echo "✓ 前端格式化完成"
}

format_backend() {
	echo "▸ 格式化后端 (spotless)..."
	cd "$ROOT"
	if [ ! -x "./mvnw" ]; then
		echo "  ✗ 未找到 ./mvnw" >&2
		exit 1
	fi
	# 默认 dev profile 覆盖全部模块；spotless:apply 整模块批量格式化
	./mvnw -q spotless:apply
	echo "✓ 后端格式化完成"
}

case "$TARGET" in
	frontend) format_frontend ;;
	backend)  format_backend ;;
	all)      format_frontend; format_backend ;;
	*)
		echo "用法: $0 [frontend|backend|all]" >&2
		exit 1
		;;
esac
