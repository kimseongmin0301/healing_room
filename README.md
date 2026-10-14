# 🔥 힐링룸 (Healing Room)

> 불멍, 바다멍, 밤하늘멍. 화면을 켜두고 멍하니 쉬는 웹 공간

테마가 있는 방에 들어가면 **실사 배경이 살아 움직이고**, 환경음이 흐르며, 같은 방 사람들과 **같은 음악을 함께 듣습니다.**
로그인 없이 누구나 들어와서 쉬다 가면 됩니다.

| 불멍 · 겨울 오두막 | 바다멍 · 달빛 바다 |
|---|---|
| ![불멍](healing-room/src/main/resources/static/images/cabin.webp) | ![바다멍](healing-room/src/main/resources/static/images/sea.webp) |

## ✨ 주요 기능

- **살아 움직이는 배경**: 사진 위에 캔버스 효과를 얹었습니다
  - 불멍: 불꽃 일렁임, 불빛·랜턴 깜빡임, 피어오르는 불씨, 창밖에 내리는 눈. **클릭하면 장작이 들어가요**
  - 바다멍: 물결, 달빛 반짝임, 별 반짝임. **하늘을 클릭하면 별똥별이 떨어져요**
- **로비**: 방 카드와 방마다 지금 쉬고 있는 사람 수
- **함께 듣는 음악 (YouTube)**
  - 같은 방 사람들은 같은 곡을 같은 위치로 들어요 (라디오처럼 동기화)
  - 누구나 YouTube 링크로 곡을 신청할 수 있어요. 신청곡이 없으면 방 기본 음악이 나와요
  - 다른 사람이 신청한 곡은 **방 인원의 과반(50% 초과)이 투표**하면 넘기거나 뺄 수 있어요. 내 신청곡은 바로 넘기거나 뺄 수 있어요
  - 다음 곡 목록과 지난 신청곡 목록, 음악 창 최소화·최대화
- **환경음 믹서**: 장작, 바람, 비, 파도, 풀벌레 소리를 녹음 파일 없이 브라우저에서 합성하고 슬라이더로 섞어요
- **방 정보는 DB에서 관리**: 방, 환경음, 기본 음악을 SQL로 추가해요
- 🚧 다음 작업: 방 추가(밤하늘, 숲, 모래사장, 클럽), 배경 영상 적용

## 🛠 기술 스택

| 영역 | 기술 |
|---|---|
| Backend | Java 17 · Spring Boot 4.1 · Spring Data JPA · Spring WebSocket · Gradle |
| Database | PostgreSQL 16 |
| Frontend | HTML · CSS · JavaScript (ES Modules, 프레임워크 없음) · Canvas 2D · Web Audio · YouTube IFrame API |

## 🚀 시작하기

### 1. 준비물
- JDK 17 이상
- PostgreSQL 서버

### 2. 환경변수 설정
`healing-room/.env.example`을 복사해서 `healing-room/.env`를 만들고 값을 채웁니다.

```bash
cp healing-room/.env.example healing-room/.env
```

| 키 | 설명 |
|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | PostgreSQL 주소, 포트, DB 이름(`healing_room`) |
| `DB_USERNAME` / `DB_PASSWORD` | 앱이 사용할 계정 |
| `DB_ADMIN_USERNAME` / `DB_ADMIN_PASSWORD` | DB 생성용 관리자 계정 (앱은 사용하지 않음) |
| `REDIS_*` | Redis 접속 정보 (현재 사용하지 않음, 서버 여러 대로 확장할 때 사용 예정) |

### 3. DB 만들기 (최초 1회)
관리자 계정으로 DB를 만들고, 앱 계정을 소유자로 지정합니다.

```sql
CREATE DATABASE healing_room OWNER <DB_USERNAME>;
```

그다음 **앱 계정으로** `healing-room/db/`의 SQL을 번호 순서대로(V1 → V2 → …) 실행합니다.

```bash
psql -h <DB_HOST> -p <DB_PORT> -U <DB_USERNAME> -d healing_room -f healing-room/db/V1__create_room.sql
```

> 다른 세션이 테이블을 잡고 있으면 마이그레이션이 계속 기다리며 서비스까지 멈출 수 있어요. SQL 앞에 `SET lock_timeout = '5s';`를 붙여 실행하세요.

> ⚠️ 이 앱은 **실행할 때 DB를 절대 자동으로 만들거나 바꾸지 않습니다** (`ddl-auto=validate`).
> 엔티티와 테이블이 다르면 앱이 시작을 거부하니, 스키마 변경은 항상 `db/`에 SQL을 추가해서 직접 실행하세요.

### 4. 실행

```bash
cd healing-room
./gradlew bootRun        # Windows: gradlew.bat bootRun
```

브라우저에서 http://localhost:8080 에 접속하세요. 같은 네트워크의 다른 기기에서는 `http://{PC의 IP}:8080` 으로 들어오면 됩니다.
주소 끝에 `#sea`를 붙이면 바다멍 방으로 바로 들어갑니다.

