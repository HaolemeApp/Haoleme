#!/usr/bin/env bash
# Build and upload Haoleme Python wheel and/or Android APK to the cloud server.
#
# Usage:
#   ./deploy/upload-release.sh              # upload python + android (if APK exists or can be built)
#   ./deploy/upload-release.sh --python     # python wheel + cloud package only
#   ./deploy/upload-release.sh --android    # android APK only
#   ./deploy/upload-release.sh --github     # also create a GitHub release
#   ./deploy/upload-release.sh --pypi       # also upload wheel + sdist to PyPI
#
# Auth (pick one):
#   export HAOLEME_UPLOAD_PASSWORD='...'
#   ssh root@api.haoleme.cloud   # when SSH keys are configured
#
# PyPI (with --pypi or when HAOLEME_PYPI_TOKEN is set during python upload):
#   export HAOLEME_PYPI_TOKEN='pypi-...'
#
# Optional env:
#   HAOLEME_UPLOAD_HOST=api.haoleme.cloud
#   HAOLEME_UPLOAD_USER=root
#   HAOLEME_PUBLIC_URL=https://api.haoleme.cloud
#   HAOLEME_UPLOAD_DIRS="/opt/haoleme-cloud-data/downloads /opt/reminder-cloud-data/downloads"
#   HAOLEME_ANDROID_NOTES="Release notes for the app"
#   HAOLEME_PYTHON_NOTES="Release notes for hao CLI"
#   JAVA_HOME=/path/to/jdk

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

UPLOAD_PYTHON=1
UPLOAD_ANDROID=1
DO_GITHUB=0
UPLOAD_PYPI=0
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
    --pypi)
      UPLOAD_PYPI=1
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

SERVER="${HAOLEME_UPLOAD_HOST:-api.haoleme.cloud}"
SERVER_USER="${HAOLEME_UPLOAD_USER:-root}"
REMOTE_DIRS_RAW="${HAOLEME_UPLOAD_DIRS:-${HAOLEME_UPLOAD_DIR:-/opt/haoleme-cloud-data/downloads /opt/reminder-cloud-data/downloads}}"
PUBLIC_BASE="${HAOLEME_PUBLIC_URL:-https://api.haoleme.cloud}"
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
SDIST_PATH="dist/haoleme-${PYTHON_VERSION}.tar.gz"
APK_PATH="dist/Haoleme-${ANDROID_VERSION}.apk"

if [[ "$UPLOAD_PYPI" -eq 1 && "$UPLOAD_PYTHON" -eq 0 ]]; then
  echo "--pypi requires a python build/upload." >&2
  exit 2
fi

if [[ -n "${HAOLEME_PYPI_TOKEN:-}" ]]; then
  UPLOAD_PYPI=1
fi

