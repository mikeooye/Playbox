#!/usr/bin/env bash
#
# 在本机构建 **已签名** 的 release APK，并把它作为附件上传到对应 tag 的 GitHub Release。
#
# 为什么需要这个脚本：签名用的 keystore 只存在于本机（刻意不进版本库，见 .gitignore），
# 所以带签名的安装包只能在这台机器上构建，构建完再传回 GitHub 的 Release 附件。
#
# 用法：
#   tools/release.sh                     # 用 build.gradle.kts 的 versionName 推导 tag=v<版本>，构建并上传
#   tools/release.sh v1.1.0              # 指定要发布的 tag
#   tools/release.sh --no-upload         # 只构建，产出 dist/Playbox-<版本>.apk
#   tools/release.sh --no-build          # 不重新构建，直接上传已有的 dist/ 安装包
#   tools/release.sh --draft             # 建 Release 时用草稿状态
#   tools/release.sh --notes "修复 xxx"  # 自定义 Release 说明（默认自动生成更新日志）
#   tools/release.sh --repo owner/name   # 覆盖自动识别的仓库
#
# 依赖：bash、curl、jq、git、Android SDK（ANDROID_HOME）以及本机的 keystore.properties。
#
# 上传凭证按顺序取：环境变量 GH_TOKEN → GITHUB_TOKEN → `gh auth token`。
# token 只写进权限 600 的临时 curl 配置里，不会出现在命令行参数（ps 可见）中。
#
# 沙箱/CI 里若不能直接用 ./gradlew，可用环境变量覆盖：GRADLE_BIN=./.dsh-build.sh tools/release.sh

set -euo pipefail

ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT_DIR"

TAG=""
DO_BUILD=1
DO_UPLOAD=1
IS_DRAFT=false
IS_PRERELEASE=false
NOTES=""
REPO_SLUG=""
APK_OVERRIDE=""
GRADLE_BIN=${GRADLE_BIN:-./gradlew}
GRADLE_EXTRA=()

info() { printf '==> %s\n' "$*"; }
warn() { printf '警告: %s\n' "$*" >&2; }
die() { printf '错误: %s\n' "$*" >&2; exit 1; }

# 打印文件开头的注释块当作帮助信息，改注释就是改 --help，不会两处失同步。
usage() { sed -nE '/^#!/d; /^[^#]/q; s/^# ?//p' "${BASH_SOURCE[0]}"; }

# github.com 走代理时可能间歇性 SSL_ERROR_SYSCALL（见 README 里的排查记录），
# 所以网络命令统一重试，避免构建完卡在最后一步。
retry() {
  local tries=$1 n=1; shift
  while :; do
    if "$@"; then return 0; fi
    [ "$n" -lt "$tries" ] || return 1
    warn "第 $n 次失败，2 秒后重试：$*"
    n=$((n + 1))
    sleep 2
  done
}

while [ $# -gt 0 ]; do
  case "$1" in
    --no-build)    DO_BUILD=0 ;;
    --no-upload)   DO_UPLOAD=0 ;;
    --draft)       IS_DRAFT=true ;;
    --prerelease)  IS_PRERELEASE=true ;;
    --offline)     GRADLE_EXTRA+=(--offline) ;;
    --notes)       NOTES=${2:?--notes 需要一个参数}; shift ;;
    --notes-file)  NOTES=$(cat "${2:?--notes-file 需要一个文件路径}"); shift ;;
    --repo)        REPO_SLUG=${2:?--repo 需要 owner/name}; shift ;;
    --apk)         APK_OVERRIDE=${2:?--apk 需要一个文件路径}; shift ;;
    -h|--help)     usage; exit 0 ;;
    -*)            die "未知参数：$1（用 --help 看用法）" ;;
    *)             [ -z "$TAG" ] || die "只能指定一个 tag，已给出：$TAG"; TAG=$1 ;;
  esac
  shift
done

