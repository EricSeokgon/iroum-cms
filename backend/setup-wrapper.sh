#!/usr/bin/env bash
# setup-wrapper.sh — Gradle 8.10 wrapper 초기화 스크립트
#
# 사용법: cd backend && bash setup-wrapper.sh
#
# 전제 조건:
#   - 시스템 PATH에 Java 8+ (또는 17) 설치 필요 (gradle 실행 용도)
#   - 인터넷 접속 필요 (Gradle 배포 ZIP 다운로드)
#
# 이 스크립트가 성공하면 ./gradlew 파일이 생성되고,
# 이후 모든 빌드는 ./gradlew 를 통해 실행하면 된다.
# (Java 17은 Gradle toolchain이 foojay API로 자동 다운로드)

set -euo pipefail

GRADLE_VERSION="8.10"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP_DIR="$(mktemp -d)"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

echo "[setup-wrapper] Gradle ${GRADLE_VERSION} 배포 파일 다운로드 중..."
curl -fsSL \
  "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
  -o "${TMP_DIR}/gradle.zip"

echo "[setup-wrapper] 압축 해제 중..."
unzip -q "${TMP_DIR}/gradle.zip" -d "${TMP_DIR}"

echo "[setup-wrapper] gradlew wrapper 초기화 중..."
"${TMP_DIR}/gradle-${GRADLE_VERSION}/bin/gradle" \
  --project-dir "$SCRIPT_DIR" \
  wrapper \
  --gradle-version "$GRADLE_VERSION" \
  --distribution-type bin

echo "[setup-wrapper] 완료! 이제 다음 명령을 실행하세요:"
echo "  cd ${SCRIPT_DIR} && ./gradlew build"
