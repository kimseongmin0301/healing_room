-- 환경음 믹서, 방별 기본 음악, 함께 듣는 음악 대기열(투표), 배경 영상

-- 배경 영상 (있으면 이미지 대신 영상을 재생)
ALTER TABLE room ADD COLUMN background_video VARCHAR(300);
COMMENT ON COLUMN room.background_video IS '배경 루프 영상 경로 (없으면 background 이미지 사용)';

-- 방마다 믹서에 나오는 환경음
CREATE TABLE room_ambient (
    id             BIGSERIAL    PRIMARY KEY,
    room_id        VARCHAR(50)  NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    sound          VARCHAR(50)  NOT NULL,
    label          VARCHAR(50)  NOT NULL,
    default_volume INTEGER      NOT NULL DEFAULT 50 CHECK (default_volume BETWEEN 0 AND 100),
    sort_order     INTEGER      NOT NULL DEFAULT 0,
    UNIQUE (room_id, sound)
);
COMMENT ON TABLE  room_ambient                IS '방별 환경음';
COMMENT ON COLUMN room_ambient.sound          IS '소리 종류 (static/js/audio/sounds.js 의 키: fire, wind, rain, waves, crickets ...)';
COMMENT ON COLUMN room_ambient.default_volume IS '처음 들어왔을 때 볼륨 (0~100)';

-- 방마다 기본 음악: 대기열이 비었을 때 순서대로 돌아가며 재생
CREATE TABLE room_playlist (
    id          BIGSERIAL    PRIMARY KEY,
    room_id     VARCHAR(50)  NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    youtube_id  VARCHAR(20)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    UNIQUE (room_id, youtube_id)
);
COMMENT ON TABLE  room_playlist            IS '방별 기본 음악 (대기열이 비었을 때 재생)';
COMMENT ON COLUMN room_playlist.youtube_id IS 'YouTube 영상 id (watch?v= 뒤 11자리)';

-- 함께 듣는 음악 대기열. 방문자가 등록한 곡과, 대기열이 비어 자동으로 넣은 기본 음악이 모두 쌓인다
CREATE TABLE room_queue_item (
    id                BIGSERIAL    PRIMARY KEY,
    room_id           VARCHAR(50)  NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    youtube_id        VARCHAR(20)  NOT NULL,
    title             VARCHAR(200) NOT NULL,
    added_by          VARCHAR(36),
    status            VARCHAR(10)  NOT NULL DEFAULT 'queued'
                      CHECK (status IN ('queued', 'playing', 'played', 'skipped', 'removed')),
    duration_seconds  INTEGER,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at        TIMESTAMPTZ,
    ended_at          TIMESTAMPTZ
);
CREATE INDEX room_queue_item_active_idx ON room_queue_item (room_id, status, id)
    WHERE status IN ('queued', 'playing');
COMMENT ON TABLE  room_queue_item                  IS '방 음악 대기열 (재생 기록 포함)';
COMMENT ON COLUMN room_queue_item.added_by         IS '등록한 방문자 id (UUID). NULL 이면 기본 음악';
COMMENT ON COLUMN room_queue_item.status           IS 'queued 대기 / playing 재생 중 / played 끝까지 재생 / skipped 투표로 넘김 / removed 삭제';
COMMENT ON COLUMN room_queue_item.duration_seconds IS '영상 길이(초). 플레이어가 알려준 값 중 가장 긴 값. 라이브는 NULL';

-- 넘기기/빼기 투표. 현재 방 인원의 50% 이상이 찬성하면 실행
CREATE TABLE room_queue_vote (
    item_id     BIGINT       NOT NULL REFERENCES room_queue_item (id) ON DELETE CASCADE,
    visitor_id  VARCHAR(36)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (item_id, visitor_id)
);
COMMENT ON TABLE room_queue_vote IS '대기열 곡 넘기기/빼기 투표';

INSERT INTO room_ambient (room_id, sound, label, default_volume, sort_order) VALUES
    ('cabin', 'fire',     '장작 타는 소리', 70, 1),
    ('cabin', 'wind',     '창밖 바람',     25, 2),
    ('cabin', 'rain',     '빗소리',         0, 3),
    ('sea',   'waves',    '파도',          70, 1),
    ('sea',   'wind',     '바닷바람',      25, 2),
    ('sea',   'crickets', '풀벌레',        15, 3);

INSERT INTO room_playlist (room_id, youtube_id, title, sort_order) VALUES
    ('cabin', 'HuFYqnbVbzY', 'jazz lofi radio · Lofi Girl',        1),
    ('cabin', 'jfKfPfyJRdk', 'lofi hip hop radio · Lofi Girl',     2),
    ('sea',   '28KRPhVzCus', 'lofi sleep/chill radio · Lofi Girl', 1),
    ('sea',   'lTRiuFIWV54', '1 A.M Study Session · Lofi Girl',    2);
