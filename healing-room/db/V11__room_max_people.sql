-- 방 최대 인원. 방을 만들 때 정하고 방장이 바꿀 수 있다. NULL 이면 제한 없음 (기본 방은 늘 NULL)
-- 칸만 추가하므로 지금 돌아가는 앱에는 영향이 없다

ALTER TABLE room ADD COLUMN max_people INTEGER CHECK (max_people BETWEEN 2 AND 100);
COMMENT ON COLUMN room.max_people IS '최대 인원 (2~100). NULL 이면 제한 없음';
