package io.github.yunanjeong.kafka.streams;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 환경변수를 한 번만 읽어 담아두고, 각 토폴로지에 인자로 넘기기 위한 설정 홀더.
 *
 * 단일 이미지에 토폴로지가 여러 개 들어가면,
 * 각 클래스가 System.getenv()를 static 필드로 직접 읽는 방식은 위험하다.
 * 레지스트리가 모든 토폴로지 클래스를 참조하므로,
 * "선택하지도 않은" 토폴로지의 환경변수가 비어있다는 이유로 클래스 초기화 단계에서 앱 전체가 죽는다.
 *
 * 따라서 이 패턴에서는 아래 규칙을 지킨다.
 *   - System.getenv() 호출은 이 클래스에서만 한다.
 *   - 토폴로지는 자기가 필요한 값만 build() 시점에 꺼내 쓴다. 선택되지 않으면 아무 검증도 일어나지 않는다.
 *
 * 부수효과로, 환경변수 없이도 of()로 설정을 만들 수 있어 유닛테스트가 쉬워진다.
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
