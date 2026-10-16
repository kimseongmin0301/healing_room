-- 새 테마 8개: 방 만들기에서 고를 수 있다 (기본 방은 만들지 않음)
-- 배경: static/images/{id}.webp, 효과: static/js/effects/{id}.js
-- 기본 음악은 YouTube IFrame Player 로 실제 재생되는 것을 확인한 일반 영상 (2026-10-16 확인, 라이브 아님)

INSERT INTO theme (id, name, title, subtitle, hint, background, effect, sort_order) VALUES
    ('forest',        '숲멍',   '숲멍 · 반딧불 시냇가', '달빛 아래 졸졸 흐르는 시냇물',     '화면을 클릭하면 반딧불이 날아올라요',   '/images/forest.webp',        'forest',        3),
    ('forest-rain',   '비멍',   '비멍 · 비 오는 숲',    '숲속 오두막에 내리는 비',          '하늘을 클릭하면 번개가 쳐요',           '/images/forest-rain.webp',   'forest-rain',   4),
    ('rain-cafe',     '카페멍', '카페멍 · 비 오는 창가', '빗방울 흐르는 창가와 따뜻한 커피', '창문을 클릭하면 빗방울이 흘러내려요',   '/images/rain-cafe.webp',     'rain-cafe',     5),
    ('snow-cabin',    '눈멍',   '눈멍 · 눈 내리는 산장', '오로라 아래 소복이 쌓이는 눈',     '화면을 클릭하면 바람이 불어요',         '/images/snow-cabin.webp',    'snow-cabin',    6),
    ('sunset-beach',  '노을멍', '노을멍 · 노을 해변',   '해 지는 바다와 밀려오는 파도',     '하늘을 클릭하면 갈매기가 날아가요',     '/images/sunset-beach.webp',  'sunset-beach',  7),
    ('space-station', '우주멍', '우주멍 · 우주정거장',  '창밖으로 보이는 푸른 행성',        '하늘을 클릭하면 별똥별이 떨어져요',     '/images/space-station.webp', 'space-station', 8),
    ('space-nebula',  '별멍',   '별멍 · 깊은 우주',     '천천히 도는 은하와 별가루',        '화면을 클릭하면 별똥별이 떨어져요',     '/images/space-nebula.webp',  'space-nebula',  9),
    ('lounge-club',   '클럽',   '클럽 · 라운지',        '네온 불빛과 잔잔한 하우스 비트',   '화면을 클릭하면 조명이 번쩍여요',       '/images/lounge-club.webp',   'lounge-club',   10)
ON CONFLICT (id) DO NOTHING;

-- 환경음 (sound 는 static/js/audio/sounds.js 의 키)
INSERT INTO theme_ambient (theme_id, sound, label, default_volume, sort_order) VALUES
    ('forest',        'stream',   '시냇물',      65, 1),
    ('forest',        'crickets', '풀벌레',      30, 2),
    ('forest',        'wind',     '산들바람',    15, 3),
    ('forest-rain',   'rain',     '빗소리',      70, 1),
    ('forest-rain',   'thunder',  '먼 천둥',     35, 2),
    ('forest-rain',   'wind',     '숲 바람',     20, 3),
    ('rain-cafe',     'rain',     '창밖 빗소리', 60, 1),
    ('rain-cafe',     'city',     '도시 소리',   25, 2),
    ('rain-cafe',     'thunder',  '먼 천둥',      0, 3),
    ('snow-cabin',    'wind',     '눈바람',      45, 1),
    ('snow-cabin',    'fire',     '벽난로',      25, 2),
    ('sunset-beach',  'waves',    '파도',        70, 1),
    ('sunset-beach',  'wind',     '바닷바람',    25, 2),
    ('sunset-beach',  'seagulls', '갈매기',      20, 3),
    ('space-station', 'hum',      '정거장 소리', 50, 1),
    ('space-station', 'pad',      '우주의 화음', 30, 2),
    ('space-nebula',  'pad',      '우주의 화음', 60, 1),
    ('space-nebula',  'hum',      '깊은 울림',   15, 2),
    ('lounge-club',   'beat',     '라운지 비트', 50, 1),
    ('lounge-club',   'city',     '도시 소리',   15, 2);

