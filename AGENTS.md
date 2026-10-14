# AGENTS.md: AI 작업 가이드

이 문서는 AI 코딩 도구(Claude Code, ChatGPT/Codex, Cursor 등)가 이 저장소에서 작업할 때 따라야 할 규칙과 맥락입니다.
사람이 읽어도 프로젝트 전체를 파악할 수 있게 작성했습니다.

> **ChatGPT 웹에서 사용할 때:** 이 파일과 `README.md`를 대화에 첨부하거나 붙여넣고 시작하세요.
> **Claude Code:** `CLAUDE.md`가 이 파일을 자동으로 불러옵니다.
> **Codex CLI:** 저장소 루트의 `AGENTS.md`를 자동으로 읽습니다.

---

## 1. 프로젝트 한 줄 요약

**힐링룸(Healing Room)**: 불멍, 바다멍 같은 테마 방에 들어가 실사 배경, 움직이는 효과, 환경음, YouTube 음악으로 쉬는 웹 서비스입니다.
**로그인이 없습니다.** 누구나 익명으로 들어와 같은 방에 함께 있을 수 있어요(현재 인원 표시).

## 2. 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 백엔드 | Java 17, Spring Boot 4.1, Gradle(wrapper), Spring Web MVC, Spring Data JPA, Spring Data Redis |
| DB | PostgreSQL 16 (DB 이름 `healing_room`) |
| 캐시·실시간 | Redis 7 (DB 번호 1, 키 접두어 `healing-room:`) |
| 프론트엔드 | 순수 HTML/CSS/JavaScript (ES Modules, 번들러 없음), Canvas 2D |
| 테스트 | JUnit 5, MockMvc, Mockito (`@WebMvcTest`, `@MockitoBean`) |

## 3. 폴더 구조

```
/                          ← 저장소 루트
├─ AGENTS.md               ← 이 파일 (AI 공통 가이드)
├─ CLAUDE.md               ← Claude Code 전용 (AGENTS.md 를 불러옴)
├─ README.md               ← 사람용 소개·실행 방법
├─ design/                 ← 디자인 시안 (SVG/PNG), 초기 프로토타입(live/)
└─ healing-room/           ← Spring Boot 애플리케이션 (Gradle 프로젝트 루트)
   ├─ .env.example         ← 환경변수 형식 (.env 는 git 제외)
   ├─ db/                  ← DB 스키마 SQL (수동 실행, 아래 5절 참고)
   └─ src/main/
      ├─ java/com/healingroom/
      │  ├─ room/          ← 방: Room(엔티티), RoomRepository, RoomService, RoomController, RoomResponse(DTO)
      │  └─ presence/      ← 현재 인원: PresenceService (Redis Sorted Set)
      └─ resources/
         ├─ application.properties
         └─ static/        ← 프론트엔드 (서버가 그대로 서빙)
            ├─ index.html, css/style.css, images/
            └─ js/
               ├─ app.js        ← 진입점: /api/rooms 호출, 탭 생성, 애니메이션 루프
               ├─ scene.js      ← 캔버스 공통 도구 (cover 맞춤, 좌표 변환, wobble, glow)
               ├─ presence.js   ← 인원 heartbeat
               └─ effects/{effect}.js ← 방별 시각 효과
```

## 4. 실행 · 테스트 명령

모든 Gradle 명령은 `healing-room/` 폴더에서 실행합니다.

```bash
cd healing-room
./gradlew bootRun     # 실행 → http://localhost:8080  (Windows: gradlew.bat bootRun)
./gradlew test        # 테스트
```

- `.env`는 `healing-room/.env`에 둡니다. `spring.config.import=optional:file:.env[.properties]`로 읽으므로 **작업 디렉터리가 `healing-room/`이어야** 합니다.
- 포트는 환경변수 `PORT`가 있으면 그 값, 없으면 8080입니다.
- `HealingRoomApplicationTests`(@SpringBootTest)는 **실제 DB와 Redis에 접속**해서 스키마를 검증합니다(읽기만 함). `.env`가 없으면 실패해요.

## 5. ⚠️ DB 규칙 (반드시 지킬 것)

1. **애플리케이션이 DB 스키마를 만들거나 바꾸면 안 됩니다.**
   - `spring.jpa.hibernate.ddl-auto=validate`를 유지하세요. `create`, `create-drop`, `update`로 바꾸지 마세요.
   - `spring.sql.init.mode=never`를 유지하세요. `schema.sql`이나 `data.sql`을 `src/main/resources`에 두지 마세요.
   - Flyway, Liquibase처럼 **실행할 때 자동으로 마이그레이션하는 도구를 추가하지 마세요.**
2. 스키마 변경은 `healing-room/db/V{번호}__{설명}.sql`을 **새 파일로 추가**하고 사람이 직접 실행합니다. 이미 실행한 파일은 수정하지 않습니다.
3. 마이그레이션을 실행할 때는 `SET lock_timeout = '5s'`를 먼저 걸어서, 다른 세션이 잠금을 잡고 있어도 서비스 전체가 멈추지 않게 합니다.
4. 엔티티를 바꿨다면 SQL도 같이 추가해야 합니다. 안 그러면 `validate`에서 앱이 시작되지 않아요.
5. 앱은 `DB_USERNAME` 계정만 씁니다. `DB_ADMIN_*`(관리자)는 DB 생성 같은 수동 작업용이고, 코드나 설정에서 참조하면 안 됩니다.
6. 엔티티를 API로 그대로 노출하지 말고 `record` DTO(예: `RoomResponse`)로 변환하세요. `open-in-view=false`입니다.

