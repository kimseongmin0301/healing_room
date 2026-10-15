# AGENTS.md: AI 작업 가이드

이 문서는 AI 코딩 도구(Claude Code, ChatGPT/Codex, Cursor 등)가 이 저장소에서 작업할 때 따라야 할 규칙과 맥락입니다.
사람이 읽어도 프로젝트 전체를 파악할 수 있게 작성했습니다.

> **ChatGPT 웹에서 사용할 때:** 이 파일과 `README.md`를 대화에 첨부하거나 붙여넣고 시작하세요.
> **Claude Code:** `CLAUDE.md`가 이 파일을 자동으로 불러옵니다.
> **Codex CLI:** 저장소 루트의 `AGENTS.md`를 자동으로 읽습니다.

---

## 1. 프로젝트 한 줄 요약

**힐링룸(Healing Room)**: 불멍, 바다멍 같은 테마를 골라 방을 만들고, 실사 배경, 움직이는 효과, 환경음, **함께 듣는 YouTube 음악**으로 쉬는 웹 서비스입니다.

- **로그인이 없습니다(만들지 않음).** 브라우저마다 UUID(방문자 id)를 만들어 localStorage에 두고 씁니다.
- **테마(theme)**는 배경, 효과, 환경음, 기본 음악의 묶음입니다. **방(room)**은 누구나 이름과 테마를 골라 만들 수 있고, 아무도 없는 채로 **30분**이 지나면 닫힙니다. 테마마다 하나씩 있는 **기본 방**(cabin, sea)은 닫히지 않습니다.
- **방장** = 지금 방에 있는 사람 중 가장 먼저 들어온 사람(보통 방을 만든 사람). 방장이 나가면 그다음에 들어온 사람이 방장이 됩니다. 새로고침처럼 20초 안에 돌아오면 자리를 지켜 줍니다.
- 같은 방에 있는 사람은 **같은 곡을 같은 위치로** 듣습니다(라디오처럼 동기화). 영상은 직접 조작할 수 없고, **방장만** 모두의 재생을 멈추고/이어 틀고/위치를 옮길 수 있습니다.
- 누구나 YouTube 링크로 곡을 신청할 수 있습니다(개수 제한 없음). 대기열이 비면 테마 기본 음악이 순서대로 나옵니다.
- 넘기기/빼기: **내 신청곡**과 **방장**은 바로, 그 밖에는 **방 인원의 50%를 넘는(과반) 투표**가 필요합니다. 대기열 순서는 방장만 바꿉니다.
- 곡 추천: 내 신청 기록(브라우저), YouTube에서 찾은 비슷한 영상(API 키 필요), 함께 신청된 곡, 테마 인기곡.
- **방 채팅**: 닉네임은 자동으로 붙고("졸린 고양이" 같은) 바꿀 수 있습니다(브라우저에 저장). 메시지는 Redis에 방마다 최근 100개를 보관하고, 사용자 방은 닫힐 때 지우며 기본 방은 최근 24시간만 보여줍니다.

## 2. 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 백엔드 | Java 17, Spring Boot 4.1, Gradle(wrapper), Spring Web MVC, Spring Data JPA, Spring WebSocket |
| DB | PostgreSQL 16 (DB 이름 `healing_room`) |
| 실시간 | WebSocket (`/ws/rooms/{roomId}`). 접속자 목록은 서버 메모리에 보관 |
| Redis | 7 (Spring Data Redis). **채팅** 보관·도배 방지·pub/sub. 전용 계정 `healing_room`, DB 1번, 키 접두어 `healing-room:` (6절) |
| 프론트엔드 | 순수 HTML/CSS/JavaScript (ES Modules, 번들러 없음), Canvas 2D, Web Audio, YouTube IFrame Player API |
| 테스트 | JUnit 5, MockMvc, Mockito (`@WebMvcTest`, `@MockitoBean`) |

## 3. 폴더 구조 (도메인별 패키지)

백엔드는 **도메인마다 controller / service / repository / entity / dto 를 두는 구조**입니다.
도메인 사이에 공통으로 쓰는 설정과 예외 처리는 `global`에 둡니다.

