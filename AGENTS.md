# AGENTS.md: AI 작업 가이드

이 문서는 AI 코딩 도구(Claude Code, ChatGPT/Codex, Cursor 등)가 이 저장소에서 작업할 때 따라야 할 규칙과 맥락입니다.
사람이 읽어도 프로젝트 전체를 파악할 수 있게 작성했습니다.

> **ChatGPT 웹에서 사용할 때:** 이 파일과 `README.md`를 대화에 첨부하거나 붙여넣고 시작하세요.
> **Claude Code:** `CLAUDE.md`가 이 파일을 자동으로 불러옵니다.
> **Codex CLI:** 저장소 루트의 `AGENTS.md`를 자동으로 읽습니다.

---

## 1. 프로젝트 한 줄 요약

**힐링룸(Healing Room)**: 불멍, 바다멍 같은 테마 방에 들어가 실사 배경, 움직이는 효과, 환경음, **함께 듣는 YouTube 음악**으로 쉬는 웹 서비스입니다.

- **로그인이 없습니다(만들지 않음).** 브라우저마다 UUID(방문자 id)를 만들어 localStorage에 두고 씁니다.
- 같은 방에 있는 사람은 **같은 곡을 같은 위치로** 듣습니다(라디오처럼 동기화).
- 누구나 YouTube 링크로 곡을 신청할 수 있습니다(개수 제한 없음). 대기열이 비면 방 기본 음악이 순서대로 나옵니다.
- 다른 사람이 신청한 곡을 넘기거나 빼려면 **방 인원의 50%를 넘는(과반) 투표**가 필요합니다. 내가 신청한 곡은 바로 넘기거나 뺄 수 있어요.

## 2. 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 백엔드 | Java 17, Spring Boot 4.1, Gradle(wrapper), Spring Web MVC, Spring Data JPA, Spring WebSocket |
| DB | PostgreSQL 16 (DB 이름 `healing_room`) |
| 실시간 | WebSocket (`/ws/rooms/{roomId}`). 접속자 목록은 서버 메모리에 보관 |
| 프론트엔드 | 순수 HTML/CSS/JavaScript (ES Modules, 번들러 없음), Canvas 2D, Web Audio, YouTube IFrame Player API |
| 테스트 | JUnit 5, MockMvc, Mockito (`@WebMvcTest`, `@MockitoBean`) |
| Redis | **현재 사용하지 않음.** 서버를 여러 대로 늘릴 때 접속자 공유·이벤트 전달용으로 쓸 예정 (6절) |

## 3. 폴더 구조 (도메인별 패키지)

백엔드는 **도메인마다 controller / service / repository / entity / dto 를 두는 구조**입니다.
도메인 사이에 공통으로 쓰는 설정과 예외 처리는 `global`에 둡니다.

```
/                                   ← 저장소 루트
├─ AGENTS.md / CLAUDE.md / README.md
├─ design/                          ← 디자인 시안(SVG/PNG), 초기 프로토타입(live/)
└─ healing-room/                    ← Spring Boot 앱 (Gradle 프로젝트 루트)
   ├─ .env.example                  ← 환경변수 형식 (.env 는 git 제외)
   ├─ db/                           ← DB 스키마 SQL (수동 실행, 5절)
   └─ src/main/java/com/healingroom/
      ├─ HealingRoomApplication.java
      ├─ domain/
      │  ├─ room/                   ← 방
      │  │  ├─ controller/          RoomController           GET /api/rooms, /api/rooms/{id}
      │  │  ├─ service/             RoomService
      │  │  ├─ repository/          RoomRepository, RoomAmbientRepository
      │  │  ├─ entity/              Room, RoomAmbient
      │  │  └─ dto/                 RoomResponse, RoomDetailResponse
      │  ├─ queue/                  ← 함께 듣는 음악 (대기열·투표)
      │  │  ├─ controller/          QueueController          /api/rooms/{roomId}/queue/**
      │  │  ├─ service/             QueueService(핵심 로직), VotePolicy(과반 규칙)
      │  │  ├─ repository/          QueueItemRepository, QueueVoteRepository, DefaultTrackRepository
      │  │  ├─ entity/              QueueItem, QueueVote, QueueStatus, DefaultTrack
      │  │  ├─ dto/                 PlayerState, QueueHistoryResponse, QueueAddRequest, DurationRequest
      │  │  ├─ event/               QueueChangedEvent (상태가 바뀌면 발행 → WebSocket 으로 전송)
      │  │  ├─ exception/           QueueException (화면에 보여줄 메시지 포함)
      │  │  ├─ client/              YouTubeOEmbedClient (영상 제목 조회, API 키 불필요)
      │  │  └─ util/                YouTubeLinks (링크 → 영상 id)
      │  └─ presence/               ← 방 접속(누가 어느 방에 있는지)
      │     ├─ handler/             RoomSocketHandler (WebSocket 연결/해제, 상태 전송)
      │     └─ service/             RoomSessionRegistry (방별 접속자, 메모리)
      └─ global/
         ├─ config/                 WebSocketConfig
         └─ exception/              GlobalExceptionHandler (@RestControllerAdvice → { "message": ... })

   src/main/resources/
      ├─ application.properties
      └─ static/                    ← 프론트엔드 (서버가 그대로 서빙)
         ├─ index.html, css/style.css, images/
         └─ js/
            ├─ app.js               진입점: #방id 라우팅(로비 ↔ 방), 애니메이션 루프
            ├─ api.js               fetch 래퍼, 방문자 id(UUID), localStorage 도우미
            ├─ lobby.js             로비 방 카드 + 인원
            ├─ room-socket.js       WebSocket 연결·재연결
            ├─ music.js             YouTube 플레이어 동기화, 대기열/지난 신청곡/투표 UI, 창 최소화·최대화
            ├─ scene.js             캔버스 공통 도구 (cover 맞춤, 좌표 변환, wobble, glow, 배경 영상)
            ├─ audio/sounds.js      환경음 합성 (fire, wind, rain, waves, crickets)
            ├─ audio/mixer.js       환경음 믹서 UI
            └─ effects/{effect}.js  방별 시각 효과
```

