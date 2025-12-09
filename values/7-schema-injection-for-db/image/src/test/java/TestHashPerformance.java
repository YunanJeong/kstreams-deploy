import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.apache.commons.codec.digest.DigestUtils;



public class TestHashPerformance {
    private static String INPUT = "12345"; 
    private static String OUTPUT = "5994471abb01112afcc18159f6cc74b4f511b99806da59b3caf5a9c173cacfc5";  
    private static String BAD_OUTPUT = "bad output sample~~~~"; 

    
    // String hash = DigestUtils.sha256Hex("hello world");


    @Test
    @DisplayName("기본 해시함수 동작: SHA-256을 쓰고 출력은 16진수 문자열")
    void testHash() {
        System.out.println(DigestUtils.sha256Hex(INPUT));
        assertEquals(OUTPUT, DigestUtils.sha256Hex(INPUT));
        assertTrue(!BAD_OUTPUT.equals(DigestUtils.sha256Hex(INPUT)));
        
    }


    @Test
    void testSha256Performance() {
        System.out.println("----- SHA-256 해싱 성능 테스트 -----");
        String largeData = "x".repeat(1_000_000);  // 1MB 문자열
        
        long startTime = System.nanoTime();
        String hash = DigestUtils.sha256Hex(largeData);
        long endTime = System.nanoTime();
        
        long durationMs = (endTime - startTime) / 1_000_000;
        System.out.println("SHA-256 해싱 시간: " + durationMs + "ms");
        System.out.println("결과: " + hash);
    }

    @Test
    void testHashPerformanceBySize() {
        System.out.println("----- SHA-256 해싱 성능 테스트 (크기별) -----");
        int[] sizes = {1_000, 10_000, 100_000, 1_000_000, 10_000_000};

        // 워밍업 (JIT 컴파일 유도, 첫 측정값 왜곡 방지)
        for (int i = 0; i < 100; i++) {
            DigestUtils.sha256Hex("x".repeat(1000));
        }

        for (int size : sizes) {
            String data = "x".repeat(size);  //size만큼 x가 나열된 문자열

            long start = System.nanoTime();
            DigestUtils.sha256Hex(data);
            long duration = (System.nanoTime() - start) / 1_000_000;
            
            System.out.printf("크기: %,d bytes | 시간: %d ms%n", size, duration);
        }
    }

    @Test
    void testHashPerformanceByCount() {
        System.out.println("----- SHA-256 해싱 성능 테스트 (횟수별) -----");
        String data = "x".repeat(1_000_000);  // 1MB 고정
        int[] counts = {1_000, 10_000, 100_000};
        
        // 워밍업
        for (int i = 0; i < 1000; i++) {
            DigestUtils.sha256Hex(data);
        }
        
        // 실제 측정
        for (int count : counts) {
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                DigestUtils.sha256Hex(data);
            }
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            double throughput = (double) count / durationMs;
            
            System.out.printf("횟수: %,d | 시간: %d ms | 처리량: %.0f ops/ms%n", 
                count, durationMs, throughput);
        }
    }

}

