package com.healingroom.domain.presence.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IpMaskTests {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "203.0.113.10 | 203.0.*.*",
            "::ffff:121.134.5.6 | 121.134.*.*",
            "127.0.0.1 | 로컬",
            "0:0:0:0:0:0:0:1 | 로컬",
            "2001:db8:85a3::8a2e:370:7334 | 2001:db8:*",
            "'' | 알 수 없음",
    })
    void masksAllButTheFirstPart(String ip, String expected) {
        assertThat(IpMask.mask(ip)).isEqualTo(expected);
    }
}
