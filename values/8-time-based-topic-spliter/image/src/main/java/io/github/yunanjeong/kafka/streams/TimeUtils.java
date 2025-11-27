package io.github.yunanjeong.kafka.streams;

import java.time.ZoneId;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public final class TimeUtils {
    private TimeUtils() {
        throw new UnsupportedOperationException("간단한 Utility class이므로 객체 생성을 하지않고, 정적 호출로 사용");
    }

    // 재사용 가능하도록 static final로 선언 (Thread safe)
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("uuuu_MM");

    public static String getYearMonthFromTimestamp(long epochSeconds) {
        return dateTimeFormatter.format(Instant.ofEpochSecond(epochSeconds).atZone(KST));
    }

}
