import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


public class TestTimeUtils {

    private static String INPUT = "2025-01-01T00:00:00.1234567+0900"; // ISO 8601 포맷 
    private static String OUTPUT = "2025-01-01 00:00:00.123456";  // mysql datetime(6)을 위함. // 소수점 7번쨰는 그냥 자름. 반올림 아님
    private static String BAD_OUTPUT = "2025-01-01 00:00:00.123457"; // 반올림
    
    @Test
    @DisplayName("시간 변환 테스트")
    void testScenario1() {
        assertEquals(OUTPUT ,getConvertedTime(INPUT));
        assertTrue(!BAD_OUTPUT.equals(getConvertedTime(INPUT)));
    }

    public static String getConvertedTime(String input) {
        /*
         * 2025-01-01T00:00:00.1234567+0900  => 2025-01-01 00:00:00.123456 과 같이 변경하는 메소드
         * T 제거, 소수점 6자리까지만 유지 (마이크로초 단위), 반올림 아니고 그냥 자름
         */
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSZ");
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

        // input 시간 해석
        OffsetDateTime dateTime = OffsetDateTime.parse(input, inputFormatter);

        // 변환 후 출력
        LocalDateTime truncated = dateTime.toLocalDateTime().truncatedTo(ChronoUnit.MICROS);
        return truncated.format(outputFormatter);
    }


}
