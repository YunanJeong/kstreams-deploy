package io.github.yunanjeong.kafka.streams;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class TimeUtils {
    private TimeUtils() {
        throw new UnsupportedOperationException("간단한 Utility class이므로 객체 생성을 하지않고, 정적 호출로 사용");
    }

    // 재사용 가능하도록 static final로 선언 (Thread safe)
    private static final DateTimeFormatter DIGIT7_PARSER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSZ");
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"); //MySQL datetime(6)

    // 기본 ISO 파서 (가변 소수점 처리, +09:00 지원, +0900 미지원)
    private static final DateTimeFormatter ISO8601_PARSER = DateTimeFormatter.ISO_OFFSET_DATE_TIME; 

    public static String convert(String input) {
        /*
         * 2025-01-01T00:00:00.1234567+0900  => 2025-01-01 00:00:00.123456 과 같이 변경하는 메소드
         * T 제거,시간대제거, 소수점 6자리까지만 유지(마이크로초 단위), 반올림 아니고 그냥 자름
         */

        // input 시간 해석
        OffsetDateTime dateTime = OffsetDateTime.parse(input, DIGIT7_PARSER);

        // 변환 후 반환
        LocalDateTime truncatedTime = dateTime.toLocalDateTime().truncatedTo(ChronoUnit.MICROS);
        return truncatedTime.format(OUTPUT_FORMATTER);
    }



    public static String isoToDatetime6(String input) {
        /*
         * 2025-01-01T00:00:00.1234567+09:00  => 2025-01-01 00:00:00.123456
         * 2025-01-01T00:00:00.1230000+09:00  => 2025-01-01 00:00:00.123000
         * 2025-01-01T00:00:00.123+09:00      => 2025-01-01 00:00:00.123000
         * 소수점이하 가변길이 자리수에 대응
         * 자릿수가 작아지면 자름(truncate), 자릿수가 커지면 0으로 채움(padding)
         * T 제거,시간대제거, 소수점 6자리까지만 유지(마이크로초 단위)
         * ISO 8601 포맷 대응, 가변길이 소수점 이하 자리수에 대응
         * +09:00 지원, +0900 미지원
         */

        LocalDateTime ldt = LocalDateTime.parse(input, ISO8601_PARSER);  
        return ldt.format(OUTPUT_FORMATTER);  
    }
}
