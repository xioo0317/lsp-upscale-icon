#!/usr/bin/env bash
# 一键把本仓库推送到 GitHub 并触发 CI。
#
# 前提：本地 gh 已登录（gh auth login）或设置了 GITHUB_TOKEN / GH_TOKEN。
# 沙箱内的 GitHub 集成令牌只有读权限，无法建仓/推送，故需在本机执行。
#
# 用法：
#   ./push-to-github.sh                 # 推到 xioo0317/lsp-upscale-icon
#   ./push-to-github.sh <owner> <repo>  # 自定义目标
#   ./push-to-github.sh --bundle-only    # 只导出 bundle，不联网

set -euo pipefail

OWNER="${1:-xioo0317}"
REPO="${2:-lsp-upscale-icon}"
BUNDLE="lsp-upscale-icon.bundle"

if [[ "${1:-}" == "--bundle-only" ]]; then
    git bundle create "$BUNDLE" --all
    echo "已导出 $BUNDLE（接收方：git clone $BUNDLE lsp-upscale-icon）"
    exit 0
fi

command -v gh >/dev/null 2>&1 || { echo "需要 gh CLI：https://cli.github.com"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "请先 gh auth login 或设置 GH_TOKEN"; exit 1; }

# 若远程仓库不存在则创建（public）
if ! gh repo view "$OWNER/$REPO" >/dev/null 2>&1; then
    echo "创建仓库 $OWNER/$REPO ..."
    gh repo create "$OWNER/$REPO" --public \
        --description "ColorOS 大图标放大 LSP 模块 (libxposed API 101/102)"
fi

git remote remove origin 2>/dev/null || true
git remote add origin "https://github.com/$OWNER/$REPO.git"
git push -u origin main --force

echo "已推送。CI 将自动构建 API 101 / 102 两个构件："
echo "  https://github.com/$OWNER/$REPO/actions"

# 可选：打 tag 触发 GitHub Release 并把 APK 挂上去
# git tag v1.0.0 && git push origin v1.0.0
