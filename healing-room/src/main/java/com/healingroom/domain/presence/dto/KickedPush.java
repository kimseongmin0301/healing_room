package com.healingroom.domain.presence.dto;

/**
 * 방에서 내보내졌다는 알림 (type=kicked). 받은 화면은 로비(/)로 가서 슬픈 고양이 창을 띄운다.
 *
 * @param by    누가 내보냈는지 ("host" 방장 / "admin" 관리자)
 * @param until 다시 들어올 수 있는 시각(ms)
 */
public record KickedPush(String type, String by, long until) {

    public static KickedPush of(String by, long until) {
        return new KickedPush("kicked", by, until);
    }
}
