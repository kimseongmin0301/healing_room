-- 힐링룸 방 테이블
-- 애플리케이션은 스키마를 만들거나 바꾸지 않는다 (ddl-auto=validate).
-- 스키마 변경은 이 폴더에 V{번호}__설명.sql 을 추가하고 직접 실행한다.

CREATE TABLE room (
    id          VARCHAR(50)  PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    title       VARCHAR(100) NOT NULL,
    subtitle    VARCHAR(200) NOT NULL,
    hint        VARCHAR(200) NOT NULL,
    background  VARCHAR(300) NOT NULL,
    effect      VARCHAR(50)  NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE  room            IS '힐링룸';
COMMENT ON COLUMN room.id         IS '방 키 (URL #id 로도 사용)';
COMMENT ON COLUMN room.name       IS '하단 탭 이름';
COMMENT ON COLUMN room.title      IS '화면 제목';
COMMENT ON COLUMN room.subtitle   IS '제목 아래 한 줄 설명';
COMMENT ON COLUMN room.hint       IS '인터랙션 안내 문구';
COMMENT ON COLUMN room.background IS '배경 이미지 경로';
COMMENT ON COLUMN room.effect     IS '프론트 효과 모듈 이름 (static/js/effects/{effect}.js)';
COMMENT ON COLUMN room.sort_order IS '탭 정렬 순서';
