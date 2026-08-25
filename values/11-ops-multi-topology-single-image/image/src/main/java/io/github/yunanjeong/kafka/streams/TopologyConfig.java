package io.github.yunanjeong.kafka.streams;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 환경변수를 한 번만 읽어 담아두고, 각 스트림 처리에 인자로 넘기기 위한 설정 홀더.
 * 스트림 처리를 추가해도 이 파일은 손대지 않는다.
 *
 * 규칙 두 개다.
 *   - System.getenv() 호출은 이 클래스에서만 한다.
 *   - 각 스트림 처리는 자기가 필요한 값만 build() 안에서 꺼내 쓴다.
 *     선택되지 않은 처리의 환경변수는 아무도 읽지 않으므로 없어도 앱이 뜬다.
 *
 * 이게 있는 이유는 테스트다. 설정값을 static 필드에서 System.getenv()로 읽어버리면
 * 그 클래스를 건드리는 모든 테스트가 환경변수 stub 도구를 끌고 와야 한다.
 * of()로 Map을 넣어주면 그게 필요 없다.
 */
public class TopologyConfig {

    private final Map<String, String> values;

    private TopologyConfig(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    /** 운영 진입점. 실제 환경변수를 읽는다. */
    public static TopologyConfig fromEnv() {
        return new TopologyConfig(System.getenv());
    }

    /** 유닛테스트용. 환경변수 stub 없이 설정을 구성할 수 있다. */
    public static TopologyConfig of(Map<String, String> values) {
        return new TopologyConfig(values);
    }

    /** 필수값. 없으면 즉시 실패시켜 잘못된 설정으로 앱이 뜨는 것을 막는다. */
    public String require(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required environment variable is missing: " + key);
        }
        return value.trim();
    }

    /** 선택값. 없으면 기본값을 쓴다. */
    public String get(String key, String defaultValue) {
        String value = values.get(key);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    /** ISO-8601 Duration 표기 필수값 (e.g. "PT30M", "PT1H", "P1D") */
    public Duration requireDuration(String key) {
        String value = require(key);
        try {
            return Duration.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                "Invalid ISO-8601 duration for " + key + ": " + value + " (e.g. PT30M, PT1H, P1D)", e);
        }
    }
}