### 마이그레이션 현황
| 파일 | 내용 | 적용 |
|---|---|---|
| `V1__create_room.sql` | room 테이블 | ✅ |
| `V2__seed_room.sql` | 기본 방 2개 (cabin, sea) | ✅ |
| `V3__ambient_playlist_video.sql` | 배경 영상 컬럼, room_ambient, room_playlist | ⏳ **미적용**: 다른 세션의 잠금 때문에 보류. 적용 후 이 표를 갱신할 것 |

## 6. ⚠️ Redis 규칙

- Redis는 **다른 서비스와 공유**합니다. 그 서비스가 DB 0번과 5번을 쓰고 있어요.
- 이 앱은 **DB 1번**(`REDIS_DATABASE`)만 쓰고, **모든 키 앞에 `healing-room:`** (`healing-room.redis.key-prefix`)를 붙입니다.
- `FLUSHDB`, `FLUSHALL`, `KEYS *` 같은 전체 대상 명령은 절대 쓰지 마세요.
- Redis가 실패해도 핵심 기능(방 보기)은 동작해야 합니다. Redis 호출부는 `DataAccessException`을 잡아서 503이나 기본값으로 처리하세요.
- 알려진 이슈: 현재 `.env`의 Redis 계정은 ACL 때문에 `healing-room:*` 키에 쓰기 권한이 없습니다(`NOPERM`). 전용 계정(`~healing-room:*`)이 생기면 `.env`만 바꾸면 됩니다.

## 7. 환경변수 (`healing-room/.env`)

| 키 | 설명 |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | PostgreSQL 접속 정보 (`DB_NAME=healing_room`) |
| `DB_USERNAME`, `DB_PASSWORD` | 앱 계정 (healing_room DB 소유자) |
| `DB_ADMIN_USERNAME`, `DB_ADMIN_PASSWORD` | 관리자 계정, 수동 작업 전용 (앱에서 사용 금지) |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD` | Redis 접속 정보 |
| `REDIS_DATABASE` | Redis DB 번호 (기본 1) |

`.env`는 절대 커밋하지 마세요. 새 키를 추가하면 `.env.example`도 함께 고치세요.

## 8. API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/rooms` | 방 목록 (sort_order 순) |
| GET | `/api/rooms/{id}` | 방 하나 (없으면 404) |
| PUT | `/api/rooms/{id}/presence/{visitorId}` | "방에 있음" 신호 → `{ "count": n }`. visitorId 는 UUID만 허용, Redis 실패 시 503 |
| DELETE | `/api/rooms/{id}/presence/{visitorId}` | 방에서 나감 |

## 9. 프론트엔드 규칙

- 번들러나 프레임워크 없이 **ES Modules**로 작성합니다. 외부 라이브러리는 꼭 필요할 때만 쓰세요.
- **효과 모듈 인터페이스** (`static/js/effects/{effect}.js`):
  ```js
  export function create(scene) {
    return {
      draw(t) { /* 매 프레임. 배경은 이미 그려져 있음 */ },
      click(u, v) { /* 클릭 위치 (이미지 기준 0~1 좌표) */ },
    };
  }
  ```
- 좌표는 **배경 이미지 기준 0~1 비율(u, v)**로 쓰고, `scene.P(u, v)`로 화면 좌표로 바꿉니다. 크기는 `scene.k`(원본 대비 배율)를 곱해서 화면 크기에 맞춥니다.
- 방을 추가하는 순서: ① `static/images/`에 배경 추가 → ② `db/V{n}__...sql`로 room 행 추가 → ③ `effects/{effect}.js` 작성
- 브라우저 저장소(localStorage)는 try/catch로 감싸세요(시크릿 모드 대비).
- YouTube는 **IFrame Player API**를 씁니다(API 키 불필요). 이용약관상 **재생 중인 플레이어를 숨기면 안 되고 최소 200×200px**이어야 해요.

## 10. 코드 스타일

- 주석과 사용자에게 보이는 문구는 **한국어**로 씁니다.
- Java: 생성자 주입, DTO는 `record`, 패키지는 기능 단위(`room`, `presence` 등)로 나눕니다.
- 주변 코드의 스타일과 주석 밀도를 따르세요.
- 기능을 추가하면 `RoomControllerTests`처럼 `@WebMvcTest` + `@MockitoBean` 테스트를 함께 작성하세요. 이 테스트는 DB 없이 돌아갑니다.

## 11. 로드맵 / 진행 상황

- [x] 방 2개 (불멍, 바다멍), 실사 배경 + 캔버스 효과
- [x] 방 정보를 PostgreSQL에서 읽기
- [x] 현재 인원 표시 (코드 완료, Redis 권한 대기)
- [ ] 환경음 믹서 (Web Audio로 소리 합성: fire, wind, rain, waves, crickets)
- [ ] 방별 YouTube 플레이리스트 (DB 기본 목록 + 방문자 개인 링크는 localStorage)
- [ ] 배경 영상 지원 (`room.background_video`)
- [ ] 방 추가: 밤하늘, 숲, 모래사장, 클럽
- [ ] 로비 화면 (방 카드와 실시간 인원)
- 로그인 기능은 **만들지 않습니다** (익명 서비스).
