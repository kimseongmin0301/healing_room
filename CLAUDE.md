# CLAUDE.md

프로젝트 규칙과 맥락은 AGENTS.md 에 있습니다. (ChatGPT/Codex 등 다른 도구와 같은 문서를 공유하기 위해 분리)

@AGENTS.md

## Claude Code 전용 메모

- 앱 실행은 `healing-room/` 에서 `./gradlew bootRun`. 미리보기용 `.claude/launch.json` 은 git 에서 제외되어 있다.
- DB 마이그레이션(db/*.sql)을 실행할 때는 사용자에게 먼저 알리고, `SET lock_timeout = '5s'` 를 건다.
- `.env` 의 값(비밀번호 등)은 대화나 커밋 메시지에 옮겨 적지 않는다.