테스트도 같은 패키지 구조를 따릅니다 (`src/test/java/com/healingroom/domain/...`).

**새 도메인을 추가할 때**는 `domain/{이름}/` 아래에 필요한 계층(controller, service, repository, entity, dto)만 만드세요.
다른 도메인의 entity 를 컨트롤러에서 직접 쓰지 말고 service 를 통해 접근합니다.

## 4. 실행 · 테스트 명령

모든 Gradle 명령은 `healing-room/` 폴더에서 실행합니다.

```bash
cd healing-room
./gradlew bootRun     # 실행 → http://localhost:8080  (Windows: gradlew.bat bootRun)
./gradlew test        # 테스트
```

- `.env`는 `healing-room/.env`에 둡니다. `spring.config.import=optional:file:.env[.properties]`로 읽으므로 **작업 디렉터리가 `healing-room/`이어야** 합니다.
- 포트는 환경변수 `PORT`가 있으면 그 값, 없으면 8080입니다.
- 같은 네트워크의 다른 기기에서는 `http://{PC의 IP}:8080`으로 접속합니다. **HTTPS가 아닌 IP 접속에서는 `crypto.randomUUID()` 같은 보안 컨텍스트 전용 API가 없으니** 프론트엔드에서 쓰지 마세요(`api.js`의 `uuid()` 참고).
- `HealingRoomApplicationTests`(@SpringBootTest)는 **실제 DB에 접속**해서 엔티티와 테이블이 맞는지 검증합니다(읽기만 함). `.env`가 없으면 실패해요.
- **서버는 한 대만 띄우세요.** 대기열 진행(스케줄러)과 접속자 목록이 서버 메모리 기준이라, 같은 DB에 서버 두 대를 붙이면 곡이 두 번 넘어가는 등 꼬입니다.

## 5. ⚠️ DB 규칙 (반드시 지킬 것)

1. **애플리케이션이 DB 스키마를 만들거나 바꾸면 안 됩니다.**
   - `spring.jpa.hibernate.ddl-auto=validate`를 유지하세요. `create`, `create-drop`, `update`로 바꾸지 마세요.
   - `spring.sql.init.mode=never`를 유지하세요. `schema.sql`이나 `data.sql`을 `src/main/resources`에 두지 마세요.
   - Flyway, Liquibase처럼 **실행할 때 자동으로 마이그레이션하는 도구를 추가하지 마세요.**
2. 스키마 변경은 `healing-room/db/V{번호}__{설명}.sql`을 **새 파일로 추가**하고 사람이 직접 실행합니다. 이미 실행한 파일은 수정하지 않습니다.
3. 마이그레이션을 실행할 때는 `SET lock_timeout = '5s'`를 먼저 걸어서, 다른 세션(DBeaver 등)이 잠금을 잡고 있어도 서비스 전체가 멈추지 않게 합니다.
4. 엔티티를 바꿨다면 SQL도 같이 추가해야 합니다. 안 그러면 `validate`에서 앱이 시작되지 않아요.
5. 앱은 `DB_USERNAME` 계정만 씁니다. `DB_ADMIN_*`(관리자)는 DB 생성 같은 수동 작업용이고, 코드나 설정에서 참조하면 안 됩니다.
6. 엔티티를 API로 그대로 노출하지 말고 `record` DTO로 변환하세요. `open-in-view=false`입니다.

