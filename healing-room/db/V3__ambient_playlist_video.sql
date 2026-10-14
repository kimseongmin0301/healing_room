-- 환경음 믹서, 방별 YouTube 플레이리스트, 배경 영상

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

-- 방마다 기본 YouTube 재생 목록 (방문자는 자기 링크를 추가할 수 있고, 그건 브라우저에만 저장)
CREATE TABLE room_playlist (
    id          BIGSERIAL    PRIMARY KEY,
    room_id     VARCHAR(50)  NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    kind        VARCHAR(10)  NOT NULL CHECK (kind IN ('video', 'playlist')),
    youtube_id  VARCHAR(64)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    UNIQUE (room_id, youtube_id)
);
COMMENT ON TABLE  room_playlist            IS '방별 기본 YouTube 재생 목록';
COMMENT ON COLUMN room_playlist.kind       IS 'video: 영상 하나 / playlist: 재생목록';
COMMENT ON COLUMN room_playlist.youtube_id IS '영상 id(v=...) 또는 재생목록 id(list=...)';

INSERT INTO room_ambient (room_id, sound, label, default_volume, sort_order) VALUES
    ('cabin', 'fire',     '장작 타는 소리', 70, 1),
    ('cabin', 'wind',     '창밖 바람',     25, 2),
    ('cabin', 'rain',     '빗소리',         0, 3),
    ('sea',   'waves',    '파도',          70, 1),
    ('sea',   'wind',     '바닷바람',      25, 2),
    ('sea',   'crickets', '풀벌레',        15, 3);

INSERT INTO room_playlist (room_id, kind, youtube_id, title, sort_order) VALUES
    ('cabin', 'video', 'HuFYqnbVbzY', 'jazz lofi radio · Lofi Girl',            1),
    ('cabin', 'video', 'jfKfPfyJRdk', 'lofi hip hop radio · Lofi Girl',         2),
    ('sea',   'video', '28KRPhVzCus', 'lofi sleep/chill radio · Lofi Girl',     1),
    ('sea',   'video', 'lTRiuFIWV54', '1 A.M Study Session · Lofi Girl',        2);
