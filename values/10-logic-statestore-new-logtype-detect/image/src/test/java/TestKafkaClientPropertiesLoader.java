import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.databind.annotation.JsonAppend.Prop;

import io.github.yunanjeong.kafka.streams.KafkaClientPropertiesLoader;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.streams.StreamsConfig;


import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SecurityConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.config.TopicConfig;

/* 테스트 코드에서만 환경변수 지정하기 */
// 환경변수는 JVM이 시작될 때 한 번 정해지고, 실행 중 변경은 원칙적으로 불가(즉, 코드 내 지정 불가)
// JDK 16+부터는 이 제한이 강화돼 코드 내 지정 방식인 @SetEnvironmentVariable 사용 불가
// 테스트용 환경변수는 보통 JVM 실행 시점(IDE, 빌드도구)에서 설정하는 것이 정석
// 대안으로 (uk) system-stubs 라이브러리 사용가능
// system-stubs는 Java의 유일한 환경변수 접근 API인 System.getenv() 호출 결과를 "테스트 범위(JUnit동작)에서만" 가짜로 반환하게 함
/* 최종적으로는 System.getenv()를 밖으로 빼내어 환경변수는 input으로 받도록 메소드 설계하는 것이 UnitTest 취지에 맞고 바람직함 */

@ExtendWith(SystemStubsExtension.class)
public class TestKafkaClientPropertiesLoader {

    @SystemStub
    private EnvironmentVariables env = 
        new EnvironmentVariables(
            "NON_TARGET_ENV_KEY", "non_target_env_value",

            // 굳이 필수 처리 안해도됨. 운영에서 관리할 영역이고, 값이 없더라도 상위 레이어에서 즉시 에러 발생시켜주거나 기본값 처리될거니까. 
            "STREAMS_BOOTSTRAP_SERVERS", "k1:9092,k2:9092",
            "STREAMS_APPLICATION_ID", "my-app",

            // 기타 설정 General Case
            // 오타 입력시 예외처리가 있으면 좋음. 운영시 잘못 넣으면 그냥 무시되고 기본값이 사용되므로 확인이 힘듦
            "STREAMS_AUTO_OFFSET_RESET", "earliest",
            "STREAMS_CONSUMER_AUTO_OFFSET_RESET", "latest",
            "STREAMS_PRODUCER_MAX_REQUEST_SIZE", "10485760",

            // 코드 복잡해지게 굳이 예외처리 할 필요없는 케이스이긴 함. 운영시 잘못 넣어도 상위 레이어에서 무시할거니까.
            "STREAMS_", "empty_name_test",
            "STREAMS_STREAMS_STREAMS_ddddd", "prefix parsing test"
        );

    @Test
    public void TestLoadEnvProperties() {

        Map<String, String> allEnvs = System.getenv();
        Properties props = new KafkaClientPropertiesLoader().loadFromEnvPrefix(allEnvs, "STREAMS_");
        
        props.forEach((k, v) -> 
            System.out.println(k + " = " + v)
        );

        assertTrue(props.containsKey("bootstrap.servers"));
        assertTrue(props.containsKey("application.id"));
        assertTrue(props.containsKey("auto.offset.reset"));
        assertTrue(props.containsKey("consumer.auto.offset.reset"));
        assertTrue(props.containsKey("producer.max.request.size"));


        assertEquals("k1:9092,k2:9092", props.getProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG)); // "bootstrap.servers"
        assertEquals("my-app", props.getProperty(StreamsConfig.APPLICATION_ID_CONFIG)); // "application.id"
        assertEquals("earliest", props.getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)); // "auto.offset.reset"
        assertEquals("latest", props.getProperty(StreamsConfig.consumerPrefix(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG))); // "consumer.auto.offset.reset"
        assertEquals("10485760", props.getProperty(StreamsConfig.producerPrefix(ProducerConfig.MAX_REQUEST_SIZE_CONFIG))); // "producer.max.request.size"
        assertEquals("empty_name_test", props.getProperty(""));

    }

    @Test
    public void TestLoadAndValidate(){
        // Properties props = new KafkaClientPropertiesLoader().loadAndValidate();
        //Properties props = new KafkaClientPropertiesLoader().loadFromEnvPrefixAndValidate(System.getenv(), "STREAMS_");

        assertDoesNotThrow(() -> {
            Map <String, String> allEnvs = Map.of(
                "STREAMS_BOOTSTRAP_SERVERS", "k1:9092,k2:9092",
                "STREAMS_APPLICATION_ID", "my-app",
                "STREAMS_AUTO_OFFSET_RESET", "earliest",
                "STREAMS_CONSUMER_AUTO_OFFSET_RESET", "latest",
                "STREAMS_PRODUCER_MAX_REQUEST_SIZE", "10485760"
            );

            Properties props = new KafkaClientPropertiesLoader().loadFromEnvPrefixAndValidate(allEnvs, "STREAMS_");
            System.out.println("// 정상 케이스에서는 예외 안나는지 확인 및 출력");
            props.forEach((k, v) -> 
                System.out.println(k + " = " + v)
            );
        });

        Exception ex = assertThrows(Exception.class, () -> {
            // invalid key가 있을 때 예외 발생하는지 확인 (Throw 발생시 성공)
            Map<String, String> allEnvs = Map.of(
                "STREAMS_BOOTSTRAP_SERVERS", "k1:9092,k2:9092",

                // 설정키가 아닌 것들
                "STREAMS_INVALID_CONFIG_NAME", "some_value",
                "STREAMS_PRODUCER_MAX_REQUESTssss_SIZE", "10485760",

                // 설정키가 아닌 것들2
                "STREAMS_", "empty_name_test",
                "STREAMS_STREAMS_STREAMS_ddddd", "prefix parsing test"
            );

            Properties props = new KafkaClientPropertiesLoader().loadFromEnvPrefixAndValidate(allEnvs, "STREAMS_");
        });
        System.out.println("// throw 발생시 메시지 정상 출력 확인");
        ex.getMessage().lines().forEach(line -> System.out.println("Exception message line: " + line));
    }


}

