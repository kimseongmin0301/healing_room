#!/usr/bin/env bash
# 새 버전 배포: 내 PC 에서 빌드 → 서버에 새 release 로 올림 → current 링크 교체 → 서비스 재시작 → 확인
#
#   bash deploy/deploy.sh            # 저장소 루트에서 실행
#   bash deploy/deploy.sh rollback   # 바로 이전 release 로 되돌리기
#
# 필요: ~/.ssh/config 의 Host ai-server, 서버에 install-root.sh --allow-restart 를 한 번 실행해 둔 상태
set -euo pipefail

HOST=ai-server
APP_DIR=healing-room
KEEP=5   # 서버에 남겨 둘 release 수

restart_and_check() {
  ssh "$HOST" 'sudo -n /usr/bin/systemctl restart healing-room \
    && for i in $(seq 1 30); do ss -ltn | grep -q ":18280 " && break; sleep 2; done \
    && systemctl is-active healing-room \
    && python3 -c "import urllib.request; print(\"API\", urllib.request.urlopen(\"http://127.0.0.1:18280/api/themes\", timeout=5).status)"'
}

if [ "${1:-}" = "rollback" ]; then
  ssh "$HOST" "cd ~/$APP_DIR && prev=\$(ls -1 releases | sort | tail -2 | head -1) && ln -sfn releases/\$prev current && echo \"되돌림: \$prev\""
  restart_and_check
  exit 0
fi

cd "$(dirname "$0")/../healing-room"
if [ -n "$(git status --porcelain)" ]; then
  echo "커밋하지 않은 변경이 있어요. 배포할 내용을 먼저 커밋하세요."; exit 1
fi
./gradlew clean test bootJar --console=plain -q

release="$(date +%Y%m%d%H%M%S)-$(git rev-parse --short HEAD)"
ssh "$HOST" "mkdir -p ~/$APP_DIR/releases/$release"
scp -q build/libs/healing-room-*.jar "$HOST:$APP_DIR/releases/$release/healing-room.jar"
ssh "$HOST" "cd ~/$APP_DIR && ln -sfn releases/$release current \
  && ls -1 releases | sort | head -n -$KEEP | xargs -r -I{} rm -rf releases/{}"
echo "release: $release"
restart_and_check