### 마이그레이션 현황
| 파일 | 내용 | 적용 |
|---|---|---|
| `V1__create_room.sql` | room 테이블 | ✅ |
| `V2__seed_room.sql` | 기본 방 2개 (cabin, sea) | ✅ |
| `V3__ambient_playlist_video.sql` | 배경 영상 컬럼, room_ambient, room_playlist(기본 음악), room_queue_item, room_queue_vote | ✅ |
| `V4__replace_default_tracks.sql` | 기본 음악 교체 (임베드 가능한 영상으로) | ✅ |

## 6. Redis (현재 미사용)

- 처음에는 현재 인원 표시에 Redis를 썼지만, `.env`의 Redis 계정이 ACL 때문에 이 앱의 키에 쓰기 권한이 없어서(`NOPERM`) **WebSocket 접속자(서버 메모리) 방식으로 바꿨습니다.** 지금 앱은 Redis에 접속하지 않습니다.
- 서버를 여러 대로 늘릴 때 다시 도입합니다. 그때 지킬 것:
  - Redis는 **다른 서비스와 공유**합니다(그 서비스가 DB 0번, 5번 사용). 이 앱은 **DB 1번**, **키 접두어 `healing-room:`**만 씁니다.
  - `FLUSHDB`, `FLUSHALL`, `KEYS *` 같은 전체 대상 명령은 절대 쓰지 마세요.
  - 전용 계정(`~healing-room:*` 권한)을 먼저 만들어야 합니다.

## 7. 환경변수 (`healing-room/.env`)

| 키 | 설명 |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | PostgreSQL 접속 정보 (`DB_NAME=healing_room`) |
| `DB_USERNAME`, `DB_PASSWORD` | 앱 계정 (healing_room DB 소유자) |
| `DB_ADMIN_USERNAME`, `DB_ADMIN_PASSWORD` | 관리자 계정, 수동 작업 전용 (앱에서 사용 금지) |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD`, `REDIS_DATABASE` | Redis 접속 정보 (현재 미사용, 6절) |

`.env`는 절대 커밋하지 마세요. 새 키를 추가하면 `.env.example`도 함께 고치세요.

## 8. API

방문자는 `X-Visitor-Id` 헤더(UUID)로 구분합니다. 곡 신청·투표·길이 보고는 **WebSocket으로 그 방에 접속해 있는 방문자만** 할 수 있습니다(아니면 403).
오류 응답은 `{ "message": "화면에 보여줄 문장" }` 형식입니다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/rooms` | 방 목록 + 방별 현재 인원(`listeners`) |
| GET | `/api/rooms/{id}` | 방 정보 + 환경음 목록(`ambients`) |
| GET | `/api/rooms/{roomId}/queue` | 음악 상태 (재생 중 곡, 대기열, 인원, 필요한 표 수) |
| GET | `/api/rooms/{roomId}/queue/history` | 지난 신청곡 최근 30곡 (played/skipped/removed) |
| POST | `/api/rooms/{roomId}/queue` | 곡 신청 `{ "url": "YouTube 링크" }` → 201 `{ "id" }` |
| PUT | `/api/rooms/{roomId}/queue/{itemId}/vote` | 넘기기/빼기 투표 (내 곡이면 바로 처리) → `{ "applied": bool }` |
| DELETE | `/api/rooms/{roomId}/queue/{itemId}/vote` | 투표 취소 |
| PUT | `/api/rooms/{roomId}/queue/{itemId}/duration` | 플레이어가 알아낸 영상 길이 `{ "seconds" }` |
| WS | `/ws/rooms/{roomId}?visitorId={uuid}` | 연결돼 있는 동안 "방에 있음". 서버가 `PlayerState`(type=`state`)를 보냄 |

### 함께 듣는 음악 동작 (QueueService)
- 곡 신청 시 oEmbed로 영상 확인·제목 조회 → 대기열 맨 뒤. 기본 음악이 나오는 중이면 바로 신청곡으로 넘어갑니다.
- 곡 끝 판단: 브라우저가 알려준 영상 길이 중 **가장 긴 값**(짧게 속여 일찍 끝내지 못하게) + 여유 2초. 라이브는 길이가 없어 투표로만 넘어갑니다.
- 투표 통과 기준: `VotePolicy.required(n) = n / 2 + 1` (과반). **방을 떠난 사람의 표는 세지 않고**, 사람이 들어오거나 나가면 다시 판정합니다.
- 방별 `ReentrantLock` + 트랜잭션으로 한 번에 하나씩 처리하고, 끝나면 `QueueChangedEvent` → `RoomSocketHandler`가 방 전체에 각자 기준의 상태를 보냅니다. **다른 방문자의 id는 절대 응답에 넣지 않습니다.**
- 브라우저에서 영상이 재생 불가(YouTube 오류 100/101/150)면 자동으로 넘기기에 한 표를 던집니다.

