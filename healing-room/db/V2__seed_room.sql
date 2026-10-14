-- 기본 방 데이터
INSERT INTO room (id, name, title, subtitle, hint, background, effect, sort_order) VALUES
    ('cabin', '불멍',   '불멍 · 겨울 오두막', '눈 내리는 밤, 장작 타는 소리', '화면을 클릭하면 장작을 넣어요',     '/images/cabin.webp', 'cabin', 1),
    ('sea',   '바다멍', '바다멍 · 달빛 바다', '달빛 아래 잔잔한 파도',       '하늘을 클릭하면 별똥별이 떨어져요', '/images/sea.webp',   'sea',   2)
ON CONFLICT (id) DO NOTHING;
