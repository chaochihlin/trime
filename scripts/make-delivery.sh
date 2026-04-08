#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Trime Watch Fork Contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#
# make-delivery.sh — 產生給客戶的原始碼交付快照
#
# 用途：
#   將目前 HEAD 的 tracked 檔案 + 所有 git submodule 內容打包成 tar.gz，
#   同時排除開發期檔案、密碼檔、CI 設定與個人化內容。
#
# 使用方式：
#   bash scripts/make-delivery.sh              # 輸出到 ./trime-delivery-<date>.tar.gz
#   bash scripts/make-delivery.sh custom-name  # 自訂檔名
#
# 產出內容：
#   - 所有 tracked 原始碼（包含 submodule 工作樹）
#   - LICENSE / THIRD_PARTY_NOTICES.md / doc/HANDOVER.md
#   - keystore.properties.sample
#   - Gradle wrapper
#
# 排除內容：
#   - .git/ 目錄（所有層級）
#   - build/ / .gradle/ / .idea/ / .kotlin/ / .vscode/ / .claude/
#   - keystore.properties / *.jks / *.keystore
#   - .github/workflows/（避免客戶 push 後觸發組織專屬 CI）
#   - CLAUDE.md（內部 AI agent 指引，含裝置序號）
#   - IMPLEMENTATION_PLAN.md / WATCH_ADAPTATION_PLAN.md /
#     OPTIMIZATION_VERIFICATION.md / PERFORMANCE_TEST_GUIDE.md
#     （開發期工作紀錄，含未完成項）
#   - osfans_alipay.png（上游作者收款碼，與功能無關）
#   - .idea/copyright/（IDE 個人化設定）
#   - build.log / *.apk / *.aab / *.DS_Store

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

# ------- 0. 基本檢查 -------

if [[ ! -d .git ]]; then
  echo "[ERROR] 此腳本需在 git repository 根目錄執行" >&2
  exit 1
fi

if ! command -v git >/dev/null 2>&1; then
  echo "[ERROR] 找不到 git 指令" >&2
  exit 1
fi

if ! command -v tar >/dev/null 2>&1; then
  echo "[ERROR] 找不到 tar 指令" >&2
  exit 1
fi

# ------- 1. 計算輸出檔名 -------

DATE_TAG="$(date +%Y%m%d)"
DEFAULT_NAME="trime-delivery-${DATE_TAG}"
OUT_NAME="${1:-$DEFAULT_NAME}"
OUT_FILE="${OUT_NAME}.tar.gz"
STAGING_DIR="$(mktemp -d -t trime-delivery-XXXXXX)"
STAGING_ROOT="${STAGING_DIR}/${OUT_NAME}"

echo "[INFO] 交付包名稱：${OUT_FILE}"
echo "[INFO] 暫存目錄：${STAGING_DIR}"

cleanup() {
  if [[ -d "$STAGING_DIR" ]]; then
    rm -rf "$STAGING_DIR"
  fi
}
trap cleanup EXIT

# ------- 2. 檢查工作目錄狀態 -------

if [[ -n "$(git status --porcelain)" ]]; then
  echo "[WARN] 工作目錄有未提交變動，以下檔案只有 HEAD 的版本會被打包："
  git status --porcelain | head -20
  echo ""
  read -r -p "繼續打包? [y/N] " REPLY
  case "$REPLY" in
    [yY]*) ;;
    *) echo "[INFO] 使用者取消"; exit 0 ;;
  esac
fi

# ------- 3. 匯出主 repo（git archive HEAD） -------

mkdir -p "$STAGING_ROOT"
echo "[INFO] 匯出主 repo HEAD 到暫存目錄…"
git archive --format=tar HEAD | tar -x -C "$STAGING_ROOT"

# ------- 4. 匯出每個 submodule（含巢狀） -------

echo "[INFO] 匯出 submodule…"
# shellcheck disable=SC2016
# 理由：$displaypath / $path 由 git submodule foreach 於子 shell 中提供，
# 需保留單引號避免外層 shell 提前展開。
git submodule foreach --recursive --quiet '
  set -e
  sub_path="${displaypath:-$path}"
  target="'"$STAGING_ROOT"'/${sub_path}"
  mkdir -p "${target}"
  git archive --format=tar HEAD | tar -x -C "${target}"
  echo "  - ${sub_path}"
