package io.github.yunanjeong.kafka.streams;

import java.time.ZoneId;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public final class TimeUtils {
    private TimeUtils() {
        throw new UnsupportedOperationException("간단한 Utility class이므로 객체 생성을 하지않고, 정적 호출로 사용");
    }

    // 재선언없이 사용가능하도록 static final로 선언 (Thread safe)
    // TZ 환경변수값 없으면 Asia/Seoul로 기본설정, TZ 이상한 값 할당시 에러
    private static final ZoneId KST = ZoneId.of(Optional.ofNullable(System.getenv("TZ")).orElse("Asia/Seoul")); 
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("uuuu_MM");

    public static String getYearMonthFromTimestamp(long epochSeconds) {
        return dateTimeFormatter.format(Instant.ofEpochSecond(epochSeconds).atZone(KST));
    }

}
