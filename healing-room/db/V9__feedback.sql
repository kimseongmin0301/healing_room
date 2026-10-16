-- 로비에서 받는 의견(피드백). 익명이며 IP·연락처는 받지 않는다. 관리자 화면에서만 본다

CREATE TABLE feedback (
    id          BIGSERIAL     PRIMARY KEY,
    message     VARCHAR(1000) NOT NULL,
    visitor_id  VARCHAR(36)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX feedback_created_idx ON feedback (created_at DESC);
COMMENT ON TABLE  feedback            IS '로비 의견 보내기. 관리자 화면(/admin.html)에서만 본다';
COMMENT ON COLUMN feedback.visitor_id IS '보낸 브라우저의 방문자 id (도배 확인용, 화면에는 앞 8자리만)';