```
/                                   ← 저장소 루트
├─ AGENTS.md / CLAUDE.md / README.md
├─ docs/screenshots/               ← README 화면 캡처 (화면이 바뀌면 다시 찍기)
├─ deploy/                          ← 운영 서버 배포 (systemd, nginx, 설치·배포 스크립트). 11절
├─ design/                          ← 디자인 시안(SVG/PNG), 초기 프로토타입(live/)
└─ healing-room/                    ← Spring Boot 앱 (Gradle 프로젝트 루트)
   ├─ .env.example                  ← 환경변수 형식 (.env 는 git 제외)
   ├─ db/                           ← DB 스키마 SQL (수동 실행, 5절)
   └─ src/main/java/com/healingroom/
      ├─ HealingRoomApplication.java
      ├─ domain/
      │  ├─ theme/                  ← 테마 (배경·효과·환경음·기본 음악)
      │  │  ├─ controller/          ThemeController          GET /api/themes
      │  │  ├─ service/             ThemeService
      │  │  ├─ repository/          ThemeRepository, ThemeAmbientRepository, ThemeTrackRepository
      │  │  ├─ entity/              Theme, ThemeAmbient, ThemeTrack(기본 음악)
      │  │  └─ dto/                 ThemeResponse, AmbientResponse
      │  ├─ room/                   ← 방 (만들기, 목록, 빈 방 닫기)
      │  │  ├─ controller/          RoomController           GET/POST /api/rooms, GET /api/rooms/{id}
      │  │  ├─ service/             RoomService (1분마다 30분 넘게 빈 방 닫기)
      │  │  ├─ repository/          RoomRepository
      │  │  ├─ entity/              Room
      │  │  ├─ dto/                 RoomResponse, RoomDetailResponse, CreateRoomRequest
      │  │  ├─ event/               RoomClosedEvent (방이 닫히면 발행 → 채팅 기록 삭제)
      │  │  └─ exception/           RoomException
      │  ├─ queue/                  ← 함께 듣는 음악 (대기열·투표·방장 조작)
      │  │  ├─ controller/          QueueController          /api/rooms/{roomId}/queue/**
      │  │  ├─ service/             QueueService(핵심 로직), VotePolicy(과반 규칙)
      │  │  ├─ repository/          QueueItemRepository, QueueVoteRepository
      │  │  ├─ entity/              QueueItem(멈춤·위치·순서 포함), QueueVote, QueueStatus
      │  │  ├─ dto/                 PlayerState, QueueHistoryResponse, QueueAdd*, DurationRequest, PlaybackRequest, MoveRequest
      │  │  ├─ event/               QueueChangedEvent (상태가 바뀌면 발행 → WebSocket 으로 전송)
      │  │  ├─ exception/           QueueException
      │  │  ├─ client/              YouTubeOEmbedClient (영상 제목 조회, API 키 불필요)
      │  │  └─ util/                YouTubeLinks (링크 → 영상 id)
      │  ├─ chat/                   ← 방 채팅 (Redis)
      │  │  ├─ controller/          ChatController           GET/POST /api/rooms/{roomId}/chat
      │  │  ├─ service/             ChatService(보내기·기록·pub/sub 수신·닫힌 방 정리), ChatPolicy(입력 규칙)
      │  │  ├─ repository/          ChatRepository (StringRedisTemplate: List·카운터·publish)
      │  │  ├─ entity/              ChatRecord (Redis 에 JSON 으로 저장, authorId 는 서버 전용)
      │  │  ├─ dto/                 ChatMessageResponse, ChatSendRequest, ChatPush(type=chat)
      │  │  └─ exception/           ChatException
      │  ├─ recommend/              ← 곡 추천
      │  │  ├─ controller/          RecommendController      GET /api/rooms/{roomId}/recommendations
      │  │  ├─ service/             RecommendService
      │  │  ├─ repository/          RecommendRepository (JdbcClient 집계 SQL: 테마 인기곡, 함께 신청된 곡)
      │  │  ├─ client/              YouTubeSearchClient (YouTube Data API 검색, 12시간 캐시)
      │  │  └─ dto/                 RecommendationResponse, VideoSuggestion
      │  └─ presence/               ← 방 접속(누가 어느 방에 있는지, 방장)
      │     ├─ handler/             RoomSocketHandler (WebSocket 연결/해제, 상태 전송)
      │     └─ service/             RoomSessionRegistry (방별 접속자·들어온 순서·방장, 메모리), RoomBroadcaster (방 사람들에게 각자 기준으로 전송)
      └─ global/
         ├─ config/                 WebSocketConfig, RedisConfig (채팅 pub/sub 구독)
         └─ exception/              ApiException(공통 부모), GlobalExceptionHandler (→ { "message": ... })

   src/main/resources/
      ├─ application.properties
      └─ static/                    ← 프론트엔드 (서버가 그대로 서빙)
         ├─ index.html, css/style.css, images/
         └─ js/
            ├─ app.js               진입점: #방id 라우팅(로비 ↔ 방), 애니메이션 루프
            ├─ api.js               fetch 래퍼, 방문자 id(UUID), localStorage 도우미
            ├─ lobby.js             로비 방 카드 + 인원, 방 만들기
            ├─ room-socket.js       WebSocket 연결·재연결
            ├─ music.js             YouTube 플레이어 동기화, 방장 조작, 대기열/지난 신청곡/추천 탭, 내 신청 기록, 창 최소화·최대화
            ├─ chat.js              채팅 창 (지난 대화, 보내기, 실시간 수신, 닉네임, 접기·안 읽은 수)
            ├─ scene.js             캔버스 공통 도구 (cover 맞춤, 좌표 변환, wobble, glow, 배경 영상)
            ├─ audio/sounds.js      환경음 합성 (fire, wind, rain, waves, crickets)
            ├─ audio/mixer.js       환경음 믹서 UI
            └─ effects/{effect}.js  방별 시각 효과
```

