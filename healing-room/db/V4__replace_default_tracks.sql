-- 기본 음악 교체: V3 의 Lofi Girl 라이브 방송 대부분이 외부 재생(임베드)이 막혀 있어(YouTube 오류 150) 재생되지 않았다.
-- 아래는 IFrame Player 로 실제 재생되는 것을 확인한 일반 영상들 (2026-10-14 확인).

DELETE FROM room_playlist WHERE room_id IN ('cabin', 'sea');

INSERT INTO room_playlist (room_id, youtube_id, title, sort_order) VALUES
    ('cabin', 'CBSlu_VMS9U', 'jazz lofi mix [3 hours] · Lofi Girl',                1),
    ('cabin', 'TjC92khsso4', 'Books, Coffee, & Jazz · Chill Crossing Hour',         2),
    ('cabin', 'YgiCjx0fmp8', 'Cozy Jazz In October · Tranquill Cabin Ambience',     3),
    ('sea',   'pQuiXQos898', 'Ocean Night · 1 Hour Chill Lofi · Chill & Mots',      1),
    ('sea',   'lTRiuFIWV54', '1 A.M Study Session · Lofi Girl',                     2),
    ('sea',   'l98w9OSKVNA', '12 A.M Study Session · Lofi Girl',                    3),
    ('sea',   'TILgMsJA7zE', 'Relaxing Piano Music to Help You Sleep · Piano Peace', 4);