> 서버는 **한 대만** 띄우세요. 음악 대기열 진행과 접속자 목록을 서버 메모리에서 관리해요.

### 5. 테스트

```bash
cd healing-room
./gradlew test
```

## 📁 프로젝트 구조

백엔드는 **도메인별로 controller / service / repository / entity / dto** 를 나눈 구조입니다. 자세한 설명은 [AGENTS.md](AGENTS.md)에 있어요.

```
├─ README.md / AGENTS.md / CLAUDE.md
├─ design/                              디자인 시안(SVG·PNG)과 초기 프로토타입
└─ healing-room/                        Spring Boot 앱
   ├─ db/                               DB 스키마 SQL (수동 실행)
   └─ src/main/
      ├─ java/com/healingroom/
      │  ├─ domain/
      │  │  ├─ room/                    방       (controller, service, repository, entity, dto)
      │  │  ├─ queue/                   함께 듣는 음악 (controller, service, repository, entity, dto,
      │  │  │                                     event, exception, client, util)
      │  │  └─ presence/                방 접속자 (WebSocket handler, service)
      │  └─ global/                     공통 설정(config), 예외 처리(exception)
      └─ resources/static/              프론트엔드
         ├─ js/                         app, lobby, music, room-socket, scene, api
         ├─ js/audio/                   환경음 합성과 믹서
         └─ js/effects/                 방별 시각 효과
```

## 🔌 API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/rooms` | 방 목록 + 방별 현재 인원 |
| GET | `/api/rooms/{id}` | 방 정보 + 환경음 목록 |
| GET | `/api/rooms/{roomId}/queue` | 지금 재생 중인 곡, 대기열, 투표 현황 |
| GET | `/api/rooms/{roomId}/queue/history` | 지난 신청곡 (최근 30곡) |
| POST | `/api/rooms/{roomId}/queue` | 곡 신청 `{ "url": "YouTube 링크" }` |
| PUT / DELETE | `/api/rooms/{roomId}/queue/{itemId}/vote` | 넘기기·빼기 투표 / 취소 |
| PUT | `/api/rooms/{roomId}/queue/{itemId}/duration` | 영상 길이 보고 (플레이어가 자동으로 호출) |
| WebSocket | `/ws/rooms/{roomId}?visitorId=...` | 방 접속. 음악 상태가 바뀔 때마다 받음 |

방문자는 로그인 대신 브라우저에 저장된 UUID를 `X-Visitor-Id` 헤더로 보냅니다.

## ➕ 방 추가하기

1. `healing-room/src/main/resources/static/images/`에 배경 이미지를 넣습니다
2. `healing-room/db/V{다음번호}__add_{방}.sql`로 `room`, `room_ambient`(환경음), `room_playlist`(기본 음악) 행을 추가하고 실행합니다
   - 기본 음악은 **다른 사이트에서 재생(임베드)이 되는 영상**인지 꼭 확인하세요. 라이브 방송은 막혀 있는 경우가 많아요
3. `static/js/effects/{effect}.js`에 효과를 작성합니다. `cabin.js`를 복사해서 시작하면 편해요

```js
export function create(scene) {
  return {
    draw(t) { /* 매 프레임 호출 */ },
    click(u, v) { /* 클릭한 위치 (이미지 기준 0~1) */ },
  };
}
```

## 🤖 AI 도구로 작업하기

이 프로젝트는 AI와 함께 개발합니다. 규칙과 맥락은 **[AGENTS.md](AGENTS.md)** 한 곳에 정리되어 있어요.

| 도구 | 사용 방법 |
|---|---|
| Claude Code | `CLAUDE.md`가 `AGENTS.md`를 자동으로 불러옵니다 |
| Codex CLI | 저장소 루트의 `AGENTS.md`를 자동으로 읽습니다 |
| ChatGPT (웹) | 대화를 시작할 때 `AGENTS.md`와 `README.md`를 첨부하세요 |

## 🗺 로드맵

- [x] 불멍, 바다멍 방 · 실사 배경 + 움직이는 효과
- [x] PostgreSQL에서 방 정보 관리
- [x] 로비 화면 · 실시간 인원
- [x] 환경음 믹서
- [x] 함께 듣는 YouTube 음악 (신청, 동기화, 과반 투표, 지난 신청곡)
- [x] 배경 영상 지원 (영상 파일 준비 중)
- [ ] 방 추가: 밤하늘, 숲, 모래사장, 클럽

## 📝 참고

- 음악은 YouTube IFrame Player API로 재생합니다(API 키 불필요). YouTube 이용약관은 재생 중인 플레이어를 가리는 것을 금지하니, 공개 서비스로 낼 때는 음악 창 최소화 기능을 다시 검토하세요.
- 배경 이미지를 공개 서비스에 쓰려면 이미지 라이선스를 확인하세요.
