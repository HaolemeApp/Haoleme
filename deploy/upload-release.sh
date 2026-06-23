#!/usr/bin/env bash
# Build and upload Haoleme Python wheel and/or Android APK to the cloud server.
#
# Usage:
#   ./deploy/upload-release.sh              # upload python + android (if APK exists or can be built)
#   ./deploy/upload-release.sh --python     # python wheel + cloud package only
#   ./deploy/upload-release.sh --android    # android APK only
#   ./deploy/upload-release.sh --github     # also create a GitHub release
#
# Auth (pick one):
#   export HAOLEME_UPLOAD_PASSWORD='...'
#   ssh root@39.96.50.42   # when SSH keys are configured
#
# Optional env:
#   HAOLEME_UPLOAD_HOST=39.96.50.42
#   HAOLEME_UPLOAD_USER=root
#   HAOLEME_PUBLIC_URL=http://39.96.50.42
#   HAOLEME_ANDROID_NOTES="Release notes for the app"
#   HAOLEME_PYTHON_NOTES="Release notes for hao CLI"
#   JAVA_HOME=/path/to/jdk

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

UPLOAD_PYTHON=1
UPLOAD_ANDROID=1
DO_GITHUB=0
SKIP_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --python)
      UPLOAD_ANDROID=0
      ;;
    --android)
      UPLOAD_PYTHON=0
      ;;
    --github)
      DO_GITHUB=1
      ;;
    --skip-build)
      SKIP_BUILD=1
      ;;
    -h|--help)
      sed -n '2,20p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
  shift
done

SERVER="${HAOLEME_UPLOAD_HOST:-39.96.50.42}"
SERVER_USER="${HAOLEME_UPLOAD_USER:-root}"
REMOTE_DIR="${HAOLEME_UPLOAD_DIR:-/opt/haoleme-cloud-data/downloads}"
PUBLIC_BASE="${HAOLEME_PUBLIC_URL:-http://${SERVER}}"
SSH_TARGET="${SERVER_USER}@${SERVER}"

PYTHON_VERSION="$(
  python3 - <<'PY'
import pathlib
import re
text = pathlib.Path("src/haoleme/__init__.py").read_text(encoding="utf-8")
match = re.search(r'__version__\s*=\s*"([^"]+)"', text)
print(match.group(1) if match else "")
PY
)"
ANDROID_VERSION="$(
  python3 - <<'PY'
import pathlib
import re
text = pathlib.Path("android/app/build.gradle").read_text(encoding="utf-8")
match = re.search(r'versionName\s+"([^"]+)"', text)
print(match.group(1) if match else "")
PY
)"
ANDROID_CODE="$(
  python3 - <<'PY'
import pathlib
import re
text = pathlib.Path("android/app/build.gradle").read_text(encoding="utf-8")
match = re.search(r"versionCode\s+(\d+)", text)
print(match.group(1) if match else "")
PY
)"

if [[ -z "$PYTHON_VERSION" || -z "$ANDROID_VERSION" || -z "$ANDROID_CODE" ]]; then
  echo "Could not read versions from the repo." >&2
  exit 1
fi

mkdir -p dist

WHEEL_PATH="dist/haoleme-${PYTHON_VERSION}-py3-none-any.whl"
APK_PATH="dist/Haoleme-${ANDROID_VERSION}.apk"

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  if [[ "$UPLOAD_PYTHON" -eq 1 ]]; then
    echo "Building Python wheel ${PYTHON_VERSION}..."
    built=0
    for py in ${HAOLEME_BUILD_PYTHON:-} python3 /opt/anaconda3/bin/python; do
      [[ -n "$py" && -x "$py" ]] || continue
      if "$py" -m build --wheel -o dist >/dev/null 2>&1; then
        built=1
        break
      fi
    done
    if [[ "$built" -eq 0 ]]; then
      python3 -m pip install -q build
      python3 -m build --wheel -o dist >/dev/null
    fi
    [[ -f "$WHEEL_PATH" ]] || { echo "Missing wheel: $WHEEL_PATH" >&2; exit 1; }
  fi

  if [[ "$UPLOAD_ANDROID" -eq 1 ]]; then
    if [[ -x android/gradlew ]]; then
      if [[ -n "${JAVA_HOME:-}" ]]; then
        export PATH="$JAVA_HOME/bin:$PATH"
      elif [[ -x /opt/homebrew/opt/openjdk@17/bin/java ]]; then
        export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
        export PATH="$JAVA_HOME/bin:$PATH"
      fi
      if command -v java >/dev/null 2>&1; then
        echo "Building Android APK ${ANDROID_VERSION} (code ${ANDROID_CODE})..."
        (cd android && ./gradlew assembleRelease >/dev/null)
        cp "android/app/build/outputs/apk/release/app-release.apk" "$APK_PATH"
      else
        echo "Java not found; reusing existing APK if present." >&2
      fi
    fi
    [[ -f "$APK_PATH" ]] || { echo "Missing APK: $APK_PATH" >&2; exit 1; }
  fi
fi

python3 - <<'PY' "$ROOT" "$PUBLIC_BASE" "$PYTHON_VERSION" "$ANDROID_VERSION" "$ANDROID_CODE" "${HAOLEME_ANDROID_NOTES:-}" "${HAOLEME_PYTHON_NOTES:-}"
import hashlib
import json
import pathlib
import sys

root, public_base, python_version, android_version, android_code, android_notes, python_notes = sys.argv[1:]
root = pathlib.Path(root)
public_base = public_base.rstrip("/")
apk_path = root / "dist" / f"Haoleme-{android_version}.apk"
wheel_name = f"haoleme-{python_version}-py3-none-any.whl"