[ "$DO_BUILD" = 1 ] || [ "$DO_UPLOAD" = 1 ] || die "--no-build 和 --no-upload 不能同时用"

# ---------- 版本信息 ----------

# 从 build.gradle.kts 里读 versionName / versionCode，作为 tag 和附件名的唯一来源。
gradle_value() {
  sed -nE "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*\"?([^\"]*[^\"[:space:]])[\"[:space:]]*$/\1/p" \
    app/build.gradle.kts | head -n1
}

VERSION=$(gradle_value versionName)
VERSION_CODE=$(gradle_value versionCode)
[ -n "$VERSION" ] || die "没能从 app/build.gradle.kts 读出 versionName"
TAG=${TAG:-v$VERSION}

ASSET_NAME="Playbox-$VERSION.apk"
DIST_DIR="$ROOT_DIR/dist"
DIST_APK="$DIST_DIR/$ASSET_NAME"

info "版本：versionName=$VERSION versionCode=$VERSION_CODE → tag=$TAG"
info "附件名：$ASSET_NAME"

# ---------- 构建 ----------

if [ "$DO_BUILD" = 1 ]; then
  [ -f keystore.properties ] || die \
    "缺少 keystore.properties。带签名的安装包只能在保存了签名文件的机器上构建，
       没有它 Gradle 仍然能编译，但产物是未签名的，脚本会拒绝上传。"

  STORE_FILE=$(sed -nE 's/^[[:space:]]*storeFile[[:space:]]*=[[:space:]]*(.*[^[:space:]])[[:space:]]*$/\1/p' keystore.properties | head -n1)
  [ -n "$STORE_FILE" ] || die "keystore.properties 里没有 storeFile"
  [ -f "$STORE_FILE" ] || die "keystore.properties 指向的签名文件不存在：$STORE_FILE"
  info "签名文件：$STORE_FILE"

  [ -x "$GRADLE_BIN" ] || die "找不到可执行的 Gradle：${GRADLE_BIN}（可用 GRADLE_BIN 覆盖）"
  [ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ] || [ -f local.properties ] || \
    die "既没有 ANDROID_HOME/ANDROID_SDK_ROOT，也没有 local.properties，Gradle 找不到 Android SDK"

  info "开始构建 release 包（$GRADLE_BIN :app:assembleRelease）"
  # 空数组在 bash 3.2 + set -u 下展开会报错，所以两种情况分开写。
  if [ "${#GRADLE_EXTRA[@]}" -gt 0 ]; then
    "$GRADLE_BIN" :app:assembleRelease --console=plain "${GRADLE_EXTRA[@]}"
  else
    "$GRADLE_BIN" :app:assembleRelease --console=plain
  fi
fi

# ---------- 定位并校验产物 ----------

BUILT_APK="app/build/outputs/apk/release/app-release.apk"
if [ -n "$APK_OVERRIDE" ]; then
  APK=$APK_OVERRIDE
elif [ "$DO_BUILD" = 1 ]; then
  APK=$BUILT_APK
elif [ -f "$DIST_APK" ]; then
  APK=$DIST_APK
else
  APK=$BUILT_APK
fi
[ -f "$APK" ] || die "找不到安装包：$APK"