-- 기본 음악 (대기열이 비고 자동 추천도 못 찾으면 순서대로 재생)
INSERT INTO theme_track (theme_id, youtube_id, title, sort_order) VALUES
    ('forest',        'hlWiI4xVXKY', 'Beautiful Relaxing Music with Piano, Guitar & Bird Sounds · Soothing Relaxation', 1),
    ('forest',        'xRsWO1VHIcU', 'Forest View Living Room Jazz · Forest Jazz Ambience',                             2),
    ('forest',        'ZlXBmHOWzL4', 'Enchanting Forest, Nature Sounds, Piano Music · Music for Body and Spirit',       3),
    ('forest',        'DRFHklnN-SM', 'Tranquility - Deep Healing Relaxing Music · Eternal Depth',                       4),
    ('forest-rain',   'o8GrqUSdzi0', 'Relaxing Music & Rain Sounds - Beautiful Piano Music · Soothing Relaxation',      1),
    ('forest-rain',   '35AdtzquJYg', 'Relaxing Sleep Music with Rain Sounds · Relaxing Piano Alex Minh',                2),
    ('forest-rain',   '9kzE8isXlQY', 'Lo-fi for study, work with Rain sounds · chill chill journal',                    3),
    ('rain-cafe',     'NJuSStkIZBg', 'Rainy Jazz Cafe - Slow Jazz Music in Coffee Shop Ambience · Coffee Shop Vibes',   1),
    ('rain-cafe',     'lA1ItxM9yIE', 'Rainy Night and Coffee Shop Ambience with Relaxing Jazz · Cozy Rain',             2),
    ('rain-cafe',     'sSmFBQIyXyE', 'Cozy Coffee Shop on a Rainy Day - Soft Jazz · Cozy Jazz Tunes',                   3),
    ('snow-cabin',    'S-4hwfyK-XQ', 'winter lofi mix [3 hours] · Lofi Girl',                                           1),
    ('snow-cabin',    '-dQ75USmQZo', 'Cozy Winter Jazz Lounge - Snow Cafe · Cozy Cup Melodies',                         2),
    ('snow-cabin',    'FycKAO9sb8I', 'Cozy Winter Jazz - Coffee Shop Ambience · Cozy Cup Melodies',                     3),
    ('snow-cabin',    'lVZAkpmKbQ4', 'Chill Winter Lofi · Settle',                                                      4),
    ('sunset-beach',  'D4M9cp_yNhg', 'Bossa Nova Beach - Covers · PMB Music',                                           1),
    ('sunset-beach',  'MqQiw5_MjEo', 'Sunset Luxury Chillout - Lounge Music · Chillout Lounge Music',                   2),
    ('sunset-beach',  'yLWabyZurhQ', 'Bossa Nova Beach - Relaxing Music · Playlists Kool',                              3),
    ('sunset-beach',  'k2CjGx_mLOg', 'CHILLOUT Sunset Mix · Settow',                                                    4),
    ('space-station', '3ST4fDVyAzA', 'Space Traveling [Lo-Fi / Jazz Hop / Chill Mix] · The Jazz Hop Café',              1),
    ('space-station', 'V-b9MirVx2w', 'THE CHILLEST LOFI IN THE UNIVERSE · VVM Lofi',                                    2),
    ('space-station', 'X-XZx1o_w-A', 'Space Ambient Music - Deep Relaxation Space Scenes · Relaxation Ambient Music',   3),
    ('space-nebula',  '4oY3v0jAWr4', 'Deep Space - Ambient Meditation and Sleep Music · Soothing Relaxation',           1),
    ('space-nebula',  'MvtWljsraTs', 'Calm Space Ambient Music. Cosmic Harmony · Relaxation Ambient Music',             2),
    ('space-nebula',  'caAVbyTCptw', 'Calm - Serene Meditative Music for Late Nights · Astral Ambience',                3),
    ('lounge-club',   '6ysKXydT8xY', 'ROOFTOP LOUNGE - Cool Music · Music Brokers',                                     1),
    ('lounge-club',   'k7HmLM7ZBBU', 'Soft Chill House Mix · Flavour Trip',                                             2),
    ('lounge-club',   'hLEmDqmcCzM', 'Soft Rooftop House Music Mix - Chillout Afterwork Lounge Set · Flavour Trip',     3),
    ('lounge-club',   't0dNbJWn8iw', 'Smoke Mood — Just Relax | Deep House Mix · Smoke Mood',                           4);