existing = {}
update_path = root / "update.json"
if update_path.exists():
    try:
        existing = json.loads(update_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        existing = {}

android = existing.get("android") if isinstance(existing.get("android"), dict) else {}
python = existing.get("python") if isinstance(existing.get("python"), dict) else {}

if apk_path.is_file():
    apk_bytes = apk_path.read_bytes()
    sha256 = hashlib.sha256(apk_bytes).hexdigest()
    github_apk = f"https://github.com/HaolemeApp/Haoleme/releases/latest/download/Haoleme-{android_version}.apk"
    android = {
        "versionCode": int(android_code),
        "versionName": android_version,
        "apkUrl": github_apk,
        "apkUrls": [github_apk, f"{public_base}/downloads/Haoleme-{android_version}.apk"],
        "sha256": sha256,
        "minSupportedVersionCode": int(android.get("minSupportedVersionCode") or 66),
        "forceUpdate": bool(android.get("forceUpdate") or False),
        "notes": android_notes or android.get("notes") or f"Haoleme app {android_version}",
    }

python = {
    "version": python_version,
    "packageUrl": python.get("packageUrl") or "https://pypi.org/project/haoleme/",
    "wheelUrl": f"{public_base}/downloads/{wheel_name}",
    "notes": python_notes or python.get("notes") or f"haoleme CLI {python_version}",
}

payload = {"android": android, "python": python}
text = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
update_path.write_text(text, encoding="utf-8")
(root / "update-example.json").write_text(text, encoding="utf-8")
print(text)
PY

remote_cmd() {
  if [[ -n "${HAOLEME_UPLOAD_PASSWORD:-}" ]] && command -v sshpass >/dev/null 2>&1; then
    SSHPASS="$HAOLEME_UPLOAD_PASSWORD" sshpass -e ssh \
      -o StrictHostKeyChecking=no \
      -o PreferredAuthentications=password \
      -o PubkeyAuthentication=no \
      "$SSH_TARGET" "$@"
  else
    ssh -o StrictHostKeyChecking=no "$SSH_TARGET" "$@"
  fi
}

remote_copy() {
  local src="$1"
  local dest="$2"
  if [[ -n "${HAOLEME_UPLOAD_PASSWORD:-}" ]] && command -v sshpass >/dev/null 2>&1; then
    SSHPASS="$HAOLEME_UPLOAD_PASSWORD" sshpass -e scp \
      -o StrictHostKeyChecking=no \
      -o PreferredAuthentications=password \
      -o PubkeyAuthentication=no \
      "$src" "${SSH_TARGET}:${dest}"
  else
    scp -o StrictHostKeyChecking=no "$src" "${SSH_TARGET}:${dest}"
  fi
}

UPLOAD_PATHS=("$ROOT/update.json")
if [[ "$UPLOAD_PYTHON" -eq 1 ]]; then
  UPLOAD_PATHS+=("$WHEEL_PATH")
fi
if [[ "$UPLOAD_ANDROID" -eq 1 && -f "$APK_PATH" ]]; then
  UPLOAD_PATHS+=("$APK_PATH")
fi

echo "Uploading to ${SSH_TARGET}:${REMOTE_DIR} ..."
for path in "${UPLOAD_PATHS[@]}"; do
  remote_copy "$path" "${REMOTE_DIR}/"
done

remote_copy_cmds=()
if [[ "$UPLOAD_PYTHON" -eq 1 ]]; then
  remote_copy_cmds+=("chown haoleme:haoleme ${REMOTE_DIR}/$(basename "$WHEEL_PATH") ${REMOTE_DIR}/update.json")
  remote_copy_cmds+=("python3.11 -m pip install -U ${REMOTE_DIR}/$(basename "$WHEEL_PATH")")
  remote_copy_cmds+=("systemctl restart haoleme-cloud")
fi
if [[ "$UPLOAD_ANDROID" -eq 1 && -f "$APK_PATH" ]]; then
  remote_copy_cmds+=("chown haoleme:haoleme ${REMOTE_DIR}/$(basename "$APK_PATH")")
fi

if [[ "${#remote_copy_cmds[@]}" -gt 0 ]]; then
  remote_cmd "$(printf '%s && ' "${remote_copy_cmds[@]}") echo 'Server update ok'"
fi

if [[ "$DO_GITHUB" -eq 1 ]] && command -v gh >/dev/null 2>&1; then
  TAG="v${ANDROID_VERSION}"
  if [[ "$UPLOAD_ANDROID" -eq 1 && -f "$APK_PATH" ]]; then
    NOTES="${HAOLEME_ANDROID_NOTES:-Haoleme ${ANDROID_VERSION} (CLI ${PYTHON_VERSION})}"
    gh release view "$TAG" >/dev/null 2>&1 && gh release upload "$TAG" "$APK_PATH" --clobber || \
      gh release create "$TAG" "$APK_PATH" --title "Haoleme ${ANDROID_VERSION}" --notes "$NOTES"
  fi
fi

echo "Done."
echo "Python: ${PYTHON_VERSION} -> ${PUBLIC_BASE}/downloads/$(basename "$WHEEL_PATH")"
if [[ -f "$APK_PATH" ]]; then
  echo "Android: ${ANDROID_VERSION} -> ${PUBLIC_BASE}/downloads/$(basename "$APK_PATH")"
fi
echo "Manifest: ${PUBLIC_BASE}/downloads/update.json"