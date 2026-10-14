-- 테마(배경)와 방(사람들이 모이는 곳)을 분리한다.
--  - 기존 room → theme: 불멍·바다멍 같은 배경, 환경음, 기본 음악
--  - 새 room: 사용자가 테마를 골라 만드는 방. 기본 방(cabin, sea)은 항상 열려 있다
--  - 대기열은 방에 속한다. 방장은 재생을 멈추고(paused_at) 순서를 바꿀(queue_order) 수 있다

-- 1) 테마로 이름 바꾸기
ALTER TABLE room RENAME TO theme;
ALTER TABLE theme RENAME CONSTRAINT room_pkey TO theme_pkey;
COMMENT ON TABLE theme IS '테마 (배경, 효과, 환경음, 기본 음악의 묶음)';

ALTER TABLE room_ambient RENAME TO theme_ambient;
ALTER TABLE theme_ambient RENAME COLUMN room_id TO theme_id;
ALTER TABLE theme_ambient RENAME CONSTRAINT room_ambient_room_id_fkey TO theme_ambient_theme_id_fkey;
COMMENT ON TABLE theme_ambient IS '테마별 환경음';

ALTER TABLE room_playlist RENAME TO theme_track;
ALTER TABLE theme_track RENAME COLUMN room_id TO theme_id;
ALTER TABLE theme_track RENAME CONSTRAINT room_playlist_room_id_fkey TO theme_track_theme_id_fkey;
COMMENT ON TABLE theme_track IS '테마별 기본 음악 (대기열이 비었을 때 재생)';

-- 2) 방
CREATE TABLE room (
    id          VARCHAR(12)  PRIMARY KEY,
    theme_id    VARCHAR(50)  NOT NULL REFERENCES theme (id),
    name        VARCHAR(40)  NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT false,
    created_by  VARCHAR(36),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    emptied_at  TIMESTAMPTZ,
    closed_at   TIMESTAMPTZ
);
CREATE INDEX room_open_idx ON room (created_at) WHERE closed_at IS NULL;
COMMENT ON TABLE  room            IS '방. 사용자가 테마를 골라 만들고, 30분 동안 비어 있으면 닫힌다';
COMMENT ON COLUMN room.id         IS '방 id (주소 #id). 기본 방은 테마 id 와 같다';
COMMENT ON COLUMN room.is_default IS '항상 열려 있는 기본 방';
COMMENT ON COLUMN room.created_by IS '만든 방문자 id (UUID)';
COMMENT ON COLUMN room.emptied_at IS '마지막 사람이 나간 시각. 사람이 있으면 NULL';
COMMENT ON COLUMN room.closed_at  IS '닫힌 시각. 열려 있으면 NULL';

-- 기존 불멍·바다멍은 기본 방으로 (기존 대기열 기록이 그대로 이어지도록 id 를 같게)
INSERT INTO room (id, theme_id, name, is_default)
SELECT id, id, title, true FROM theme;

-- 3) 대기열은 방에 속한다
ALTER TABLE room_queue_item DROP CONSTRAINT room_queue_item_room_id_fkey;
ALTER TABLE room_queue_item ADD CONSTRAINT room_queue_item_room_id_fkey
    FOREIGN KEY (room_id) REFERENCES room (id) ON DELETE CASCADE;

ALTER TABLE room_queue_item ADD COLUMN paused_at TIMESTAMPTZ;
ALTER TABLE room_queue_item ADD COLUMN queue_order BIGINT;
UPDATE room_queue_item SET queue_order = id;
ALTER TABLE room_queue_item ALTER COLUMN queue_order SET NOT NULL;
COMMENT ON COLUMN room_queue_item.paused_at   IS '방장이 일시정지한 시각. 재생 중이면 NULL';
COMMENT ON COLUMN room_queue_item.queue_order IS '대기열 순서 (작을수록 먼저). 방장이 바꿀 수 있다';

-- 추천(테마 인기곡, 함께 신청된 곡) 조회용
CREATE INDEX room_queue_item_requests_idx ON room_queue_item (youtube_id, room_id) WHERE added_by IS NOT NULL;
