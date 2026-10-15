-- 대기열 곡의 출처: 방문자 신청(request) / 테마 기본 음악(default) / 자동 추천(autoplay)
-- 신청곡이 없으면 방금 나온 곡과 비슷한 영상을 자동으로 이어 튼다. default·autoplay 는 누구나 바로 넘길 수 있다.

ALTER TABLE room_queue_item
    ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'request'
    CHECK (source IN ('request', 'default', 'autoplay'));

-- 지금까지 added_by 가 없는 곡은 모두 테마 기본 음악이었다
UPDATE room_queue_item SET source = 'default' WHERE added_by IS NULL;

COMMENT ON COLUMN room_queue_item.source IS 'request 방문자 신청 / default 테마 기본 음악 / autoplay 자동 추천 (default·autoplay 는 added_by 가 NULL)';