'

# ------- 5. 移除不該交付的檔案 -------

echo "[INFO] 移除開發期 / 密碼 / CI / 個人化檔案…"

EXCLUDE_PATHS=(
  # IDE / build 產物（通常 git archive 已排除，但雙重保險）
  ".idea"
  ".gradle"
  ".kotlin"
  ".vscode"
  ".claude"
  "build"
  # 密碼與簽章
  "keystore.properties"
  "trime-release.jks"
  # CI（客戶 push 後可能誤觸組織 secret）
  ".github/workflows"
  # 開發期 AI / 工作紀錄文件
  "CLAUDE.md"
  "IMPLEMENTATION_PLAN.md"
  "WATCH_ADAPTATION_PLAN.md"
  "OPTIMIZATION_VERIFICATION.md"
  "PERFORMANCE_TEST_GUIDE.md"
  # 觀感類
  "osfans_alipay.png"
)

for p in "${EXCLUDE_PATHS[@]}"; do
  target="${STAGING_ROOT}/${p}"
  if [[ -e "$target" || -L "$target" ]]; then
    rm -rf "$target"
    echo "  - 移除 ${p}"
  fi
done

# 再掃一次並清掉意外殘留
find "$STAGING_ROOT" -type f \( \
  -name "*.jks" -o \
  -name "*.keystore" -o \
  -name "*.apk" -o \
  -name "*.aab" -o \
  -name "*.log" -o \
  -name ".DS_Store" -o \
  -name "Thumbs.db" \
  \) -print -delete | sed 's/^/  - /' || true

# 移除所有 __pycache__ 與 .pyc
find "$STAGING_ROOT" -type d -name "__pycache__" -prune -exec rm -rf {} + 2>/dev/null || true
find "$STAGING_ROOT" -type f -name "*.pyc" -delete 2>/dev/null || true

# 移除所有殘留 .git 物件（git archive 已不會產出，但 submodule 若以 tar 解包可能遺留）
find "$STAGING_ROOT" -type d -name ".git" -prune -exec rm -rf {} + 2>/dev/null || true
find "$STAGING_ROOT" -type f -name ".gitattributes" -delete 2>/dev/null || true
find "$STAGING_ROOT" -type f -name ".gitmodules" -delete 2>/dev/null || true

# ------- 6. 產出 README 提示 -------

cat > "${STAGING_ROOT}/DELIVERY_README.txt" <<'EOF'
========================================
Trime Watch Fork — 原始碼交付包
========================================

本包為程式碼快照，不含 .git 歷史。所有 git submodule 的內容
已在對應目錄下內嵌，**請勿執行 `git submodule update`**
（會把內嵌內容覆蓋為空）。

快速開始：
  1. 解壓：tar -xzf trime-delivery-<date>.tar.gz
  2. 進入目錄
  3. 閱讀 doc/HANDOVER.md（繁中接手指南，包含 build、部署、架構）
  4. 閱讀 THIRD_PARTY_NOTICES.md（第三方授權說明）
  5. Debug build：BUILD_ABI=armeabi-v7a ./gradlew assembleDebug

若需要 release build：
  複製 keystore.properties.sample 為 keystore.properties，
  填入你的簽章設定後執行 make release。

授權：GPL-3.0-or-later（見 LICENSE）
EOF

# ------- 7. 打包 -------

echo "[INFO] 產生 ${OUT_FILE}…"
tar -czf "${REPO_ROOT}/${OUT_FILE}" -C "${STAGING_DIR}" "${OUT_NAME}"

SIZE="$(du -h "${REPO_ROOT}/${OUT_FILE}" | awk '{print $1}')"
echo ""
echo "[DONE] 交付包已產生："
echo "       ${REPO_ROOT}/${OUT_FILE}  (${SIZE})"
echo ""
echo "建議後續步驟："
echo "  1. 解壓到乾淨目錄做最後人工檢視"
echo "  2. 至少跑一次 debug build 確認可重現建置"
echo "  3. 確認 doc/HANDOVER.md 與 THIRD_PARTY_NOTICES.md 存在"
