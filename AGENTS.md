# AGENTS.md: AI 작업 가이드

이 문서는 AI 코딩 도구(Claude Code, ChatGPT/Codex, Cursor 등)가 이 저장소에서 작업할 때 따라야 할 규칙과 맥락입니다.
사람이 읽어도 프로젝트 전체를 파악할 수 있게 작성했습니다.

> **ChatGPT 웹에서 사용할 때:** 이 파일과 `README.md`를 대화에 첨부하거나 붙여넣고 시작하세요.
> **Claude Code:** `CLAUDE.md`가 이 파일을 자동으로 불러옵니다.
> **Codex CLI:** 저장소 루트의 `AGENTS.md`를 자동으로 읽습니다.

---

## 1. 프로젝트 한 줄 요약

**힐링룸(Healing Room)**: 불멍, 바다멍 같은 테마를 골라 방을 만들고, 실사 배경, 움직이는 효과, 환경음, **함께 듣는 YouTube 음악**으로 쉬는 웹 서비스입니다.

- **방문자 로그인은 없습니다(만들지 않음).** 브라우저마다 UUID(방문자 id)를 만들어 localStorage 와 쿠키(`hr_vid`, HttpOnly)에 함께 둡니다. 시작할 때 `GET /api/visitor` 로 맞추고, 쿠키가 있으면 쿠키 id 가 이깁니다(저장소만 지우거나 바꿔서 내보내기 금지를 피하지 못하게). IP 로는 구분하지 않습니다(PC방·회사처럼 IP 를 같이 쓰는 곳). 로그인은 **관리자 화면에만** 있습니다(로그인 + 관리자 키 2단계).
- **테마(theme)**는 배경, 효과, 환경음, 기본 음악의 묶음입니다. **방(room)**은 누구나 이름과 테마를 골라 만들 수 있고, 아무도 없는 채로 **30분**이 지나면 닫힙니다. **기본 방**은 테마마다 하나씩 있고 닫히지 않습니다. 테마 10개와 기본 방 id: cabin, sea, forest, forest-rain→`rain`, rain-cafe→`cafe`, snow-cabin→`snow`, sunset-beach→`sunset`, space-station→`space`, space-nebula→`galaxy`, lounge-club→`club` (방 id 는 12자 제한, 주소 `/{id}` 예: `/cafe`). 로비에는 불멍·바다멍(`room.featured`)과 사용자 방, 사람이 있는 기본 방만 보입니다(`RoomService.findLobby`).
- **방장** = 지금 방에 있는 사람 중 가장 먼저 들어온 사람(보통 방을 만든 사람). 방장이 나가면 그다음에 들어온 사람이 방장이 됩니다. 새로고침처럼 20초 안에 돌아오면 자리를 지켜 줍니다.
- 같은 방에 있는 사람은 **같은 곡을 같은 위치로** 듣습니다(라디오처럼 동기화). 영상은 직접 조작할 수 없고, **방장만** 모두의 재생을 멈추고/이어 틀고/위치를 옮길 수 있습니다.
- 누구나 YouTube 링크로, 또는 **직접 검색**해서 곡을 신청할 수 있습니다(개수 제한 없음. 검색은 한 사람이 1분에 5번). 대기열이 비면 **방금 나온 곡과 비슷한 영상이 자동으로 이어지고(자동 추천)**, 추천을 못 찾으면 테마 기본 음악이 나옵니다. 자동으로 넣은 곡(기본 음악·자동 추천)은 누구나 바로 넘길 수 있고, 신청곡이 들어오면 바로 바뀝니다.
- 넘기기/빼기: **내 신청곡**과 **방장**은 바로, 그 밖에는 **방 인원의 50%를 넘는(과반) 투표**가 필요합니다. 대기열 순서는 방장만 바꿉니다.
- 곡 추천: 내 신청 기록(브라우저), YouTube에서 찾은 비슷한 영상(API 키 필요), 함께 신청된 곡, 테마 인기곡.
- 영상 자막은 끕니다. 자동 추천은 같은 노래의 다른 영상(공식 MV·라이브·가사 영상)을 다시 고르지 않습니다.
- **접속자 목록**: 방 사람 수와 닉네임, **앞부분만 보이는 IP**(`203.0.*.*`)를 모두에게 보여줍니다. 전체 IP 는 관리자 화면(`/admin`)에서만 봅니다.
- **주소**: `.html`·`#` 없이 `/{방id}`, `/themes`, `/themes/{방id}`, `/about`, `/privacy`, `/admin`. 없거나 닫힌 방·없는 주소는 404 안내 화면(5초 뒤 `/`).
- **의견 보내기**: 로비 맨 아래에서 익명으로(관리자 화면에서만 읽음). **서버비 후원**: 위쪽 가운데 ☕ 버튼(카카오페이 QR).
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
├─ AGENTS.md / CLAUDE.md / README.md / .editorconfig (모든 파일 UTF-8)
├─ docs/screenshots/               ← README 화면 캡처 (화면이 바뀌면 다시 찍기), themes.jpg 는 테마 배경 모음
├─ docs/theme-image-prompts.md     ← 새 테마 배경 이미지를 ChatGPT 에 요청할 때 첨부하는 요청서
├─ scripts/                         ← seo_pages.py (소개 페이지·미리보기 이미지·sitemap 생성), admin_password.py (관리자 비밀번호 → 해시)
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
      │  ├─ room/                   ← 방 (만들기, 목록, 방장·관리자의 설정 바꾸기·닫기, 빈 방 닫기)
      │  │  ├─ controller/          RoomController           GET/POST /api/rooms, GET /api/rooms/{id}, PUT …/settings, POST …/close, POST …/reset
      │  │  ├─ service/             RoomService (설정 바꾸기·닫기, 최대 인원, 1분마다 30분 넘게 빈 방 닫기), RoomResetService (기록 초기화)
      │  │  ├─ repository/          RoomRepository
      │  │  ├─ entity/              Room
      │  │  ├─ dto/                 RoomResponse(maxPeople 포함), RoomDetailResponse, CreateRoomRequest, RoomSettingsRequest
      │  │  ├─ event/               RoomClosedEvent (by: host·admin·idle → 채팅 삭제 + 남은 사람에게 room-closed),
      │  │  │                       RoomUpdatedEvent (설정이 바뀌면 → room-updated)
      │  │  └─ exception/           RoomException (HostOnly, DefaultRoomLocked, DefaultRoomTheme, InvalidMaxPeople 등)
      │  ├─ queue/                  ← 함께 듣는 음악 (대기열·투표·방장 조작)
      │  │  ├─ controller/          QueueController          /api/rooms/{roomId}/queue/**
      │  │  ├─ service/             QueueService(핵심 로직), VotePolicy(과반 규칙)
      │  │  ├─ repository/          QueueItemRepository, QueueVoteRepository
      │  │  ├─ entity/              QueueItem(멈춤·위치·순서 포함), QueueVote, QueueStatus, QueueSource(request/default/autoplay)
      │  │  ├─ dto/                 PlayerState, QueueHistoryResponse, QueueAdd*, DurationRequest, PlaybackRequest, MoveRequest
      │  │  ├─ event/               QueueChangedEvent (상태가 바뀌면 발행 → WebSocket 으로 전송)
      │  │  ├─ exception/           QueueException
      │  │  ├─ client/              YouTubeOEmbedClient (영상 제목 조회, API 키 불필요)
      │  │  └─ util/                YouTubeLinks (링크 → 영상 id), SongTitles (제목 → 곡 이름, 같은 노래 알아보기)
      │  ├─ chat/                   ← 방 채팅 (Redis)
      │  │  ├─ controller/          ChatController           GET/POST /api/rooms/{roomId}/chat
      │  │  ├─ service/             ChatService(보내기·기록·pub/sub 수신·닫힌 방 정리), ChatPolicy(입력 규칙)
      │  │  ├─ repository/          ChatRepository (StringRedisTemplate: List·카운터·publish)
      │  │  ├─ entity/              ChatRecord (Redis 에 JSON 으로 저장, authorId 는 서버 전용)
      │  │  ├─ dto/                 ChatMessageResponse, ChatSendRequest, ChatPush(type=chat)
      │  │  └─ exception/           ChatException
      │  ├─ recommend/              ← 곡 추천
      │  │  ├─ controller/          RecommendController      GET /api/rooms/{roomId}/recommendations
      │  │  │                       SearchController         GET /api/rooms/{roomId}/search?q= (직접 검색)
      │  │  ├─ service/             RecommendService, SearchService (방 접속 확인, 1분에 5번 제한)
      │  │  ├─ exception/           SearchException
      │  │  ├─ repository/          RecommendRepository (JdbcClient 집계 SQL: 테마 인기곡, 함께 신청된 곡)
      │  │  ├─ client/              YouTubeSearchClient (비슷한 영상·직접 검색, 12시간 캐시, 할당량 소진 감지)
      │  │  └─ dto/                 RecommendationResponse, VideoSuggestion, SearchResult
      │  ├─ site/                   ← 화면 주소 (.html·# 없이)
      │  │  └─ controller/          SiteController  /{방id} → 첫 화면, /about·/themes·/themes/{id}·/admin → resources/pages/, 예전 .html 주소는 301
      │  ├─ feedback/               ← 로비 의견 보내기 (익명)
      │  │  ├─ controller/          FeedbackController       POST /api/feedback
      │  │  ├─ service/             FeedbackService (입력 정리, 한 브라우저 10분에 3번·전체 1시간에 60번, 관리자 조회·삭제)
      │  │  ├─ repository/ entity/  FeedbackRepository, Feedback
      │  │  ├─ dto/                 FeedbackRequest, FeedbackResponse (방문자 id 앞 8자리만)
      │  │  └─ exception/           FeedbackException
      │  ├─ admin/                  ← 관리자: ① 로그인(쿠키 HR_ADMIN) ② 요청마다 X-Admin-Token. /admin 은 로그인 전 pages/admin-login.html
      │  │  ├─ controller/          AdminController          /api/admin/login·logout·presence·feedback·reset·kick·rooms
      │  │  ├─ service/             AdminLoginService (아이디·비밀번호 해시 PBKDF2, IP 5번 틀리면 15분 잠금, 로그인 8시간, 메모리),
      │  │  │                       AdminService (관리자 키 확인, 모든 방 접속자·전체 IP)
      │  │  ├─ dto/                 AdminLoginRequest, AdminPresenceResponse
      │  │  └─ exception/           AdminException (설정 없음 404, 키·로그인 틀림 401, 잠금 429)
      │  ├─ moderation/             ← 욕설·음란어 30분 제한
      │  │  ├─ service/             ProfanityFilter (resources/moderation/badwords.txt·allow.txt·mask.txt), ModerationService (IP·방문자 제한, 관리자 풀기)
      │  │  ├─ repository/ entity/  BanRepository (Redis, 30분 TTL), BanRecord
      │  │  ├─ dto/ exception/      BanResponse, ModerationException (Banned 403)
      │  ├─ visitor/                ← 방문자 번호 (로그인 없이 브라우저 구분)
      │  │                          VisitorController (GET /api/visitor), VisitorCookie (쿠키 hr_vid, WebSocket 연결 때도 읽음)
      │  └─ presence/               ← 방 접속(누가 어느 방에 있는지, 방장, 닉네임, IP), 내보내기
      │     ├─ controller/          KickController           POST /api/rooms/{roomId}/kick (방장)
      │     ├─ exception/           PresenceException
      │     ├─ handler/             RoomSocketHandler (WebSocket 연결/해제, 상태·접속자 전송, 닉네임 변경 수신),
      │     │                       ClientIpHandshakeInterceptor (nginx 뒤에서 X-Forwarded-For 로 실제 IP)
      │     ├─ dto/                 PresencePush (type=presence, 가린 IP)
      │     ├─ util/                IpMask (203.0.*.*)
      │     └─ service/             RoomSessionRegistry (방별 접속자·들어온 순서·방장·가린 키·내보낸 사람, 메모리), RoomBroadcaster (방 사람들에게 각자 기준으로 전송),
      │                             KickService (방장·관리자 내보내기, 10분 재입장 금지, kicked 알림 후 4001 로 끊기)
      └─ global/
         ├─ config/                 WebSocketConfig, RedisConfig (채팅 pub/sub 구독), TextFileCharsetFilter (.txt 를 UTF-8 로),
         │                          AppVersionFilter (API 응답에 X-App-Version = 서버가 켜진 시각)
         ├─ util/                   ClientRequest (nginx 뒤에서 실제 IP·https 여부, 루프백에서 온 헤더만 믿음)
         └─ exception/              ApiException(공통 부모), GlobalExceptionHandler (→ { "message": ... })

   src/main/resources/
      ├─ application.properties
      ├─ pages/                     ← SiteController 가 깔끔한 주소로 보여주는 페이지 (정적 파일로 바로 나가지 않음)
      │                               themes/{방id}.html·themes/index.html·about.html·privacy.html (seo_pages.py 결과물), admin.html, admin-login.html
      └─ static/                    ← 프론트엔드 (서버가 그대로 서빙)
         ├─ index.html (링크 미리보기 og 메타 태그 포함), favicon.ico, css/style.css
         ├─ images/                 테마 배경(webp), 마스코트(mascot*.webp), og-cat.jpg·og/{방id}.jpg(링크 미리보기 1200×630), 아이콘, support-qr.png
         ├─ error/                  404.html·4xx.html·5xx.html (오류 안내, 5초 뒤 /)
         ├─ robots.txt·sitemap.xml·llms.txt  검색엔진·AI 수집기용
         └─ js/
            ├─ app.js               진입점: 주소 /{방id} 라우팅(history API, 예전 /#id 는 바꿔 줌, 로비 ↔ 방), 애니메이션 루프
            ├─ api.js               fetch 래퍼, 방문자 id(UUID), localStorage 도우미
            ├─ lobby.js             로비 방 카드 + 인원(한 쪽에 6·4·2개, 좌우 버튼으로 넘기기), 방 만들기
            ├─ room-socket.js       WebSocket 연결·재연결
            ├─ music.js             YouTube 플레이어 동기화(자막 끄기), 방장 조작, 대기열/지난 신청곡/추천/검색 탭, 내 신청 기록, 창 최소화·최대화
            ├─ chat.js              채팅 창 (지난 대화, 보내기, 실시간 수신, 닉네임, 접기·안 읽은 수)
            ├─ people.js            함께 있는 사람 목록 (닉네임, 가린 IP, 방장 👑)
            ├─ admin.js             관리자 화면 (pages/admin.html: 관리자 키, 접속자, 받은 의견, 초기화, 로그아웃)
            ├─ feedback.js          로비 의견 보내기
            ├─ support.js           위쪽 ☕ 서버비 후원 버튼 → 카카오페이 QR 창
            ├─ scene.js             캔버스 공통 도구 (cover 맞춤, 좌표 변환, wobble, glow, 배경 영상)
            ├─ audio/sounds.js      환경음 합성 (fire, wind, rain, waves, crickets, stream, thunder, hum, pad, city, seagulls, beat)
            ├─ audio/mixer.js       환경음 믹서 UI
            ├─ effects/parts.js     효과 조각 (별, 별똥별, 눈, 비, 웅덩이, 반딧불, 반짝임, 김·안개, 불빛 깜빡임)
            └─ effects/{effect}.js  테마별 시각 효과 (effect 이름 = 테마 id)
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
| `V6__queue_item_source.sql` | 대기열에 source(request/default/autoplay) 추가 | ✅ |
| `V5__theme_and_rooms.sql` | room→theme, room_ambient→theme_ambient, room_playlist→theme_track 로 이름 변경. 새 room(사용자 방) 테이블, 기본 방 cabin·sea 생성, 대기열에 paused_at·queue_order 추가 | ✅ |
| `V7__more_themes.sql` | 새 테마 8개 (행만 추가): 환경음, 임베드 재생 확인한 기본 음악 | ✅ |
| `V8__default_rooms_for_all_themes.sql` | 새 테마 8개의 기본 방 (id: forest, rain, cafe, snow, sunset, space, galaxy, club) | ✅ |
| `V9__feedback.sql` | 로비 의견 보내기 `feedback` 테이블 (익명, IP·연락처 없음) | ✅ |
| `V10__room_featured.sql` | `room.featured` (로비에 늘 보이기: cabin, sea) | ✅ |
| `V11__room_max_people.sql` | `room.max_people` (최대 인원 2~100, NULL 이면 제한 없음) | ✅ |

> V1~V4 파일 안의 `room` 은 지금의 `theme` 입니다(V5 에서 이름이 바뀜).

## 6. ⚠️ Redis 규칙

Redis는 **다른 서비스와 같은 서버를 공유**합니다. 그 서비스들이 다른 DB 번호를 씁니다.

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
| `healing-room:chat-image-rate:{visitorId}` | String (카운터) | 이미지 도배 방지 (1분에 3장) | 1분 |
| `healing-room:chat` | pub/sub 채널 | 새 메시지를 모든 서버에 알림 | - |
| `healing-room:ban:{id}` | String (JSON) | 욕설·음란어 이용 제한 기록(`BanRecord`: 방문자·IP·어디서·걸린 말·시각) | 30분 (TTL) |
| `healing-room:ban-ip:{IP}`, `healing-room:ban-visitor:{visitorId}` | String (제한 id) | IP·방문자 번호로 제한 찾기 | 30분 (TTL) |

### 채팅 흐름 (ChatService)
1. `POST /api/rooms/{roomId}/chat` → 방에 접속해 있는지 확인 → 입력 정리(`ChatPolicy`: 제어 문자 제거, 메시지 300자·닉네임 12자) → 도배 확인
2. Redis List 에 저장(최근 100개만 남김) → `healing-room:chat` 채널로 publish
3. `RedisConfig` 의 구독자가 받아서 `RoomBroadcaster` 로 이 서버에 접속한 방 사람들에게 `{ type: "chat", message }` 전송. `mine` 은 받는 사람마다 계산하고 **보낸 사람의 방문자 id 는 절대 내보내지 않습니다.**
4. 방이 닫히면(`RoomClosedEvent`) 그 방의 채팅 키를 지웁니다.
5. **메시지 하나 지우기**(방장은 방 안 채팅의 🗑, 관리자는 `/admin` "방 관리" → 채팅): List 에서 그 메시지만 `LREM` 하고 `healing-room:chat` 채널로 `{type:"delete", roomId, id}` 를 publish → 각 서버가 방 사람들에게 `{type:"chat-delete", id}` → 화면에서 뺍니다. 이상한 글·이미지를 기록 초기화 없이 지울 때 씁니다.
- 화면은 메시지를 `textContent`로만 넣습니다(HTML로 해석하지 않음). 서버는 HTML을 바꾸지 않습니다.
- **이미지**: 채팅 창에 붙여넣기(Ctrl+V)·끌어다 놓기로만 붙입니다(파일 선택 버튼 없음). 화면이 최대 800px·WebP(안 되면 JPEG)로 줄여 **data URL** 로 보내고, 서버는 파일로 저장하지 않고 채팅 기록(`ChatRecord.image`, Redis)에 그대로 넣습니다. `ChatImage` 가 JPEG·PNG·WebP 만(SVG 는 스크립트 위험으로 거절), 첫 바이트가 그 형식인지, 160,000자(약 120KB) 이하인지 확인합니다. 이미지만 보낼 때는 글이 비어도 됩니다. 한 사람이 1분에 3장(`healing-room:chat-image-rate:{visitorId}`). 화면에서 누르면 크게 보기(`#image-viewer`). 이미지 내용은 거르지 않으므로 문제가 있으면 방장·관리자 내보내기와 기록 초기화로 지웁니다.

## 7. 환경변수 (`healing-room/.env`)

| 키 | 설명 |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | PostgreSQL 접속 정보 (`DB_NAME=healing_room`) |
| `DB_USERNAME`, `DB_PASSWORD` | 앱 계정 (healing_room DB 소유자) |
| `DB_ADMIN_USERNAME`, `DB_ADMIN_PASSWORD` | 관리자 계정, 수동 작업 전용 (앱에서 사용 금지) |
| `REDIS_HOST`, `REDIS_PORT` | Redis 접속 정보 |
| `REDIS_USERNAME`, `REDIS_PASSWORD` | 전용 계정 `healing_room` (6절) |
| `REDIS_DATABASE` | Redis DB 번호 (1) |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH` | 관리자 로그인(1단계). 비밀번호는 원문 대신 `python scripts/admin_password.py` 로 만든 해시(`pbkdf2_sha256$…`)만 넣음. 비면 관리자 기능 전체가 꺼짐(404) |
| `ADMIN_TOKEN` | 관리자 키(2단계, 로그인 뒤 요청마다). 비워 두면 관리자 기능이 꺼짐(404). 전체 IP 가 보이므로 길고 무작위로 |
| `YOUTUBE_API_KEY` | YouTube Data API v3 키. "비슷한 영상" 추천·자동 추천·직접 검색에 쓰며, 비워 두면 그 기능만 꺼짐. **하루 검색 약 100번**(할당량 10,000 중 검색 1번 100, 그리고 `Search Queries per day` 100번 한도). 다 쓰면(403 또는 429) 태평양 시간 자정(한국 오후 4~5시)까지 YouTube 를 부르지 않음 |

`.env`는 절대 커밋하지 마세요. 새 키를 추가하면 `.env.example`도 함께 고치세요.

## 8. API

방문자는 `X-Visitor-Id` 헤더(UUID)로 구분합니다. 곡 신청·투표·조작은 **WebSocket으로 그 방에 접속해 있는 방문자만** 할 수 있습니다(아니면 403). 방장 전용 기능은 방장이 아니면 403입니다.
오류 응답은 `{ "message": "화면에 보여줄 문장" }` 형식입니다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/themes` | 테마 목록 (방 만들기에서 고름) |
| GET | `/api/rooms` | **로비에 보일** 방 목록 (`featured` 기본 방·사용자 방·사람 있는 기본 방, 기본 방 먼저) + 테마 + 현재 인원(`listeners`) |
| POST | `/api/rooms` | 방 만들기 `{ "name", "themeId", "maxPeople"? }` → 201. 이름 1~30자, 최대 인원 2~50(없으면 제한 없음), 한 사람이 동시에 3개까지 |
| GET | `/api/rooms/{id}` | 방 정보(테마 포함) + 환경음 목록(`ambients`). 닫힌 방은 404 |
| GET | `/api/rooms/{roomId}/queue` | 음악 상태 (재생 중 곡, 대기열, 인원, 필요한 표 수, `hostIsMe`) |
| GET | `/api/rooms/{roomId}/queue/history` | 지난 신청곡 최근 30곡 (played/skipped/removed) |
| POST | `/api/rooms/{roomId}/queue` | 곡 신청 `{ "url": "YouTube 링크 또는 id" }` → 201 `{ "id", "youtubeId", "title" }`. oEmbed 401·403 → 422 "다른 사이트 재생 막힘", 400·404 → 422 "없거나 비공개" (실패한 영상 id·이유는 서버 로그에) |
| PUT | `/api/rooms/{roomId}/queue/{itemId}/vote` | 넘기기/빼기 (내 곡이거나 방장이면 바로, 아니면 투표) → `{ "applied": bool }` |
| DELETE | `/api/rooms/{roomId}/queue/{itemId}/vote` | 투표 취소 |
| PUT | `/api/rooms/{roomId}/queue/{itemId}/duration` | 플레이어가 알아낸 영상 길이 `{ "seconds" }` |
| PUT | `/api/rooms/{roomId}/queue/playback` | **방장** `{ "action": "pause" \| "resume" \| "seek", "seconds"? }` |
| PUT | `/api/rooms/{roomId}/queue/{itemId}/move` | **방장** 대기열 순서 `{ "direction": "up" \| "down" }` |
| GET | `/api/rooms/{roomId}/recommendations` | 지금 곡 기준 추천 `{ basedOn, similar, similarAvailable, together, popular }` |
| GET | `/api/rooms/{roomId}/search?q=` | 직접 검색 (방에 있는 사람만) → `[{ youtubeId, title, channel, thumbnail, seconds }]`. 이 사이트에서 재생 가능·라이브 아님·한국 차단 아님만. 1분에 5번 넘으면 429, 할당량 소진·키 없음 503 |
| GET | `/api/rooms/{roomId}/chat` | 지난 대화 (오래된 것부터, 최대 100개) `[{ id, nickname, text, sentAt, mine }]` |
| POST | `/api/rooms/{roomId}/chat` | 채팅 보내기 `{ "nickname", "text", "image"? }` (image 는 data URL, 있으면 text 는 비어도 됨) → 201. 이미지 형식·크기가 틀리면 400, 1분에 3장 넘으면 429. 10초에 6개 넘으면 429, Redis 장애 시 503 |
| DELETE | `/api/rooms/{roomId}/chat/{messageId}` | **방장** 메시지 하나 지우기 → 204, 방 사람 모두에게 `chat-delete`. 방장 아님 403, 없는 메시지 404 |
| GET · DELETE | `/api/admin/rooms/{id}/chat`, `/api/admin/rooms/{id}/chat/{messageId}` | **관리자** 방의 최근 대화(이미지 포함) 보기 / 메시지 하나 지우기 |
| POST | `/api/rooms/{id}/reset` | **기본 방의 방장** 기록 초기화 (지난 신청곡·투표·통계·채팅) → 204, 아니면 403 |
| POST | `/api/admin/reset?roomId=` | **관리자** 기록 초기화. roomId 가 없으면 모든 방 + YouTube 검색 캐시 |
| PUT | `/api/rooms/{id}/settings` | **사람이 만든 방의 방장** 설정 바꾸기 `{ name, themeId, maxPeople }` → 방 사람 모두에게 `room-updated`. 기본 방 403 |
| POST | `/api/rooms/{id}/close` | **사람이 만든 방의 방장** 방 닫기 → 방 사람 모두에게 `room-closed` 후 4002. 기본 방 403 |
| GET | `/api/visitor` | 방문자 id 맞추기 (쿠키 → 헤더 → 새로 만들기 순) `{ id }` + 쿠키 `hr_vid` |
| POST | `/api/rooms/{roomId}/kick` | **방장** 내보내기 `{ "key" }` (접속자 목록의 가린 키) → 204. 방장 아님 403, 없는 사람 404, 자기 자신 400 |
| POST | `/api/admin/kick?roomId=&key=` | **관리자** 내보내기 (방장 포함 누구나) |
| GET | `/api/admin/rooms` | **관리자** 열려 있는 모든 방(빈 방·숨은 기본 방 포함) + 테마·인원·최대 인원 |
| PUT | `/api/admin/rooms/{id}/settings` | **관리자** 방 설정 바꾸기 `{ name, themeId, maxPeople }` → `room-updated`. 기본 방은 이름·최대 인원만(테마를 바꾸면 403) |
| DELETE | `/api/admin/rooms/{id}` | **관리자** 방 없애기(닫기) → 방 사람 모두에게 `room-closed`(`by:"admin"`) 후 4002, 채팅 삭제. 기본 방 403 |
| GET · DELETE | `/api/admin/bans`, `/api/admin/bans/{id}` | **관리자** 욕설·음란어로 제한된 사람(전체 IP·걸린 말) / 제한 풀기 |
| POST | `/api/feedback` | 로비 의견 보내기 `{ "message" }` (1~1000자) → 201. 한 브라우저 10분에 3번·사이트 전체 1시간에 60번 넘으면 429 |
| GET · DELETE | `/api/admin/feedback`, `/api/admin/feedback/{id}` | **관리자** 받은 의견 최근 200개 / 지우기 |
| POST | `/api/admin/login`, `/api/admin/logout` | 관리자 로그인 `{ username, password }` → 쿠키 `HR_ADMIN`(HttpOnly·Secure·SameSite=Strict, 8시간). 한 IP 5번 틀리면 15분 429 |
| GET | `/api/admin/presence` | **관리자** (로그인 쿠키 + `X-Admin-Token`) 모든 방 접속자: 닉네임, 전체 IP, 방문자 id 앞 8자리, 들어온 시각 |
| WS | `/ws/rooms/{roomId}?visitorId={uuid}&nickname={닉네임}` | 연결돼 있는 동안 "방에 있음". 서버 → `state`(음악), `chat`(새 채팅), `chat-clear`(채팅 지움), `chat-delete`(`id`, 메시지 하나 지움), `presence`(접속자 목록), `kicked`(내보내짐, 4001), `room-closed`(`by`, 4002), `room-full`(`max`, 4003), `room-updated`(설정 바뀜). 쿠키 `hr_vid` 가 있으면 주소의 visitorId 보다 쿠키를 씁니다. 브라우저 → `{"type":"nickname","nickname":"…"}`(닉네임 변경). 닫힌 방이면 연결 거부 |

### 화면 주소 (SiteController)
| 주소 | 보여주는 것 |
|---|---|
| `/` | `static/index.html` (스프링 기본 첫 화면) |
| `/{방id}` (`[a-z0-9][a-z0-9-]{0,11}`) | 열린 방이면 `static/index.html`(화면이 주소를 보고 입장), 아니면 404 |
| `/about`, `/privacy`, `/themes`, `/themes/{방id}` | `pages/` 의 소개 페이지·개인정보처리방침 (없으면 404) |
| `/admin` | 로그인 전 `pages/admin-login.html`, 로그인 후 `pages/admin.html`. 로그인 설정이 없으면 404 |
| `/about.html`, `/admin.html`, `/themes/index.html`, `/themes/{id}.html` | 301 로 새 주소 (`/index.html` 은 넣지 않음: 스프링이 `/` 를 보여줄 때 안에서 넘기므로 무한 이동) |

- 고정 주소가 방 주소 규칙보다 먼저 맞습니다. 방 id 는 소문자·숫자(사용자 방 8자)라 `about`·`themes`·`admin` 과 겹치지 않습니다.
- 화면(`app.js`)은 history API 로 주소를 바꾸고(`go`), 예전 `/#방id` 로 들어오면 `/방id` 로 다시 열어 서버가 확인하게 합니다.
- 오류 화면은 `static/error/404.html`·`4xx.html`·`5xx.html` (스프링이 상태 코드로 고름). API 오류는 그대로 JSON.

### 함께 듣는 음악 동작 (QueueService)
- 곡 신청 시 oEmbed로 영상 확인·제목 조회 → 대기열 맨 뒤. 기본 음악이 나오는 중이면 바로 신청곡으로 넘어갑니다.
- 곡 끝 판단: 브라우저가 알려준 영상 길이 중 **가장 긴 값**(짧게 속여 일찍 끝내지 못하게) + 여유 2초. 라이브는 길이가 없어 투표로만 넘어갑니다. 받아들이는 길이는 **12시간까지**(`QueueService.MAX_DURATION_SECONDS`, YouTube 최대 업로드 길이). 예전 6시간 상한은 7~8시간짜리 차트 모음 영상의 길이를 조용히 버려, 새로 들어온 사람이 처음부터 듣고 곡도 끝나지 않았습니다.
- 투표 통과 기준: `VotePolicy.required(n) = n / 2 + 1` (과반). **방을 떠난 사람의 표는 세지 않고**, 사람이 들어오거나 나가면 다시 판정합니다.
- 방별 `ReentrantLock` + 트랜잭션으로 한 번에 하나씩 처리하고, 끝나면 `QueueChangedEvent` → `RoomSocketHandler`가 방 전체에 각자 기준의 상태를 보냅니다. **다른 방문자의 id는 절대 응답에 넣지 않습니다.**
- 브라우저에서 영상이 재생 불가(YouTube 오류 100/101/150)면 자동으로 넘기기에 한 표를 던집니다.
- **자동 추천**(`nextAutoplay`): 대기열이 비어 다음 곡이 필요하면, **기준 곡**(방금 끝난 곡 → 이 방에서 최근 신청·자동 추천된 곡 최대 5개, 기본 음악은 기준에서 제외)마다 ① YouTube 비슷한 영상(검색 1번에 25개) ② 함께 신청된 곡을 보고, 모두 없으면 ③ 테마 인기곡에서 고릅니다. 최근 30곡에 나온 곡과 같은 노래(`SongTitles.sameSong`)는 빼고 `source=autoplay` 로 넣습니다. 그래도 없으면 테마 기본 음악(`source=default`). 한 번에 새로 YouTube 를 검색하는 건 2번까지(이미 찾아 둔 기준 곡은 할당량 없이 다시 씀). 예전에는 방금 끝난 곡 하나만 기준이라, 기본 음악을 넘기면 기본 음악 제목으로 찾다가 후보가 바닥나 기본 음악 몇 곡만 돌았습니다.
- 자동으로 넣은 곡(`added_by IS NULL`: default·autoplay)은 투표 없이 누구나 바로 넘기고, 신청곡이 들어오면 바로 신청곡으로 바뀝니다.
- 방장 조작: 재생 위치 = (`paused_at` 또는 지금) − `started_at`. 멈춤은 `paused_at` 기록, 이어서 재생은 멈춰 있던 시간만큼 `started_at`을 뒤로 미룸, 위치 이동은 `started_at`을 다시 계산합니다. 멈춘 곡은 끝나지 않습니다.
- 대기열 순서는 `queue_order`(작을수록 먼저). 새 곡은 방의 최댓값 + 1, 방장이 옮기면 이웃과 값을 맞바꿉니다.

### 기록 초기화 (RoomResetService)
- 이상한 영상이 올라왔을 때 등 방의 흔적을 지웁니다: `room_queue_item`(지난 신청곡·재생 기록, 투표는 FK CASCADE) 삭제 → 인기곡·함께 신청된 곡·자동 추천 후보에서도 사라짐, Redis 채팅 키 삭제 후 `chat-clear` 알림으로 화면도 비움. 전체 초기화는 YouTube 검색 캐시도 비웁니다. 지운 뒤 사람이 있는 방은 기본 음악부터 다시 틉니다.
- 방문자는 **기본 방의 방장만** 할 수 있습니다(화면의 "🧹 기록 초기화"). 사용자 방은 30분 비면 닫히므로 대상이 아닙니다. 관리자는 `/admin` 에서 방별·전체 초기화.
- 브라우저 `localStorage.myRequests`(내 신청 기록)는 각자 브라우저에 있어 서버에서 지울 수 없습니다.

### 직접 검색 (SearchService)
- 신청 칸에 링크가 아닌 말을 넣으면 검색합니다(`music.js` 의 `looksLikeLink`). 결과는 "검색" 탭에 썸네일·채널·길이와 함께 나오고 눌러서 신청합니다.
- `search`(100) + `videos`(1) 로 찾고, 외부 재생 가능(`status.embeddable`)·라이브 아님(길이 0 제외)·한국 차단 아님(`regionRestriction.blocked`)만 돌려줍니다.
- 방에 접속해 있는 사람만, 한 사람이 1분에 5번(`healing-room.youtube.search-per-minute`). 같은 검색어는 12시간 캐시.
- 하루 한도를 다 쓰면(403 quotaExceeded 또는 429 `Search Queries per day`, `YouTubeSearchClient.isQuotaError`) 태평양 시간 자정까지 YouTube 를 부르지 않고 503 "링크로 신청해 주세요". 자동 추천·비슷한 영상도 같은 표시를 보고 쉽니다.

### 곡 추천 (RecommendService)
- **내가 자주 신청한 곡**: 서버가 아니라 브라우저 `localStorage.myRequests`(최대 100곡, 신청 횟수·마지막 시각)에 저장합니다.
- **비슷한 영상**: YouTube Data API `search`로 검색합니다(YouTube가 "연관 영상" 검색을 없애서). 제목이 "가수 - 곡"이면 **가수 이름**으로, 아니면 정리한 제목으로 찾습니다(곡 제목으로 찾으면 같은 노래의 다른 영상만 나와서). 조건: `videoEmbeddable=true`, `videoCategoryId=10`(음악), 라이브·예정 방송(`liveBroadcastContent != none`) 제외 — 라이브는 다른 사이트에서 재생이 막히는 경우가 많아서입니다. 같은 곡은 12시간 캐시합니다. 키가 없거나 할당량 초과·오류면 빈 목록이고 나머지 추천은 그대로 나옵니다.
- **함께 신청된 곡**: 같은 방에서 앞뒤 3시간 안에 함께 신청된 곡 (함께 신청된 방이 많은 순).
- **테마 인기곡**: 같은 테마의 방들에서 최근 30일 동안 많이 신청된 곡. 기본 음악은 세지 않습니다.

### 관리자 (AdminLoginService, AdminService)
- 2단계: ① `POST /api/admin/login` → 쿠키 `HR_ADMIN`(HttpOnly·Secure(https 일 때)·SameSite=Strict·8시간) ② 관리자 API 마다 `X-Admin-Token` 헤더. 둘 다 맞아야 합니다(`AdminController.authorize`).
- 비밀번호는 원문을 두지 않고 `ADMIN_PASSWORD_HASH`(`pbkdf2_sha256$반복$소금$해시`, `scripts/admin_password.py`)만. 아이디가 틀려도 해시 계산을 똑같이 해서 걸린 시간으로 아이디를 알 수 없게 합니다.
- 한 IP(`ClientRequest.ip`) 5번 틀리면 15분 429. 로그인 표·잠금은 서버 메모리라 재시작하면 다시 로그인합니다.
- `ADMIN_USERNAME`·`ADMIN_PASSWORD_HASH` 가 비면 `/admin` 과 관리자 API 전체가 404, `ADMIN_TOKEN` 이 비면 관리자 API 가 404.

### 방장의 방 관리 (RoomService)
- 사람이 만든 방의 방장만: **설정 바꾸기**(이름·테마·최대 인원, `changeSettingsByHost` → `RoomUpdatedEvent` → `room-updated`), **방 닫기**(`closeByHost` → `RoomClosedEvent(byHost)` → 남은 사람에게 `room-closed` 후 4002, 채팅 삭제). 기본 방은 `DefaultRoomLocked`(403).
- 화면은 `room-updated` 를 받으면 방 정보를 다시 불러, 테마가 바뀌었으면 방에 다시 들어가고(20초 안이라 방장 자리 유지) 아니면 이름만 바꿉니다(`app.js` `refreshRoom`).
- **최대 인원**: 연결할 때 이미 들어와 있지 않은 사람이고 인원이 차 있으면 `room-full` 후 4003 → 화면은 로비로 가서 "방이 꽉 찼어요". 줄여도 이미 있는 사람은 그대로입니다. 로비 카드는 `3/6명`, 가득 차면 "가득 찼어요".
- 방 만들기·방 설정은 같은 창(`#create-dialog`, `lobby.js` `openRoomForm`)을 씁니다.
- **관리자**도 `/admin` 의 "방 관리" 표에서 모든 방의 이름·테마·최대 인원을 바꾸고(`changeSettingsByAdmin`) 방을 없앨 수 있습니다(`closeByAdmin`). 기본 방은 주소 `/{id}`·테마 소개 페이지와 이어져 있어 테마 변경·없애기를 막고 이름·최대 인원만 바꿉니다.
- `RoomClosedEvent.by` = `host`·`admin`·`idle`. 화면은 `room-closed` 의 `by` 를 보고 "방장이/관리자가 이 방을 닫았어요"라고 알립니다.

### 내보내기 (KickService)
- 방장은 자기 방에서(자기 자신 제외), 관리자는 어느 방에서나 내보냅니다. 사람은 **가린 키**(`RoomSessionRegistry.keyOf`: 서버를 켤 때 만든 무작위 값 + 방 + 방문자 id 의 SHA-256 앞 16자)로 가리키고, 방문자 id 는 밖에 내보내지 않습니다.
- 내보내면 그 사람의 모든 탭에 `{type:"kicked", by:"host"|"admin", until}` 을 보내고 **4001** 로 끊습니다. 화면은 다시 연결하지 않고 `/` 로 가서 슬픈 고양이 창(`#kicked-dialog`, `images/mascot-sad.webp`)을 띄웁니다.
- **10분 동안 그 방에 다시 못 들어옵니다**(방문자 id 기준, 서버 메모리). 다시 연결하면 `by:"ban"` 알림 후 4001. 들어온 순서도 지워져 다시 오면 맨 뒤(방장 아님)입니다.
- 금지 중 재접속이 **5초 간격 안으로 5번 연달아** 오면(`KickService.RAPID`·`RAPID_LIMIT`) 사람이 아니라 내보내기 기능 전부터 열려 있던 탭이 혼자 두드리는 것으로 보고 금지를 지금부터 10분으로 늘립니다. 그런 탭은 알림을 몰라 금지가 풀리면 다시 들어와 버리기 때문입니다. 사람이 몇 번 다시 눌러 보는 것으로는 늘어나지 않습니다.
- 방문자 id 는 쿠키(`hr_vid`)에도 있어 저장소만 지워서는 피할 수 없지만, 쿠키까지 지우거나 시크릿 창을 쓰면 새 사람으로 보이므로 완벽한 차단은 아닙니다(장난 방지용).

### 욕설·음란어 제한 (moderation: ProfanityFilter, ModerationService)
- 채팅(내용·닉네임, `ChatController`), 방 이름(방 만들기·방장 설정, `RoomController`), 닉네임(WebSocket 입장·변경, `RoomSocketHandler`)을 검사합니다. 관리자가 바꾸는 방 이름은 검사하지 않습니다.
- 걸리면 **IP 와 방문자 번호 둘 다 30분**(`ModerationService.BAN`) 제한: 채팅·방 만들기 403, 방 입장 거절(`kicked` `by:"profanity"` 후 4001), 들어가 있던 모든 방에서도 나갑니다(`KickService.removeEverywhere`, 같은 IP 의 다른 사람도). 화면은 슬픈 고양이 창 + 저장된 닉네임을 새 무작위로 바꿈(닉네임이 원인이면 다시 막히지 않게).
- 목록: `resources/moderation/badwords.txt`(30분 제한하는 말), `mask.txt`(가리기만 하는 말), `allow.txt`(제한하는 말을 품고 있지만 평범한 말), 출처 `THIRD_PARTY.md`(MIT·CC BY 4.0). 공개 목록을 그대로 합치면 평범한 문장의 65% 가 걸려서 **확실한 말만 골라 다듬은 목록**입니다. 평범한 단어에도 들어 있는 짧은 말은 `badwords.txt` 에 넣지 말고 `mask.txt` 로 보내거나 `allow.txt` 에 예외를 둡니다. **문서·주석·커밋 메시지에는 목록의 단어를 예시로 쓰지 않습니다**(예시는 `ProfanityFilterTests` 에만).
- 비교: 소문자·전각→반각, 한글·자모·영문·숫자만(NFKC 는 겹자음 같은 자모를 다른 코드로 바꿔 버려서 쓰지 않음). 띄어쓴 단어마다 보고, **한 글자 단어는 이웃과 붙여서도** 봄(한 글자씩 띄어 쓴 우회). 띄어쓰기를 다 지우면 앞 단어 끝과 뒷 단어 처음이 이어져 오탐이 나서입니다. 숫자를 지운 글자도 봄(숫자 끼워 넣기).
- **가리기(제한 없음)**: 평범한 말에도 들어 있는 애매한 말(`mask.txt`)은 제한하지 않고 `*` 로 가립니다(`ProfanityFilter.mask`, 채팅 내용·닉네임·방 이름). 원래 글자·띄어쓰기·기호는 두고 그 글자만 바꾸며, 기호는 건너뛰고 띄어쓰기는 넘지 않습니다. 순서: 제한 검사(`check`) → 가리기(`mask`) → 저장.
- **목록을 고치면 `ProfanityFilterTests`** 에 멀쩡한 문장·욕 문장을 더하고 돌립니다. 오탐 의견이 오면 `allow.txt` 에 더합니다.
- 관리자 화면 "이용 제한": 걸린 시각·어디서·걸린 말·전체 IP·방문자·풀리는 시각, **제한 풀기**(`GET/DELETE /api/admin/bans`). 제한은 **Redis 에 30분 TTL** 로 저장합니다(`BanRepository`, 배포·재시작해도 유지, 시간이 지나면 Redis 가 스스로 지움). Redis 가 안 되면 제한 확인은 건너뛰고(서비스는 계속), 지금 걸린 글은 그대로 거절합니다. IP·쓴 글은 로그에 남기지 않고 걸린 말만 남깁니다.

### 의견 보내기 (FeedbackService)
- `POST /api/feedback` 익명. 제어 문자 정리, 1~1000자. 한 브라우저(방문자 id) 10분에 3번, 사이트 전체 1시간에 60번(방문자 id 는 바꿀 수 있어 전체 한도도 둠). IP·연락처 저장 안 함.
- 관리자 화면에서 최근 200개 보기·지우기. 화면은 `textContent` 로만 넣습니다.

### 접속자 목록과 IP (presence)
- WebSocket 연결 때 `ClientIpHandshakeInterceptor` 가 IP 를 세션에 남깁니다. 요청이 루프백(nginx)에서 왔을 때만 `X-Forwarded-For` 첫 주소를 믿고, 밖에서 직접 온 요청의 헤더는 무시합니다(위조 방지).
- 닉네임은 연결 주소(`nickname=`)로 받고, 바꾸면 WebSocket 메시지로 받습니다(`ChatPolicy.cleanNickname` 으로 정리).
- 사람이 들어오고 나가거나 닉네임을 바꾸면 `PresencePush` 를 방 전체에 보냅니다. **방문자 id 와 전체 IP 는 절대 내보내지 않고** IP 는 `IpMask` 로 앞부분만 보냅니다. 전체 IP 는 관리자 API 에서만.
- IP 는 개인정보이므로 저장(DB·로그)하지 않고 서버 메모리에만, 접속해 있는 동안만 둡니다. 예외: 욕설·음란어로 제한된 IP 는 Redis 에 30분 TTL 로 둡니다.

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
- 테마를 추가하는 순서: ① `static/images/`에 배경 추가(webp, 1672×941) → ② `db/V{n}__...sql`로 theme, theme_ambient, theme_track, 기본 방 room(is_default=true, 짧은 id, 로비에 늘 보일 거면 featured=true) 행 추가 후 **배포 전에** 실행 → ③ `effects/{effect}.js` 작성(`parts.js` 조각 활용) → ④ `scripts/seo_pages.py` 의 `THEMES` 에 소개 글 추가 후 실행, `llms.txt`·`index.html`(`.about-themes`, JSON-LD) 갱신
- 원본 PNG 는 저장소에 넣지 말고 webp 로 바꿔 넣습니다 (방 만들기 화면에서 모든 테마 배경을 미리보기로 불러옴).
- 환경음을 추가하려면 `audio/sounds.js`의 `SOUNDS`에 키를 추가하고, 같은 키를 `theme_ambient.sound`에 씁니다.
- 브라우저 저장소(localStorage)는 `api.js`의 `store`를 쓰세요(실패해도 무시).
- **오래 열어 둔 탭**: 로비·방을 오가도 페이지를 새로 불러오지 않으므로, 탭을 닫지 않으면 배포 전 코드가 계속 돕니다(예: 내보내기 기능 전 코드가 내보내진 뒤 1초마다 재접속하고, 금지가 풀리자 다시 들어가 "아무도 없는데 누가 있는" 방이 됨). 그래서 `api.js` 가 응답의 `X-App-Version` 이 처음 값과 달라지면 `whenOutdated` 를 부르고, `app.js` 는 **로비에 있고 열린 창·쓰던 의견이 없을 때만** 새로고침합니다(방에서 듣는 중에는 끊지 않음).
- **방 만들기 창 등 `.dialog`** 는 화면보다 길면 창 안에서 스크롤하고 `.dialog-actions`(취소·만들기)는 아래에 붙어 있습니다(휴대폰). 휴대폰에서 테마 선택은 3칸·짧은 이름.
- 로비 방 카드는 한 쪽(page)에 6·4·2개(`lobby.js` `pageSize`, CSS `.room-page` 열 수와 맞춤), 좌우 버튼으로 넘깁니다.
- **소개 페이지(SEO/AEO/GEO)**: `resources/pages/themes/{기본 방 id}.html`(테마 10개), `pages/themes/index.html`, `pages/about.html`, `pages/privacy.html`, `static/images/og/{id}.jpg`, `static/sitemap.xml` 은 **`scripts/seo_pages.py` 로 만든 결과물**입니다. 직접 고치지 말고 스크립트의 `THEMES` 등을 고친 뒤 `python scripts/seo_pages.py` 로 다시 만들어 함께 커밋하세요(Pillow·맑은 고딕 필요). 주소는 `.html` 없이 `SiteController` 가 보여줍니다(`/about`, `/themes`, `/themes/cafe`). 테마를 추가하면 스크립트와 `llms.txt` 에도 넣습니다.
- **개인정보처리방침**(`/privacy`, `seo_pages.py` 의 `privacy()`): 저장하는 정보(DB 칼럼, Redis 키, 쿠키·localStorage 키, 로그)나 외부 서비스를 추가·변경하면 **이 페이지의 표도 함께 고치고** `PRIVACY_SINCE` 날짜와 "바뀐 내용"을 갱신하세요. 확인하지 않은 보안 주장은 쓰지 않습니다.
- **Google Analytics (GA4, `G-DYY0NFW8RR`)**: 태그는 `static/index.html` 과 `seo_pages.py` 의 `GA_ID`(소개·개인정보 페이지). 관리자 화면·오류 화면에는 넣지 않습니다. 페이지 조회는 GA "향상된 측정"이 주소 바뀜(history API)까지 셉니다. 이벤트는 `js/analytics.js` 의 `track()`: `room_enter`(theme, room_type), `room_create`(theme, max_people), `song_request`, `chat_send`, `feedback_send`, `support_open`. **방문자 id·닉네임·채팅·곡 제목 등 사람을 알아볼 수 있거나 사용자가 쓴 내용은 보내지 않습니다.** 이벤트·수집 항목을 바꾸면 개인정보처리방침도 고치세요.
- 로비·소개 페이지 맨 아래 footer 에 만든 사람(김성민, GitHub 프로필)과 개인정보처리방침 링크가 있습니다. 구조화 데이터에는 `Person`(`#author`)으로 들어갑니다.
- **검색·AI 노출(SEO/AEO)**: `index.html` 의 og 메타 태그·JSON-LD(WebSite, WebApplication, FAQPage), 로비 아래 소개·자주 묻는 질문(`.about`), `robots.txt`·`sitemap.xml`·`llms.txt`. **FAQ 를 바꾸면 JSON-LD 의 FAQPage 도 같은 내용으로** 바꾸고, 기능이 바뀌면 `llms.txt` 도 고치세요. 테마를 추가하면 `.about-themes` 와 JSON-LD `featureList` 에도 넣습니다.
- **소리는 음악과 환경음을 따로 켜고 끕니다** (`app.js`의 `setMusic`, `setAmbient`).
  - 음악이 소리를 내며 재생되기 시작하면 환경음을 자동으로 끄고("환경음 쉬는 중"), 음악을 끄거나 멈추면 자동으로 껐던 환경음만 다시 켭니다. 곡 사이 잠깐 끊김에 깜빡이지 않도록 멈춤은 4초 기다렸다 알립니다(`music.js`의 `onMusicAudible`).
  - 사용자가 직접 누른 켜기/끄기는 항상 우선하며 `localStorage.musicOn`, `ambientOn`에 기억합니다.
  - 음악이 나오는 중에 사용자가 환경음을 직접 켜면(버튼 또는 **쉬는 중에 믹서 슬라이더를 움직임**, `mixer.js` `onMixerInput`) "음악과 함께 듣기"로 보고 `localStorage.ambientWithMusic` 에 기억해, 다음 곡이 시작돼도 자동으로 끄지 않습니다. 직접 끄면 풀립니다. (예전에는 직접 켜도 다음 곡에서 또 꺼져 "환경음 조절이 안 된다"는 의견이 왔음)
  - 기본값은 음악·환경음 모두 켜짐입니다. 브라우저 정책상 소리는 사용자가 한 번 누른 뒤에만 나므로, 새로고침·주소로 들어오면 음소거로 틀어 두었다가 페이지 어디든 처음 누를 때(`app.js` 의 `unlockSound`) 켜 둔 소리를 냅니다. 그 전에는 음악 버튼이 "🔈 소리 켜기"로 보입니다. 단, 브라우저가 클릭 없이도 소리를 허락하면(크롬: 이 사이트에서 소리를 자주 들은 경우, 엣지·파이어폭스: 자동 재생 허용, `app.js` `autoplayAllowed`) 처음부터 소리를 냅니다. 그래도 막혀서 8초 넘게 소리가 안 나면(`music.js` `isMusicStuck`) 음소거로 돌리고 첫 누름을 기다립니다.
- YouTube: IFrame Player API를 씁니다(API 키 불필요). 음악 창은 기본·최소화·최대화 3단계이며 `localStorage.musicView`에 기억합니다.
  - 자막은 끕니다(`cc_load_policy: 0`, 재생될 때마다 `hideCaptions`).
  - **YouTube 플레이어는 아무도 직접 조작할 수 없습니다.** `controls: 0, disablekb: 1, fs: 0`에 투명한 막(`.player-shield`)을 덮었고, 멈춰 있으면 동기화 때 다시 재생합니다. 볼륨만 별도 슬라이더(`musicVolume`)로 조절합니다.
  - 위치 맞추기는 서버가 길이를 알거나, 아직 몰라도 **내 플레이어가 길이를 알고 라이브가 아니면**(`music.js` `hasLength`) 합니다. 서버가 길이를 모르면 0초부터 틀었다가 재생이 시작되면 다음 동기화(5초 안)에 맞춥니다.
  - 위치 맞추기(`seekTo`)를 하면 YouTube 가 제목·일시정지·공유 버튼을 몇 초 띄웁니다. 옮긴 뒤 불러오느라 또 밀려 5초마다 옮기면 버튼이 사라지지 않으므로 `music.js` 의 `catchUp` 이 횟수를 아낍니다(1초 앞으로, 20초에 한 번, 3번 연속 실패하면 2분 쉼, 방장이 옮기면 바로).
  - 재생 위치 바는 모두에게 보이고, **방장에게만** 멈춤 버튼과 위치 이동이 켜집니다(서버 API로 모두에게 적용). 방장에게는 대기열 ▲▼와 바로 넘기기/빼기가 보입니다.
  - 참고: YouTube 이용약관은 재생 중인 플레이어를 가리거나 200×200px보다 작게 만드는 것을 금지합니다. 최소화는 사용자 요청으로 넣은 기능이니 공개 서비스로 낼 때 다시 검토하세요.
- 기본 음악을 추가할 때는 **임베드 재생이 되는지 꼭 확인**하세요. oEmbed가 성공해도 실제 플레이어에서 오류 150(외부 재생 금지)이 날 수 있습니다(라이브 방송에 많음).

## 10. 코드 스타일

- 주석과 사용자에게 보이는 문구는 **한국어**로 씁니다.
- 모든 글 파일은 **UTF-8**(BOM 없음)입니다(`.editorconfig`). 한글이 깨져 보이면 파일이 아니라 여는 프로그램의 인코딩 문제입니다(IntelliJ 의 .properties 기본값, Windows PowerShell 5, 서버 셸의 LANG). 서버 계정은 `~/.bashrc` 에 `LANG=C.UTF-8` 을 넣어 두었습니다.
- Java: 생성자 주입, DTO는 `record`, 도메인별 패키지(3절)를 따릅니다.
- 주변 코드의 스타일과 주석 밀도를 따르세요.
- 기능을 추가하면 `@WebMvcTest` + `@MockitoBean` 컨트롤러 테스트나 단위 테스트를 함께 작성하세요. 이 테스트들은 DB 없이 돌아갑니다.

## 11. 운영 서버 배포

- 주소: **https://healing.kimseongmin.co.kr** (nginx → `127.0.0.1:18280`). SSH 별칭 `ai-server`(`~/.ssh/config`).
- DB·Redis 가 같은 서버에 있어서 운영 `.env` 는 `127.0.0.1` 로 접속합니다.
- 서버 폴더
  ```
  ~/healing-room/
  ├─ releases/{날짜}-{커밋}/healing-room.jar   최근 5개 보관
  ├─ current → releases/…                     실행 중인 버전
  ├─ shared/.env                              운영 설정 (권한 600, server.address=127.0.0.1)
  └─ deploy/                                  저장소 deploy/ 복사본
  ```
- 실행: systemd `healing-room` (Java: `~/apps/jre17`), 로그는 `journalctl -u healing-room -f` (seongmin 은 adm 그룹이라 sudo 없이 볼 수 있음)
- **새 버전 배포 = `main` 에 푸시** (GitHub Actions `.github/workflows/deploy.yml`, `healing-room/**` 가 바뀐 푸시만). 테스트(`-Pci`, DB 가 필요한 `@Tag("integration")` 제외) → 빌드 → 배포 전용 키로 jar 를 보내면 서버의 `deploy/ci-receive.sh` 가 새 release 저장 → current 교체 → 재시작 → 상태 확인, **실패하면 이전 버전으로 자동 복구**합니다. Actions 탭에서 수동 실행(workflow_dispatch)도 됩니다.
  - 배포 전용 키는 서버 `authorized_keys` 에 `restrict,command="…/ci-receive.sh"` 로 묶여 있어 **배포 말고는 아무것도 못 합니다**(쉘·다른 명령 불가). 키와 서버 정보는 저장소 Secrets(`DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`, `DEPLOY_HOST`, `DEPLOY_PORT`, `DEPLOY_USER`).
  - `deploy/ci-receive.sh` 를 고치면 서버에 **직접 복사**해야 합니다(배포 키로는 바꿀 수 없음): `scp deploy/ci-receive.sh ai-server:healing-room/deploy/`
  - 손으로 배포·되돌리기: 저장소 루트에서 `bash deploy/deploy.sh`, `bash deploy/deploy.sh rollback`.
- **sudo 가 필요한 설치**(서비스 등록, nginx, 인증서)는 `deploy/install-root.sh` 를 사람이 직접 실행합니다. AI 는 sudo 비밀번호를 다루지 않습니다. 재시작만 `/etc/sudoers.d/healing-room` 으로 비밀번호 없이 허용합니다.
- nginx 는 **`sites-available/healing.kimseongmin.co.kr` 파일만** 이 앱 것입니다. 기존 메인 도메인 설정 파일은 다른 서비스가 쓰므로 절대 수정하지 마세요. 적용 전에는 항상 `nginx -t`.
- 운영 서버에 붙은 앱과 **같은 DB 를 쓰는 서버를 로컬에서 동시에 띄우지 마세요**(대기열 스케줄러가 두 번 돕니다). 로컬 개발용 DB 를 따로 두는 게 좋습니다.
- **엔티티를 바꾸는 배포**는 마이그레이션을 먼저 운영 DB 에 실행한 뒤 푸시합니다(`ddl-auto=validate` 라 순서가 바뀌면 새 버전이 시작을 거부하고 자동 복구됨). 칸·테이블 추가는 이전 버전에 영향이 없습니다.
- **관리자 설정**(`shared/.env` 의 `ADMIN_USERNAME`·`ADMIN_PASSWORD_HASH`·`ADMIN_TOKEN`)은 사람이 직접 넣습니다. 값은 대화·커밋에 옮겨 적지 않습니다.
- **DNS**(가비아): `healing` 은 A 기록(서버 IP), 같은 이름에 구글 서치 콘솔 인증 TXT. CNAME 은 다른 기록과 함께 둘 수 없으므로 쓰지 않습니다. 기록을 바꿀 땐 새 기록을 먼저 추가하고 예전 기록을 나중에 지웁니다(비는 순간의 "없음" 답을 최대 하루 저장하는 DNS 가 있음).
- **검색 등록**: 네이버 서치어드바이저(메타 태그 인증), 구글 서치 콘솔(DNS TXT 인증). 소개 페이지가 바뀌면 sitemap 을 다시 제출합니다. 카카오톡 미리보기는 카카오 공유 디버거에서 캐시 초기화.

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
- [x] 접속자 목록 (닉네임, 가린 IP), 관리자 화면 (전체 IP)
- [x] 자동 추천 재생 (신청곡이 없으면 비슷한 영상을 이어서)
- [x] 테마 추가: 숲, 비 오는 숲, 비 오는 카페, 눈 산장, 노을 해변, 우주정거장, 깊은 우주, 라운지 클럽 (V7)
- [x] 클럽 방 비트 효과 (조명 분당 100박 + 환경음 beat)
- [x] 곡 직접 검색, 자막 끄기, 자동 추천 개선(기준 곡 여러 개, 같은 노래 거르기)
- [x] 테마마다 기본 방(V8), 로비는 featured·사용자 방·사람 있는 방만(V10), 방 카드 넘기기
- [x] 검색엔진·AI 노출 (소개 페이지 14개, JSON-LD, sitemap, llms.txt, 링크 미리보기), 마스코트
- [x] 깔끔한 주소(SiteController), 404·5xx 안내 화면
- [x] 의견 보내기(V9), 관리자 로그인 + 관리자 키, 서버비 후원 버튼
- [x] 방장·관리자 내보내기 (10분 재입장 금지, 슬픈 고양이 창), 방문자 id 쿠키 함께 저장
- [x] 방장의 방 설정 바꾸기(이름·테마·최대 인원)·방 닫기, 방 만들 때 최대 인원(V11)
- [x] 만든 사람 footer, 개인정보처리방침(`/privacy`)
- [x] 욕설·음란어 거르기 + IP·방문자 30분 제한, 관리자 제한 풀기
- [x] 관리자 방 관리 (모든 방의 이름·테마·최대 인원 바꾸기, 방 없애기. 기본 방은 이름·최대 인원만)
- [ ] 테마별 배경 영상 (선택)
- 방문자 로그인은 **만들지 않습니다** (익명 서비스). 로그인은 관리자 화면에만 있습니다.
