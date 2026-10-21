# CLAUDE.md

프로젝트 규칙과 맥락은 AGENTS.md 에 있습니다. (ChatGPT/Codex 등 다른 도구와 같은 문서를 공유하기 위해 분리)

@AGENTS.md

## Claude Code 전용 메모

- 앱 실행은 `healing-room/` 에서 `./gradlew bootRun`. 미리보기용 `.claude/launch.json` 은 git 에서 제외되어 있다.
- DB 마이그레이션(db/*.sql)을 실행할 때는 사용자에게 먼저 알리고, `SET lock_timeout = '5s'` 를 건다.
- `.env` 의 값(비밀번호 등)은 대화나 커밋 메시지에 옮겨 적지 않는다.
- **사용자에게 보이는 모든 글(답변, 도구 쓰기 전 안내, 중간 보고)은 한국어로만 쓴다.** 코드 식별자·명령어·경로만 원문.
- 작업이 끝나면 커밋·푸시까지 하고(`main` 푸시 = 자동 배포), 운영 사이트에서 반영됐는지 확인한다.
- 엔티티를 바꾸는 배포는 마이그레이션을 운영 DB 에 먼저 실행한 뒤 푸시한다 (`ddl-auto=validate`).
- 운영 DB 는 `ssh ai-server` 에서 `shared/.env` 를 읽어 `psql` 로 다룬다(값을 출력하지 않게). 로컬 PC 에는 psql 이 없다.
- 화면 JS 를 고친 뒤에는 `node --check` 로 문법을 확인한다 (문자열 안의 실제 줄바꿈 하나로 로비 전체가 멈춘 적이 있음).
- 커밋 메시지·문서·주석에 욕설·음란어 목록의 단어를 예시로 쓰지 않는다(공개 저장소). 예시는 `ProfanityFilterTests` 와 목록 파일에만.
