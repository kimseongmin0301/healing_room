# 🔥 힐링룸 (Healing Room)

> 불멍, 바다멍, 밤하늘멍. 화면을 켜두고 멍하니 쉬는 웹 공간

테마가 있는 방에 들어가면 **실사 배경이 살아 움직이고**, 환경음과 음악이 흐르며, **지금 같은 방에 있는 사람 수**가 보입니다.
로그인 없이 누구나 들어와서 쉬다 가면 됩니다.

| 불멍 · 겨울 오두막 | 바다멍 · 달빛 바다 |
|---|---|
| ![불멍](healing-room/src/main/resources/static/images/cabin.webp) | ![바다멍](healing-room/src/main/resources/static/images/sea.webp) |

## ✨ 주요 기능

- **살아 움직이는 배경**: 사진 위에 캔버스 효과를 얹었습니다
  - 불멍: 불꽃 일렁임, 불빛·랜턴 깜빡임, 피어오르는 불씨, 창밖에 내리는 눈. **클릭하면 장작이 들어가요**
  - 바다멍: 물결, 달빛 반짝임, 별 반짝임. **하늘을 클릭하면 별똥별이 떨어져요**
- **함께 있는 사람 수**: Redis로 방마다 실시간 인원을 셉니다
- **방 정보는 DB에서 관리**: 방 추가와 수정은 SQL로 합니다
- 🚧 개발 중: 환경음 믹서, 방별 YouTube 플레이리스트, 배경 영상, 로비, 방 추가(밤하늘, 숲, 모래사장, 클럽)

## 🛠 기술 스택

| 영역 | 기술 |
|---|---|
| Backend | Java 17 · Spring Boot 4.1 · Spring Data JPA · Spring Data Redis · Gradle |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Frontend | HTML · CSS · JavaScript (ES Modules, 프레임워크 없음) · Canvas 2D |

## 🚀 시작하기

### 1. 준비물
- JDK 17 이상
- PostgreSQL, Redis 서버

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
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_USERNAME` / `REDIS_PASSWORD` | Redis 접속 정보 |
| `REDIS_DATABASE` | 사용할 Redis DB 번호 (기본 1) |

### 3. DB 만들기 (최초 1회)
관리자 계정으로 DB를 만들고, 앱 계정을 소유자로 지정합니다.

```sql
CREATE DATABASE healing_room OWNER <DB_USERNAME>;
```

그다음 **앱 계정으로** `healing-room/db/`의 SQL을 번호 순서대로 실행합니다.

```bash
psql -h <DB_HOST> -p <DB_PORT> -U <DB_USERNAME> -d healing_room -f healing-room/db/V1__create_room.sql
```

> ⚠️ 이 앱은 **실행할 때 DB를 절대 자동으로 만들거나 바꾸지 않습니다** (`ddl-auto=validate`).
> 엔티티와 테이블이 다르면 앱이 시작을 거부하니, 스키마 변경은 항상 `db/`에 SQL을 추가해서 직접 실행하세요.

### 4. 실행

```bash
cd healing-room
./gradlew bootRun        # Windows: gradlew.bat bootRun
```

브라우저에서 http://localhost:8080 에 접속하세요. 주소 끝에 `#sea`를 붙이면 바다멍 방으로 바로 들어갑니다.

### 5. 테스트

```bash
cd healing-room
./gradlew test
```

## 📁 프로젝트 구조

```
├─ README.md / AGENTS.md / CLAUDE.md
├─ design/                       디자인 시안(SVG·PNG)과 초기 프로토타입
└─ healing-room/                 Spring Boot 앱
   ├─ db/                        DB 스키마 SQL (수동 실행)
   └─ src/main/
      ├─ java/com/healingroom/
      │  ├─ room/                방 엔티티, 리포지토리, 서비스, API
      │  └─ presence/            현재 인원 (Redis)
      └─ resources/static/       프론트엔드
         └─ js/effects/          방별 시각 효과
```

## 🔌 API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/rooms` | 방 목록 |
| GET | `/api/rooms/{id}` | 방 상세 |
| PUT | `/api/rooms/{id}/presence/{visitorId}` | 방에 있다는 신호, 현재 인원 반환 |
| DELETE | `/api/rooms/{id}/presence/{visitorId}` | 방에서 나감 |

## ➕ 방 추가하기

1. `healing-room/src/main/resources/static/images/`에 배경 이미지를 넣습니다
2. `healing-room/db/V{다음번호}__add_{방}.sql`로 `room` 행을 추가하고 실행합니다
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
- [x] 현재 인원 표시 (Redis)
- [ ] 환경음 믹서
- [ ] 방별 YouTube 플레이리스트
- [ ] 배경 영상 지원
- [ ] 방 추가: 밤하늘, 숲, 모래사장, 클럽
- [ ] 로비 화면

## 📝 참고

- 배경 음악은 YouTube IFrame Player API로 재생합니다(API 키 불필요).
- 배경 이미지를 공개 서비스에 쓰려면 이미지 라이선스를 확인하세요.