테스트도 같은 패키지 구조를 따릅니다 (`src/test/java/com/healingroom/domain/...`).

**새 도메인을 추가할 때**는 `domain/{이름}/` 아래에 필요한 계층(controller, service, repository, entity, dto)만 만드세요.
다른 도메인의 entity 를 컨트롤러에서 직접 쓰지 말고 service 를 통해 접근합니다.

## 4. 실행 · 테스트 명령 (로컬)

모든 Gradle 명령은 `healing-room/` 폴더에서 실행합니다.

```bash
cd healing-room
./gradlew bootRun     # 실행 → http://localhost:18280  (Windows: gradlew.bat bootRun)
./gradlew test        # 테스트
```

- `.env`는 `healing-room/.env`에 둡니다. `spring.config.import=optional:file:.env[.properties]`로 읽으므로 **작업 디렉터리가 `healing-room/`이어야** 합니다.
- 포트는 환경변수 `PORT`가 있으면 그 값, 없으면 **18280**입니다(운영 서버와 같음. 8080 은 쓰지 않음).
- `bootRun`은 `static/` 화면 파일을 `src`에서 바로 읽습니다(`build.gradle`의 `sourceResources`). 화면 파일은 고친 뒤 새로고침만 하면 되고, Java 코드를 고치면 재시작하세요. IntelliJ의 Application 실행 설정은 이 설정을 쓰지 않으니 빌드(Ctrl+F9)를 해야 반영됩니다.
- 같은 네트워크의 다른 기기에서는 `http://{PC의 IP}:18280`으로 접속합니다. **HTTPS가 아닌 IP 접속에서는 `crypto.randomUUID()` 같은 보안 컨텍스트 전용 API가 없으니** 프론트엔드에서 쓰지 마세요(`api.js`의 `uuid()` 참고).
- `HealingRoomApplicationTests`(@SpringBootTest)는 **실제 DB와 Redis에 접속**합니다. DB는 엔티티와 테이블이 맞는지 검증만 하고(읽기만 함), Redis는 채팅 채널을 구독만 합니다. `.env`가 없으면 실패해요.
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
| `V5__theme_and_rooms.sql` | room→theme, room_ambient→theme_ambient, room_playlist→theme_track 로 이름 변경. 새 room(사용자 방) 테이블, 기본 방 cabin·sea 생성, 대기열에 paused_at·queue_order 추가 | ✅ |

