package io.github.yunanjeong.kafka.streams;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

public final class TimeUtils {
    private TimeUtils() {
        throw new UnsupportedOperationException("간단한 Utility class이므로 객체 생성을 하지않고, 정적 호출로 사용");
    }

    // 재사용 가능하도록 static final로 선언 (Thread safe)
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSZ");
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"); //MySQL datetime(6)

    public static String convert(String input) {
        /*
         * 2025-01-01T00:00:00.1234567+0900  => 2025-01-01 00:00:00.123456 과 같이 변경하는 메소드
         * T 제거,시간대제거, 소수점 6자리까지만 유지(마이크로초 단위), 반올림 아니고 그냥 자름
         */

        // input 시간 해석
        OffsetDateTime dateTime = OffsetDateTime.parse(input, INPUT_FORMATTER);

        // 변환 후 반환
        LocalDateTime truncatedTime = dateTime.toLocalDateTime().truncatedTo(ChronoUnit.MICROS);
        return truncatedTime.format(OUTPUT_FORMATTER);
    }

    private static final DateTimeFormatter ISO8601_PARSER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public static String isoToDatetime6(String input) {
        LocalDateTime ldt = LocalDateTime.parse(input, ISO8601_PARSER);  // 기본 ISO 파서 (가변 소수점 처리)
        return ldt.format(OUTPUT_FORMATTER);  
    }
}
