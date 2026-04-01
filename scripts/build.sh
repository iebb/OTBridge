#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE_DIR="${ROOT_DIR}/module_template"
BUILD_DIR="${ROOT_DIR}/build"
STAGING_DIR="${BUILD_DIR}/staging"
PRIV_APP_DIR="${STAGING_DIR}/system/priv-app/ee.nekoko.nbridge"
PERM_DIR="${STAGING_DIR}/system/etc/permissions"
OUT_ZIP="${BUILD_DIR}/otbridge-magisk-kernelsu.zip"

APK_PATH=""
VERSION="v1.0.0"
VERSION_CODE="1"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk)
      APK_PATH="${2:-}"
      shift 2
      ;;
    --version)
      VERSION="${2:-}"
      shift 2
      ;;
    --version-code)
      VERSION_CODE="${2:-}"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "${APK_PATH}" ]]; then
  APK_PATH="${ROOT_DIR}/app/build/outputs/apk/release/app-release.apk"
  if [[ ! -f "${APK_PATH}" ]]; then
    "${ROOT_DIR}/gradlew" :app:assembleRelease
  fi
fi

if [[ ! -f "${APK_PATH}" ]]; then
  echo "APK not found after build attempt: ${APK_PATH}" >&2
  exit 1
fi

rm -rf "${STAGING_DIR}"
mkdir -p "${PRIV_APP_DIR}" "${PERM_DIR}" "${BUILD_DIR}"

cp "${TEMPLATE_DIR}/customize.sh" "${STAGING_DIR}/customize.sh"
cp "${TEMPLATE_DIR}/system/etc/permissions/privapp-permissions-ee.nekoko.nbridge.xml" \
   "${PERM_DIR}/privapp-permissions-ee.nekoko.nbridge.xml"
cp "${APK_PATH}" "${PRIV_APP_DIR}/NBridge.apk"

sed \
  -e "s/@VERSION@/${VERSION}/g" \
  -e "s/@VERSION_CODE@/${VERSION_CODE}/g" \
  "${TEMPLATE_DIR}/module.prop.in" > "${STAGING_DIR}/module.prop"

(
  cd "${STAGING_DIR}"
  zip -qr "${OUT_ZIP}" .
)

echo "Built ${OUT_ZIP}"
