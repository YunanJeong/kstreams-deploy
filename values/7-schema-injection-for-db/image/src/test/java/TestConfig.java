import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.streams.StreamsConfig;



public class TestConfig {
    
    @Test
    public void compareConfigs() {
        
        // 모두 단순 String이고, 단지 Java 코드화(인텔리센스, 디버그, 오타방지 등)를 위해 메서드/상수로 제공될 뿐이다.
        // 명확한 Kafka용어를 그대로 쓰고싶으면 문자열 입력해도 무관하며, 스타일 차이라고 볼 수 있음
        // 방식 0,1: "auto.offset.reset"
        // 방식 2,3,4: "consumer.auto.offset.reset"
        // https://kafka.apache.org/41/javadoc/org/apache/kafka/clients/consumer/ConsumerConfig.html
        // https://kafka.apache.org/41/javadoc/org/apache/kafka/clients/producer/ProducerConfig.html

        // consumer/producer prefix를 붙이는 이유
        // consumer, producer 공통으로 사용되는 옵션들(bootstrap.servers, request.timeout.ms 등)이 있는데,
        // 이를 각각 다르게 설정해야할 때 prefix를 붙이면됨(또는 코드상 명시적 표현 목적)
        // auto.offset.reset의 경우 consumer 전용이라 prefix가 있든 없든 동작차이가 없다.
        String key0 = "auto.offset.reset";
        String key1 = ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
        assertEquals(key0, key1);


        String key2 = "consumer.auto.offset.reset";
        String key3 = StreamsConfig.consumerPrefix("auto.offset.reset");
        String key4 = StreamsConfig.consumerPrefix(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG);
        assertEquals(key2, key3, key4);
        
        System.out.println("\n=== 모두 단순 String일 뿐. Java 코드화(인텔리센스, 디버그, 오타방지 등)를 위해 메서드/상수로 제공됨 ===");
        System.out.println("방식 0: " + key0);
        System.out.println("방식 1: " + key1);
        System.out.println("방식 2: " + key2);
        System.out.println("방식 3: " + key3);
        System.out.println("방식 4: " + key4);
    }
    
    
    @Test
    public void testMessageSizeConfigsPrinted() {
        Properties props = new Properties();
        
        props.put(StreamsConfig.consumerPrefix(ConsumerConfig.FETCH_MAX_BYTES_CONFIG), "10485760");
        props.put(StreamsConfig.consumerPrefix(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG), "10485760");
        props.put(StreamsConfig.producerPrefix(ProducerConfig.MAX_REQUEST_SIZE_CONFIG), "10485760");
        
        System.out.println("\n=== All Kafka Configs ===");
        props.forEach((key, value) -> 
            System.out.println(key + " = " + value)
        );
    }
}

