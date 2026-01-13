package io.github.yunanjeong.kafka.streams;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class TimeUtils {
    private TimeUtils() {
        throw new UnsupportedOperationException("간단한 Utility class이므로 객체 생성을 하지않고, 정적 호출로 사용");
    }

    // 재생성없도록 static final로 선언 (Thread safe)

    //2025-12-26T09:26:51.0000000+0000
    private static final DateTimeFormatter INPUT_FORMATTER_Z = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSZ");

    //2025-12-26T09:26:51.0000000+00:00
    private static final DateTimeFormatter INPUT_FORMATTER_XXX = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSXXX");

    // 기본 ISO 파서 (소수점 이하 가변길이 자리수 처리, +09:00 지원, +0900 미지원)
    private static final DateTimeFormatter ISO8601_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME; 


    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"); //MySQL datetime(6)


    public static String convert(String input, DateTimeFormatter inputFormat, DateTimeFormatter outputFormat) {
        /*
         * 입출력 포맷 기반으로 시간을 변환 및 return하는 범용 메소드
         * 변환시 소수점 이하 자릿수가 작아지면 자름(truncate) => 이는 자바 라이브러리 기본동작이며, 반올림은 보통 별도 구현이 필요
         */
        OffsetDateTime ldt = OffsetDateTime.parse(input, inputFormat);  
        return ldt.format(outputFormat);  
    }

    public static String toMySqlDateTime6(String input) {
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

        return convert(input, ISO8601_FORMATTER, OUTPUT_FORMATTER);
    }
}