# 签名校验：宁可在这里失败，也不要把未签名或签名异常的包发出去。
find_sdk_tool() {
  local name=$1 sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
  [ -n "$sdk" ] || return 1
  ls -1 "$sdk"/build-tools/*/"$name" 2>/dev/null |
    sort -t. -k1,1n -k2,2n -k3,3n | tail -n1
}

APKSIGNER=$(command -v apksigner || find_sdk_tool apksigner || true)
if [ -n "$APKSIGNER" ]; then
  if ! SIGNOUT=$("$APKSIGNER" verify --print-certs "$APK" 2>&1); then
    die "签名校验失败，拒绝上传：
$SIGNOUT"
  fi
  SIGNER=$(printf '%s\n' "$SIGNOUT" | sed -nE 's/^Signer #1 certificate DN: (.*)$/\1/p' | head -n1)
  info "签名校验通过：${SIGNER:-未知签名者}"
else
  warn "没找到 apksigner，跳过签名校验（建议装好 build-tools）"
fi

# 包内的 versionName 必须和文件名/tag 一致，防止把旧产物传到新 tag 上。
AAPT2=$(command -v aapt2 || find_sdk_tool aapt2 || true)
if [ -n "$AAPT2" ]; then
  APK_VERSION=$("$AAPT2" dump badging "$APK" 2>/dev/null |
    sed -nE "s/.*versionName='([^']*)'.*/\1/p" | head -n1)
  if [ -n "$APK_VERSION" ]; then
    [ "$APK_VERSION" = "$VERSION" ] || die \
      "包内 versionName=${APK_VERSION}，但 build.gradle.kts 是 ${VERSION}；先重新构建再上传"
    info "包内 versionName 校验通过：$APK_VERSION"
  fi
fi

mkdir -p "$DIST_DIR"
if [ "$APK" != "$DIST_APK" ]; then
  cp -f "$APK" "$DIST_APK"
fi
SHA256=$(shasum -a 256 "$DIST_APK" | awk '{print $1}')
SIZE=$(du -h "$DIST_APK" | awk '{print $1}')
info "产物：${DIST_APK}（${SIZE}）"
info "SHA-256：$SHA256"

if [ "$DO_UPLOAD" = 0 ]; then
  info "按 --no-upload 要求，构建到此结束。"
  exit 0
fi

# ---------- 上传 ----------

command -v jq >/dev/null 2>&1 || die "上传需要 jq，请先 brew install jq"
command -v curl >/dev/null 2>&1 || die "上传需要 curl"

if [ -z "$REPO_SLUG" ]; then
  REMOTE_URL=$(git remote get-url origin 2>/dev/null) || die "没有 origin 远端，请用 --repo owner/name"
  REPO_SLUG=$(printf '%s\n' "$REMOTE_URL" |
    sed -E 's#^git@[^:]+:##; s#^[a-z]+://[^/]+/##; s#\.git$##; s#/$##')
fi
case "$REPO_SLUG" in
  */*) ;;
  *) die "仓库名不合法：${REPO_SLUG}（应为 owner/name）" ;;
esac
info "目标仓库：$REPO_SLUG"

# tag 必须先存在于远端，否则 GitHub 会拿默认分支新建一个 tag，发布到错误的位置。
if ! retry 3 git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1; then
  die "远端没有 tag ${TAG}。请先：git push origin $TAG"
fi
info "远端 tag 已确认：$TAG"

TOKEN=${GH_TOKEN:-${GITHUB_TOKEN:-}}
if [ -z "$TOKEN" ] && command -v gh >/dev/null 2>&1; then
  TOKEN=$(gh auth token 2>/dev/null || true)
fi
[ -n "$TOKEN" ] || die \
  "没有 GitHub token。请设置 GH_TOKEN 环境变量（勾选 repo 权限），例如：
       GH_TOKEN=ghp_xxx tools/release.sh
   或先 brew install gh && gh auth login"

WORK_DIR=$(mktemp -d "$DIST_DIR/.upload.XXXXXX")
# token 走 curl 配置文件，不进 argv；退出时连同临时文件一起删掉。
CURL_CONF="$WORK_DIR/curlrc"
umask 077
{
  printf 'header = "Authorization: Bearer %s"\n' "$TOKEN"
  printf 'header = "Accept: application/vnd.github+json"\n'
  printf 'header = "X-GitHub-Api-Version: 2022-11-28"\n'
  printf 'header = "User-Agent: playbox-release-script"\n'
} > "$CURL_CONF"
trap 'rm -rf "$WORK_DIR"' EXIT

# api <METHOD> <PATH> <OUT_FILE> [JSON_BODY] → 打印 HTTP 状态码
api() {
  local method=$1 path=$2 out=$3 body=${4:-}
  local args=(--config "$CURL_CONF" --silent --show-error --location
              --request "$method" --output "$out" --write-out '%{http_code}')
  [ -n "$body" ] && args+=(--header 'Content-Type: application/json' --data-binary "$body")
  curl "${args[@]}" "https://api.github.com$path"
}

REL_JSON="$WORK_DIR/release.json"
info "查询 Release：$TAG"
CODE=$(retry 3 api GET "/repos/$REPO_SLUG/releases/tags/$TAG" "$REL_JSON")

if [ "$CODE" = 200 ]; then
  RELEASE_ID=$(jq -r '.id' "$REL_JSON")
  info "Release 已存在（id=${RELEASE_ID}），将更新附件"
  [ "$IS_DRAFT" = false ] && [ "$IS_PRERELEASE" = false ] || warn "--draft/--prerelease 只在新建 Release 时生效，已存在的不会改"
elif [ "$CODE" = 404 ]; then
  info "Release 不存在，新建一个"
  if [ -n "$NOTES" ]; then
    BODY="$NOTES

**$ASSET_NAME**

SHA-256: \`$SHA256\`"
    PAYLOAD=$(jq -n --arg tag "$TAG" --arg name "Playbox $VERSION" --arg body "$BODY" \
      --argjson draft "$IS_DRAFT" --argjson prerelease "$IS_PRERELEASE" \
      '{tag_name:$tag, name:$name, body:$body, draft:$draft, prerelease:$prerelease}')
  else
    PAYLOAD=$(jq -n --arg tag "$TAG" --arg name "Playbox $VERSION" \
      --argjson draft "$IS_DRAFT" --argjson prerelease "$IS_PRERELEASE" \
      '{tag_name:$tag, name:$name, generate_release_notes:true, draft:$draft, prerelease:$prerelease}')
  fi
  CODE=$(retry 3 api POST "/repos/$REPO_SLUG/releases" "$REL_JSON" "$PAYLOAD")
  [ "$CODE" = 201 ] || die "新建 Release 失败（HTTP ${CODE}）：$(cat "$REL_JSON")"
  RELEASE_ID=$(jq -r '.id' "$REL_JSON")
  info "Release 已创建（id=${RELEASE_ID}）"
else
  die "查询 Release 失败（HTTP ${CODE}）：$(cat "$REL_JSON")"
fi

# 同名附件先删掉，脚本可以反复执行。
ASSET_ID=$(jq -r --arg n "$ASSET_NAME" '[.assets[]? | select(.name == $n) | .id] | first // empty' "$REL_JSON")
if [ -n "$ASSET_ID" ]; then
  info "删除同名旧附件（id=${ASSET_ID}）"
  CODE=$(retry 3 api DELETE "/repos/$REPO_SLUG/releases/assets/$ASSET_ID" "$WORK_DIR/del.json")
  [ "$CODE" = 204 ] || die "删除旧附件失败（HTTP ${CODE}）：$(cat "$WORK_DIR/del.json")"
fi

info "上传 $ASSET_NAME …"
UPLOAD_JSON="$WORK_DIR/upload.json"
CODE=$(retry 3 curl --config "$CURL_CONF" --silent --show-error --location \
  --request POST \
  --header 'Content-Type: application/vnd.android.package-archive' \
  --data-binary "@$DIST_APK" \
  --output "$UPLOAD_JSON" --write-out '%{http_code}' \
  "https://uploads.github.com/repos/$REPO_SLUG/releases/$RELEASE_ID/assets?name=$ASSET_NAME")

if [ "$CODE" = 201 ]; then
  DOWNLOAD_URL=$(jq -r '.browser_download_url' "$UPLOAD_JSON")
  info "上传完成 ✅"
  printf '\n  下载地址：%s\n  文件大小：%s\n  SHA-256 ：%s\n\n' "$DOWNLOAD_URL" "$SIZE" "$SHA256"
else
  die "上传失败（HTTP ${CODE}）：$(cat "$UPLOAD_JSON")"
fi
