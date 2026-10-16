-- 모든 테마에 항상 열려 있는 기본 방을 만든다 (행만 추가, 테이블 구조는 그대로)
-- 테마 소개 페이지(/themes/*.html)에서 바로 들어갈 수 있게. 방 id 는 주소(#id)로 쓰기 좋게 짧게 (12자 제한)
-- 목록은 기본 방 → 만든 시각 순이므로, 테마 순서대로 보이도록 만든 시각을 1초씩 늦춘다

INSERT INTO room (id, theme_id, name, is_default, created_at) VALUES
    ('forest', 'forest',        '숲멍 · 반딧불 시냇가', true, now() + interval '1 second'),
    ('rain',   'forest-rain',   '비멍 · 비 오는 숲',    true, now() + interval '2 second'),
    ('cafe',   'rain-cafe',     '카페멍 · 비 오는 창가', true, now() + interval '3 second'),
    ('snow',   'snow-cabin',    '눈멍 · 눈 내리는 산장', true, now() + interval '4 second'),
    ('sunset', 'sunset-beach',  '노을멍 · 노을 해변',   true, now() + interval '5 second'),
    ('space',  'space-station', '우주멍 · 우주정거장',  true, now() + interval '6 second'),
    ('galaxy', 'space-nebula',  '별멍 · 깊은 우주',     true, now() + interval '7 second'),
    ('club',   'lounge-club',   '클럽 · 라운지',        true, now() + interval '8 second')
ON CONFLICT (id) DO NOTHING;
