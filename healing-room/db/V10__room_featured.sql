-- 로비에 늘 보일 방 표시. 기본 방이 10개로 늘어 로비가 붐비므로,
-- 불멍·바다멍만 늘 보이고 나머지 기본 방은 사람이 있을 때만 로비에 나온다 (주소 /#id 로는 언제든 들어감)
-- 칸만 추가하므로 지금 돌아가는 앱에는 영향이 없다

ALTER TABLE room ADD COLUMN featured BOOLEAN NOT NULL DEFAULT false;
COMMENT ON COLUMN room.featured IS '로비에 늘 보이기 (아니면 기본 방은 사람이 있을 때만 로비에 보임)';

UPDATE room SET featured = true WHERE id IN ('cabin', 'sea');
