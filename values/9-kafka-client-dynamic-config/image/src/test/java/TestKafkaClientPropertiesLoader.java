import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.github.yunanjeong.kafka.streams.KafkaClientPropertiesLoader;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.streams.StreamsConfig;



/* 테스트 코드에서만 환경변수 지정하기 */
// 환경변수는 JVM이 시작될 때 한 번 정해지고, 실행 중 변경은 원칙적으로 불가(즉, 코드 내 지정 불가)
// JDK 16+부터는 이 제한이 강화돼 코드 내 지정 방식인 @SetEnvironmentVariable 사용 불가
// 테스트용 환경변수는 보통 JVM 실행 시점(IDE, 빌드도구)에서 설정하는 것이 정석
// 대안으로 (uk) system-stubs 라이브러리 사용가능
// system-stubs는 Java의 유일한 환경변수 접근 API인 System.getenv() 호출 결과를 "테스트 범위(JUnit동작)에서만" 가짜로 반환하게 함

@ExtendWith(SystemStubsExtension.class)
public class TestKafkaClientPropertiesLoader {

    @SystemStub
    private EnvironmentVariables env = 
        new EnvironmentVariables(
            "FOO", "bar",
            "STREAMS_BOOTSTRAP_SERVERS", "k1:9092,k2:9092",
            "STREAMS_APPLICATION_ID", "my-app",
            "STREAMS_AUTO_OFFSET_RESET", "earliest",
            "STREAMS_CONSUMER_AUTO_OFFSET_RESET", "latest"
        );

    @Test
    public void TestLoadEnvProperties() {

        Properties props = new KafkaClientPropertiesLoader().loadFromEnvPrefix("STREAMS_");
        
        System.out.println(props);

        assertEquals("k1:9092,k2:9092", props.getProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("my-app", props.getProperty(StreamsConfig.APPLICATION_ID_CONFIG));
        assertEquals("earliest", props.getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
        assertEquals("latest", props.getProperty(StreamsConfig.consumerPrefix(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)));


    }
    
    

}

