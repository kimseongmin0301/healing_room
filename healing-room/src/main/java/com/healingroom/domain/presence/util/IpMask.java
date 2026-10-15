package com.healingroom.domain.presence.util;

/**
 * 다른 방문자에게 보여줄 IP 가리기. 커뮤니티 게시판처럼 앞부분만 보여줘 "같은 사람인지" 정도만 구분되게 한다.
 * 전체 IP 는 관리자 화면에서만 볼 수 있다.
 */
public final class IpMask {

    private IpMask() {
    }

    /** 203.0.113.10 → 203.0.*.*, 2001:db8:1:2::5 → 2001:db8:*, 127.0.0.1 → 로컬 */
    public static String mask(String ip) {
        if (ip == null || ip.isBlank()) {
            return "알 수 없음";
        }
        String value = ip.strip();
        if (value.startsWith("::ffff:")) {
            value = value.substring("::ffff:".length()); // IPv6 로 표현된 IPv4
        }
        if (value.equals("127.0.0.1") || value.equals("::1") || value.equals("0:0:0:0:0:0:0:1")) {
            return "로컬";
        }
        if (value.contains(".") && !value.contains(":")) {
            String[] parts = value.split("\\.");
            return parts.length == 4 ? parts[0] + "." + parts[1] + ".*.*" : "알 수 없음";
        }
        String[] groups = value.split(":");
        return groups.length >= 2 ? groups[0] + ":" + groups[1] + ":*" : "알 수 없음";
    }
}