build_python_artifacts() {
  echo "Building Python wheel + sdist ${PYTHON_VERSION}..."
  local built=0
  for py in ${HAOLEME_BUILD_PYTHON:-} /opt/anaconda3/bin/python python3; do
    [[ -n "$py" && -x "$py" ]] || continue
    if "$py" -m build -o dist >/dev/null 2>&1; then
      built=1
      break
    fi
  done
  if [[ "$built" -eq 0 ]]; then
    local venv="${ROOT}/.venv-build"
    if [[ ! -x "${venv}/bin/python" ]]; then
      python3 -m venv "$venv"
      "${venv}/bin/pip" install -q build twine
    fi
    "${venv}/bin/python" -m build -o dist >/dev/null
  fi
  [[ -f "$WHEEL_PATH" ]] || { echo "Missing wheel: $WHEEL_PATH" >&2; exit 1; }
  if [[ "$UPLOAD_PYPI" -eq 1 ]]; then
    [[ -f "$SDIST_PATH" ]] || { echo "Missing sdist: $SDIST_PATH" >&2; exit 1; }
  fi
}

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  if [[ "$UPLOAD_PYTHON" -eq 1 ]]; then
    build_python_artifacts
  fi

  if [[ "$UPLOAD_ANDROID" -eq 1 ]]; then
    if [[ -x android/gradlew ]]; then
      if [[ -n "${JAVA_HOME:-}" ]]; then
        export PATH="$JAVA_HOME/bin:$PATH"
      elif [[ -x /opt/homebrew/opt/openjdk@17/bin/java ]]; then
        export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
        export PATH="$JAVA_HOME/bin:$PATH"
      fi
      KT=""
      if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/keytool" ]]; then
        KT="$JAVA_HOME/bin/keytool"
      elif [[ -x /opt/homebrew/opt/openjdk@17/bin/keytool ]]; then
        KT="/opt/homebrew/opt/openjdk@17/bin/keytool"
      elif command -v keytool >/dev/null 2>&1; then
        KT="$(command -v keytool)"
      fi
      if command -v java >/dev/null 2>&1; then
        echo "Building Android (clean) ${ANDROID_VERSION} (code ${ANDROID_CODE}) with fixed signing..."
        (cd android && ./gradlew clean assembleRelease lintVitalRelease)
        cp "android/app/build/outputs/apk/release/app-release.apk" "$APK_PATH"
        # Verify that the produced APK uses exactly the legacy keystore cert (prevents signature drift)
        echo "Verifying APK signature matches legacy/debug.keystore ..."
        LEGACY_SHA1="$("$KT" -list -v -keystore "$ROOT/android/legacy/debug.keystore" -storepass android 2>/dev/null | awk '/SHA1:/ {print $2; exit}')"
        APK_CERT_SHA1="$(unzip -p "$APK_PATH" META-INF/CERT.RSA 2>/dev/null | "$KT" -printcert 2>/dev/null | awk '/SHA1:/ {print $2; exit}')"
        if [[ -z "$LEGACY_SHA1" || -z "$APK_CERT_SHA1" ]]; then
          echo "WARNING: Could not extract one of the cert fingerprints for verification." >&2
        elif [[ "$LEGACY_SHA1" != "$APK_CERT_SHA1" ]]; then
          echo "ERROR: APK was signed with $APK_CERT_SHA1 but legacy is $LEGACY_SHA1" >&2
          echo "This would cause 'different app / developer signature' errors on upgrade. Aborting." >&2
          exit 1
        else
          echo "OK: APK signed with fixed legacy key SHA1=$LEGACY_SHA1"
        fi
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
    github_apk = f"https://github.com/HaolemeApp/Haoleme/releases/download/v{android_version}/Haoleme-{android_version}.apk"
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

read -r -a REMOTE_DIRS <<< "$REMOTE_DIRS_RAW"
echo "Uploading to ${SSH_TARGET}:${REMOTE_DIRS[*]} ..."
set +e
for dir in "${REMOTE_DIRS[@]}"; do
  remote_cmd "mkdir -p '$dir'" || true
  for path in "${UPLOAD_PATHS[@]}"; do
    remote_copy "$path" "${dir}/" || echo "  (server copy failed for $(basename "$path") -> ${dir}, continuing...)"
  done
done

remote_copy_cmds=()
for dir in "${REMOTE_DIRS[@]}"; do
  remote_copy_cmds+=("chown -R haoleme:haoleme ${dir} 2>/dev/null || true")
  remote_copy_cmds+=("chmod 0644 ${dir}/update.json 2>/dev/null || true")
  if [[ "$UPLOAD_PYTHON" -eq 1 ]]; then
    remote_copy_cmds+=("chmod 0644 ${dir}/$(basename "$WHEEL_PATH") 2>/dev/null || true")
  fi
  if [[ "$UPLOAD_ANDROID" -eq 1 && -f "$APK_PATH" ]]; then
    remote_copy_cmds+=("chmod 0644 ${dir}/$(basename "$APK_PATH") 2>/dev/null || true")
  fi
done
if [[ "$UPLOAD_PYTHON" -eq 1 ]]; then
  remote_copy_cmds+=("python3.11 -m pip install -U ${REMOTE_DIRS[0]}/$(basename "$WHEEL_PATH")")
  remote_copy_cmds+=("systemctl restart haoleme-cloud")
fi

if [[ "${#remote_copy_cmds[@]}" -gt 0 ]]; then
  remote_cmd "$(printf '%s && ' "${remote_copy_cmds[@]}") echo 'Server update ok'" || echo "  (server post-commands failed or no auth, continuing to GitHub...)"
fi
set -e