## 9. 프론트엔드 규칙

- 번들러나 프레임워크 없이 **ES Modules**로 작성합니다. 외부 라이브러리는 꼭 필요할 때만 쓰세요.
- **효과 모듈 인터페이스** (`static/js/effects/{effect}.js`):
  ```js
  export function create(scene) {
    return {
      draw(t) { /* 매 프레임. 배경은 이미 그려져 있음 */ },
      click(u, v) { /* 클릭 위치 (배경 기준 0~1 좌표) */ },
    };
  }
  ```
- 좌표는 **배경 기준 0~1 비율(u, v)**로 쓰고, `scene.P(u, v)`로 화면 좌표로 바꿉니다. 크기는 `scene.k`(원본 대비 배율)를 곱합니다. 배경이 영상이면 `wobble`은 자동으로 꺼집니다.
- 방을 추가하는 순서: ① `static/images/`에 배경 추가 → ② `db/V{n}__...sql`로 room, room_ambient, room_playlist 행 추가 → ③ `effects/{effect}.js` 작성
- 환경음을 추가하려면 `audio/sounds.js`의 `SOUNDS`에 키를 추가하고, 같은 키를 `room_ambient.sound`에 씁니다.
- 브라우저 저장소(localStorage)는 `api.js`의 `store`를 쓰세요(실패해도 무시).
- **소리는 음악과 환경음을 따로 켜고 끕니다** (`app.js`의 `setMusic`, `setAmbient`).
  - 음악이 소리를 내며 재생되기 시작하면 환경음을 자동으로 끄고("환경음 쉬는 중"), 음악을 끄거나 멈추면 자동으로 껐던 환경음만 다시 켭니다. 곡 사이 잠깐 끊김에 깜빡이지 않도록 멈춤은 4초 기다렸다 알립니다(`music.js`의 `onMusicAudible`).
  - 사용자가 직접 누른 켜기/끄기는 항상 우선하며 `localStorage.musicOn`, `ambientOn`에 기억합니다.
  - 브라우저 정책상 소리는 사용자가 클릭한 뒤에만 납니다. 로비에서 카드를 누르면 기억된 설정대로 켜지고, 주소로 바로 들어오면 직접 켜야 합니다.
- YouTube: IFrame Player API를 씁니다(API 키 불필요). 음악 창은 기본·최소화·최대화 3단계이며 `localStorage.musicView`에 기억합니다.
  - **방문자가 재생을 조작할 수 없습니다.** `controls: 0, disablekb: 1, fs: 0`에 투명한 막(`.player-shield`)을 덮었고, 멈춰 있으면 동기화 때 다시 재생합니다. 볼륨만 별도 슬라이더(`musicVolume`)로 조절합니다.
  - 참고: YouTube 이용약관은 재생 중인 플레이어를 가리거나 200×200px보다 작게 만드는 것을 금지합니다. 최소화는 사용자 요청으로 넣은 기능이니 공개 서비스로 낼 때 다시 검토하세요.
- 기본 음악을 추가할 때는 **임베드 재생이 되는지 꼭 확인**하세요. oEmbed가 성공해도 실제 플레이어에서 오류 150(외부 재생 금지)이 날 수 있습니다(라이브 방송에 많음).

## 10. 코드 스타일

- 주석과 사용자에게 보이는 문구는 **한국어**로 씁니다.
- Java: 생성자 주입, DTO는 `record`, 도메인별 패키지(3절)를 따릅니다.
- 주변 코드의 스타일과 주석 밀도를 따르세요.
- 기능을 추가하면 `@WebMvcTest` + `@MockitoBean` 컨트롤러 테스트나 단위 테스트를 함께 작성하세요. 이 테스트들은 DB 없이 돌아갑니다.

## 11. 로드맵 / 진행 상황

- [x] 방 2개 (불멍, 바다멍), 실사 배경 + 캔버스 효과
- [x] 방 정보를 PostgreSQL에서 읽기
- [x] 로비 화면 (방 카드와 실시간 인원)
- [x] 현재 인원 표시 (WebSocket)
- [x] 환경음 믹서 (Web Audio 합성)
- [x] 함께 듣는 음악: 곡 신청, 동기화 재생, 과반 투표로 넘기기/빼기, 기본 음악, 지난 신청곡, 창 최소화·최대화
- [x] 배경 영상 지원 (`room.background_video`, 아직 영상 파일은 없음)
- [ ] 방 추가: 밤하늘, 숲, 모래사장, 클럽 (배경 이미지/영상 필요)
- [ ] 클럽 방 비트 효과
- 로그인 기능은 **만들지 않습니다** (익명 서비스).