> V1~V4 파일 안의 `room` 은 지금의 `theme` 입니다(V5 에서 이름이 바뀜).

## 6. ⚠️ Redis 규칙

Redis는 **다른 서비스(other-service)와 같은 서버를 공유**합니다. 그 서비스가 DB 0번, 5번을 씁니다.

- 이 앱은 전용 계정 **`healing_room`**으로만 접속합니다. ACL: `~healing-room:* &healing-room:* +@all -@dangerous`
  → `healing-room:`으로 시작하는 키·채널만 쓸 수 있고 `FLUSHDB`, `FLUSHALL`, `KEYS`, `CONFIG` 같은 위험한 명령은 막혀 있습니다.
- **DB 1번**(`REDIS_DATABASE`)을 쓰고, 모든 키·채널 앞에 **`healing-room:`**(`healing-room.redis.key-prefix`)을 붙입니다. 새 키를 만들 때도 반드시 이 접두어를 붙이세요(안 붙이면 `NOPERM`).
- 전체 대상 명령(`FLUSHDB`, `FLUSHALL`, `KEYS *`)은 절대 쓰지 마세요. 키를 찾아야 하면 `SCAN` + 접두어 패턴을 씁니다.
- 계정 권한을 바꿀 때는 Redis 관리자 계정으로 `ACL SETUSER` 후 `ACL SAVE`를 실행합니다(재시작 필요 없음, 다른 서비스에 영향 없음). 관리자 정보는 저장소에 적지 않습니다.
- Redis가 실패해도 방·음악은 동작해야 합니다. 채팅만 503(`ChatException.Unavailable`)을 돌려줍니다.

### 쓰는 키와 채널
| 키/채널 | 종류 | 용도 | 수명 |
|---|---|---|---|
| `healing-room:chat:{roomId}` | List (JSON) | 방의 최근 메시지 100개 | 기본 방: 마지막 메시지 후 24시간 / 사용자 방: 닫힐 때 삭제(놓쳐도 7일 뒤 자동 삭제) |
| `healing-room:chat-rate:{visitorId}` | String (카운터) | 도배 방지 (10초에 6개) | 10초 |
| `healing-room:chat` | pub/sub 채널 | 새 메시지를 모든 서버에 알림 | - |

### 채팅 흐름 (ChatService)
1. `POST /api/rooms/{roomId}/chat` → 방에 접속해 있는지 확인 → 입력 정리(`ChatPolicy`: 제어 문자 제거, 메시지 300자·닉네임 12자) → 도배 확인
2. Redis List 에 저장(최근 100개만 남김) → `healing-room:chat` 채널로 publish
3. `RedisConfig` 의 구독자가 받아서 `RoomBroadcaster` 로 이 서버에 접속한 방 사람들에게 `{ type: "chat", message }` 전송. `mine` 은 받는 사람마다 계산하고 **보낸 사람의 방문자 id 는 절대 내보내지 않습니다.**
4. 방이 닫히면(`RoomClosedEvent`) 그 방의 채팅 키를 지웁니다.
- 화면은 메시지를 `textContent`로만 넣습니다(HTML로 해석하지 않음). 서버는 HTML을 바꾸지 않습니다.

## 7. 환경변수 (`healing-room/.env`)

| 키 | 설명 |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | PostgreSQL 접속 정보 (`DB_NAME=healing_room`) |
| `DB_USERNAME`, `DB_PASSWORD` | 앱 계정 (healing_room DB 소유자) |
| `DB_ADMIN_USERNAME`, `DB_ADMIN_PASSWORD` | 관리자 계정, 수동 작업 전용 (앱에서 사용 금지) |
| `REDIS_HOST`, `REDIS_PORT` | Redis 접속 정보 |
| `REDIS_USERNAME`, `REDIS_PASSWORD` | 전용 계정 `healing_room` (6절) |
| `REDIS_DATABASE` | Redis DB 번호 (1) |
| `YOUTUBE_API_KEY` | YouTube Data API v3 키. 추천의 "비슷한 영상"에만 쓰며, 비워 두면 그 기능만 꺼짐. 검색 1번 = 하루 할당량 10,000 중 100 |