if [[ "$UPLOAD_PYPI" -eq 1 ]]; then
  if [[ -z "${HAOLEME_PYPI_TOKEN:-}" ]]; then
    echo "HAOLEME_PYPI_TOKEN is required for PyPI upload." >&2
    exit 1
  fi
  PYPI_TOKEN="$HAOLEME_PYPI_TOKEN"
  if [[ "$PYPI_TOKEN" != pypi-* ]]; then
    PYPI_TOKEN="pypi-${PYPI_TOKEN}"
  fi
  twine_py=""
  for py in ${HAOLEME_BUILD_PYTHON:-} /opt/anaconda3/bin/python "${ROOT}/.venv-build/bin/python" python3; do
    [[ -n "$py" && -x "$py" ]] || continue
    if "$py" -m twine --version >/dev/null 2>&1; then
      twine_py="$py"
      break
    fi
  done
  if [[ -z "$twine_py" ]]; then
    venv="${ROOT}/.venv-build"
    if [[ ! -x "${venv}/bin/python" ]]; then
      python3 -m venv "$venv"
    fi
    "${venv}/bin/pip" install -q twine
    twine_py="${venv}/bin/python"
  fi
  echo "Uploading ${PYTHON_VERSION} to PyPI..."
  TWINE_USERNAME=__token__ TWINE_PASSWORD="$PYPI_TOKEN" \
    "$twine_py" -m twine upload "$WHEEL_PATH" "$SDIST_PATH"
fi

if [[ "$DO_GITHUB" -eq 1 ]] && command -v gh >/dev/null 2>&1; then
  TAG="v${ANDROID_VERSION}"
  if [[ "$UPLOAD_ANDROID" -eq 1 && -f "$APK_PATH" ]]; then
    NOTES="${HAOLEME_ANDROID_NOTES:-Haoleme ${ANDROID_VERSION} (CLI ${PYTHON_VERSION})}"
    if gh release view "$TAG" >/dev/null 2>&1; then
      gh release upload "$TAG" "$APK_PATH" "$ROOT/update.json" --clobber
    else
      gh release create "$TAG" "$APK_PATH" "$ROOT/update.json" --title "Haoleme ${ANDROID_VERSION}" --notes "$NOTES"
    fi
  elif [[ "$UPLOAD_PYTHON" -eq 1 ]]; then
    TAG="v${ANDROID_VERSION}"
    gh release view "$TAG" >/dev/null 2>&1 && gh release upload "$TAG" "$WHEEL_PATH" "$ROOT/update.json" --clobber || true
  fi
fi

echo "Verifying public release chain..."
python3 - <<'PY' "$PUBLIC_BASE" "$ANDROID_VERSION" "$ANDROID_CODE" "$PYTHON_VERSION" "$APK_PATH"
import hashlib
import json
import pathlib
import sys
import urllib.request

public_base, android_version, android_code, python_version, apk_path = sys.argv[1:]
manifest_url = public_base.rstrip("/") + "/downloads/update.json"
with urllib.request.urlopen(manifest_url, timeout=12) as resp:
    manifest = json.loads(resp.read().decode("utf-8"))
android = manifest.get("android") or {}
python = manifest.get("python") or {}
if int(android.get("versionCode") or 0) != int(android_code):
    raise SystemExit(f"cloud android versionCode mismatch: {android.get('versionCode')} != {android_code}")
if str(android.get("versionName") or "") != android_version:
    raise SystemExit(f"cloud android versionName mismatch: {android.get('versionName')} != {android_version}")
if str(python.get("version") or "") != python_version:
    raise SystemExit(f"cloud python version mismatch: {python.get('version')} != {python_version}")
apk = pathlib.Path(apk_path)
if apk.is_file():
    expected = hashlib.sha256(apk.read_bytes()).hexdigest()
    if str(android.get("sha256") or "").lower() != expected:
        raise SystemExit("cloud APK sha256 mismatch")
    with urllib.request.urlopen(public_base.rstrip("/") + f"/downloads/Haoleme-{android_version}.apk", timeout=12) as resp:
        if resp.status != 200:
            raise SystemExit(f"cloud APK unavailable: HTTP {resp.status}")
print(f"OK: {manifest_url} -> Android {android_version}, Python {python_version}")
PY

echo "Done."
if [[ "$UPLOAD_PYPI" -eq 1 ]]; then
  echo "PyPI: https://pypi.org/project/haoleme/${PYTHON_VERSION}/"
fi
echo "Python: ${PYTHON_VERSION} -> ${PUBLIC_BASE}/downloads/$(basename "$WHEEL_PATH")"
if [[ -f "$APK_PATH" ]]; then
  echo "Android: ${ANDROID_VERSION} -> ${PUBLIC_BASE}/downloads/$(basename "$APK_PATH")"
fi
echo "Manifest: ${PUBLIC_BASE}/downloads/update.json"
