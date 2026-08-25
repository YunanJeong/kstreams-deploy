import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.yunanjeong.kafka.streams.TimeUtils;




public class TestTimeUtils {

    private static Long INPUT = 1727352009L;  // 초단위까지 나온 타임스탬프 (epoch seconds)
    private static String OUTPUT = "2024_09"; 
    private static String BAD_OUTPUT = "1970_12"; 
    

    @Test
    @DisplayName("시간 변환 테스트")
    void testScenario1() {
        System.out.println(TimeUtils.getYearMonthFromTimestamp(INPUT));
        assertEquals(OUTPUT ,TimeUtils.getYearMonthFromTimestamp(INPUT));
        assertTrue(!BAD_OUTPUT.equals(TimeUtils.getYearMonthFromTimestamp(INPUT)));
    }


}