`.env`는 절대 커밋하지 마세요. 새 키를 추가하면 `.env.example`도 함께 고치세요.

## 8. API

방문자는 `X-Visitor-Id` 헤더(UUID)로 구분합니다. 곡 신청·투표·조작은 **WebSocket으로 그 방에 접속해 있는 방문자만** 할 수 있습니다(아니면 403). 방장 전용 기능은 방장이 아니면 403입니다.
오류 응답은 `{ "message": "화면에 보여줄 문장" }` 형식입니다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/themes` | 테마 목록 (방 만들기에서 고름) |
| GET | `/api/rooms` | 열려 있는 방 목록 (기본 방 먼저) + 테마 + 현재 인원(`listeners`) |
| POST | `/api/rooms` | 방 만들기 `{ "name", "themeId" }` → 201. 이름 1~30자, 한 사람이 동시에 3개까지 |
| GET | `/api/rooms/{id}` | 방 정보(테마 포함) + 환경음 목록(`ambients`). 닫힌 방은 404 |
| GET | `/api/rooms/{roomId}/queue` | 음악 상태 (재생 중 곡, 대기열, 인원, 필요한 표 수, `hostIsMe`) |
| GET | `/api/rooms/{roomId}/queue/history` | 지난 신청곡 최근 30곡 (played/skipped/removed) |
| POST | `/api/rooms/{roomId}/queue` | 곡 신청 `{ "url": "YouTube 링크 또는 id" }` → 201 `{ "id", "youtubeId", "title" }` |
| PUT | `/api/rooms/{roomId}/queue/{itemId}/vote` | 넘기기/빼기 (내 곡이거나 방장이면 바로, 아니면 투표) → `{ "applied": bool }` |
| DELETE | `/api/rooms/{roomId}/queue/{itemId}/vote` | 투표 취소 |
| PUT | `/api/rooms/{roomId}/queue/{itemId}/duration` | 플레이어가 알아낸 영상 길이 `{ "seconds" }` |
| PUT | `/api/rooms/{roomId}/queue/playback` | **방장** `{ "action": "pause" \| "resume" \| "seek", "seconds"? }` |
| PUT | `/api/rooms/{roomId}/queue/{itemId}/move` | **방장** 대기열 순서 `{ "direction": "up" \| "down" }` |
| GET | `/api/rooms/{roomId}/recommendations` | 지금 곡 기준 추천 `{ basedOn, similar, similarAvailable, together, popular }` |
| GET | `/api/rooms/{roomId}/chat` | 지난 대화 (오래된 것부터, 최대 100개) `[{ id, nickname, text, sentAt, mine }]` |
| POST | `/api/rooms/{roomId}/chat` | 채팅 보내기 `{ "nickname", "text" }` → 201. 10초에 6개 넘으면 429, Redis 장애 시 503 |
| WS | `/ws/rooms/{roomId}?visitorId={uuid}` | 연결돼 있는 동안 "방에 있음". 서버가 `PlayerState`(type=`state`)와 새 채팅(type=`chat`)을 보냄. 닫힌 방이면 연결 거부 |

### 함께 듣는 음악 동작 (QueueService)
- 곡 신청 시 oEmbed로 영상 확인·제목 조회 → 대기열 맨 뒤. 기본 음악이 나오는 중이면 바로 신청곡으로 넘어갑니다.
- 곡 끝 판단: 브라우저가 알려준 영상 길이 중 **가장 긴 값**(짧게 속여 일찍 끝내지 못하게) + 여유 2초. 라이브는 길이가 없어 투표로만 넘어갑니다.
- 투표 통과 기준: `VotePolicy.required(n) = n / 2 + 1` (과반). **방을 떠난 사람의 표는 세지 않고**, 사람이 들어오거나 나가면 다시 판정합니다.
- 방별 `ReentrantLock` + 트랜잭션으로 한 번에 하나씩 처리하고, 끝나면 `QueueChangedEvent` → `RoomSocketHandler`가 방 전체에 각자 기준의 상태를 보냅니다. **다른 방문자의 id는 절대 응답에 넣지 않습니다.**
- 브라우저에서 영상이 재생 불가(YouTube 오류 100/101/150)면 자동으로 넘기기에 한 표를 던집니다.
- 방장 조작: 재생 위치 = (`paused_at` 또는 지금) − `started_at`. 멈춤은 `paused_at` 기록, 이어서 재생은 멈춰 있던 시간만큼 `started_at`을 뒤로 미룸, 위치 이동은 `started_at`을 다시 계산합니다. 멈춘 곡은 끝나지 않습니다.
- 대기열 순서는 `queue_order`(작을수록 먼저). 새 곡은 방의 최댓값 + 1, 방장이 옮기면 이웃과 값을 맞바꿉니다.

### 곡 추천 (RecommendService)
- **내가 자주 신청한 곡**: 서버가 아니라 브라우저 `localStorage.myRequests`(최대 100곡, 신청 횟수·마지막 시각)에 저장합니다.
- **비슷한 영상**: YouTube Data API `search`로 지금 곡 제목을 검색합니다(YouTube가 "연관 영상" 검색을 없애서 제목 검색 방식). 조건: `videoEmbeddable=true`, `videoCategoryId=10`(음악), 라이브·예정 방송(`liveBroadcastContent != none`) 제외 — 라이브는 다른 사이트에서 재생이 막히는 경우가 많아서입니다. 같은 곡은 12시간 캐시합니다. 키가 없거나 할당량 초과·오류면 빈 목록이고 나머지 추천은 그대로 나옵니다.
- **함께 신청된 곡**: 같은 방에서 앞뒤 3시간 안에 함께 신청된 곡 (함께 신청된 방이 많은 순).
- **테마 인기곡**: 같은 테마의 방들에서 최근 30일 동안 많이 신청된 곡. 기본 음악은 세지 않습니다.

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
- 테마를 추가하는 순서: ① `static/images/`에 배경 추가 → ② `db/V{n}__...sql`로 theme, theme_ambient, theme_track 행과 기본 방(room, is_default=true) 추가 → ③ `effects/{effect}.js` 작성
- 환경음을 추가하려면 `audio/sounds.js`의 `SOUNDS`에 키를 추가하고, 같은 키를 `theme_ambient.sound`에 씁니다.
- 브라우저 저장소(localStorage)는 `api.js`의 `store`를 쓰세요(실패해도 무시).
- **소리는 음악과 환경음을 따로 켜고 끕니다** (`app.js`의 `setMusic`, `setAmbient`).
  - 음악이 소리를 내며 재생되기 시작하면 환경음을 자동으로 끄고("환경음 쉬는 중"), 음악을 끄거나 멈추면 자동으로 껐던 환경음만 다시 켭니다. 곡 사이 잠깐 끊김에 깜빡이지 않도록 멈춤은 4초 기다렸다 알립니다(`music.js`의 `onMusicAudible`).
  - 사용자가 직접 누른 켜기/끄기는 항상 우선하며 `localStorage.musicOn`, `ambientOn`에 기억합니다.
  - 브라우저 정책상 소리는 사용자가 클릭한 뒤에만 납니다. 로비에서 카드를 누르면 기억된 설정대로 켜지고, 주소로 바로 들어오면 직접 켜야 합니다.
- YouTube: IFrame Player API를 씁니다(API 키 불필요). 음악 창은 기본·최소화·최대화 3단계이며 `localStorage.musicView`에 기억합니다.
  - **YouTube 플레이어는 아무도 직접 조작할 수 없습니다.** `controls: 0, disablekb: 1, fs: 0`에 투명한 막(`.player-shield`)을 덮었고, 멈춰 있으면 동기화 때 다시 재생합니다. 볼륨만 별도 슬라이더(`musicVolume`)로 조절합니다.
  - 재생 위치 바는 모두에게 보이고, **방장에게만** 멈춤 버튼과 위치 이동이 켜집니다(서버 API로 모두에게 적용). 방장에게는 대기열 ▲▼와 바로 넘기기/빼기가 보입니다.
  - 참고: YouTube 이용약관은 재생 중인 플레이어를 가리거나 200×200px보다 작게 만드는 것을 금지합니다. 최소화는 사용자 요청으로 넣은 기능이니 공개 서비스로 낼 때 다시 검토하세요.
- 기본 음악을 추가할 때는 **임베드 재생이 되는지 꼭 확인**하세요. oEmbed가 성공해도 실제 플레이어에서 오류 150(외부 재생 금지)이 날 수 있습니다(라이브 방송에 많음).

## 10. 코드 스타일

- 주석과 사용자에게 보이는 문구는 **한국어**로 씁니다.
- Java: 생성자 주입, DTO는 `record`, 도메인별 패키지(3절)를 따릅니다.
- 주변 코드의 스타일과 주석 밀도를 따르세요.
- 기능을 추가하면 `@WebMvcTest` + `@MockitoBean` 컨트롤러 테스트나 단위 테스트를 함께 작성하세요. 이 테스트들은 DB 없이 돌아갑니다.

## 11. 운영 서버 배포

- 주소: **https://healing.kimseongmin.co.kr** (nginx → `127.0.0.1:18280`). SSH 별칭 `ai-server`(`~/.ssh/config`).
- DB·Redis 가 같은 서버에 있어서 운영 `.env` 는 `127.0.0.1` 로 접속합니다.
- 서버 폴더 (other-app 와 같은 구조)
  ```
  ~/healing-room/
  ├─ releases/{날짜}-{커밋}/healing-room.jar   최근 5개 보관
  ├─ current → releases/…                     실행 중인 버전
  ├─ shared/.env                              운영 설정 (권한 600, server.address=127.0.0.1)
  └─ deploy/                                  저장소 deploy/ 복사본
  ```
- 실행: systemd `healing-room` (Java: `~/apps/jre17`), 로그는 `journalctl -u healing-room -f` (seongmin 은 adm 그룹이라 sudo 없이 볼 수 있음)
- **새 버전 배포**: 커밋한 뒤 저장소 루트에서 `bash deploy/deploy.sh` (테스트·빌드 → 업로드 → 재시작 → 확인). 되돌리기는 `bash deploy/deploy.sh rollback`.
- **sudo 가 필요한 설치**(서비스 등록, nginx, 인증서)는 `deploy/install-root.sh` 를 사람이 직접 실행합니다. AI 는 sudo 비밀번호를 다루지 않습니다. 재시작만 `/etc/sudoers.d/healing-room` 으로 비밀번호 없이 허용합니다.
- nginx 는 **`sites-available/healing.kimseongmin.co.kr` 파일만** 이 앱 것입니다. 기존 `kimseongmin.co.kr` 설정 파일은 다른 서비스(메인, RAG, other-app)가 쓰므로 절대 수정하지 마세요. 적용 전에는 항상 `nginx -t`.
- 운영 서버에 붙은 앱과 **같은 DB 를 쓰는 서버를 로컬에서 동시에 띄우지 마세요**(대기열 스케줄러가 두 번 돕니다). 로컬 개발용 DB 를 따로 두는 게 좋습니다.

## 12. 로드맵 / 진행 상황

- [x] 방 2개 (불멍, 바다멍), 실사 배경 + 캔버스 효과
- [x] 방 정보를 PostgreSQL에서 읽기
- [x] 로비 화면 (방 카드와 실시간 인원)
- [x] 현재 인원 표시 (WebSocket)
- [x] 환경음 믹서 (Web Audio 합성)
- [x] 함께 듣는 음악: 곡 신청, 동기화 재생, 과반 투표로 넘기기/빼기, 기본 음악, 지난 신청곡, 창 최소화·최대화
- [x] 배경 영상 지원 (`theme.background_video`, 아직 영상 파일은 없음)
- [x] 방 만들기 (이름 + 테마), 방장(재생 조작·순서 바꾸기), 빈 방 30분 뒤 닫기
- [x] 곡 추천 (내 신청 기록, 비슷한 영상, 함께 신청된 곡, 테마 인기곡)
- [x] 방 채팅 (Redis 보관·도배 방지·pub/sub, 자동 닉네임)
- [ ] 테마 추가: 밤하늘, 숲, 모래사장, 클럽 (배경 이미지/영상 필요)
- [ ] 클럽 방 비트 효과
- 로그인 기능은 **만들지 않습니다** (익명 서비스).
