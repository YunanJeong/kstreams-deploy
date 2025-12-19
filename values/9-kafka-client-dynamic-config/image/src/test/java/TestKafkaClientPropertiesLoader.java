import static org.junit.jupiter.api.Assertions.assertEquals;
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

            // 코드 복잡해지게 굳이 예외처리 할 필요없는 케이스. 운영시 잘못 넣어도 상위 레이어에서 에러뜨거나 무시할거니까.
            "STREAMS_", "empty_name_test",
            "STREAMS_STREAMS_STREAMS_ddddd", "prefix parsing test",

            // 굳이 필수 처리 안해도됨. 운영에서 관리할 영역이고, 값이 없더라도 상위 레이어에서 즉시 에러 발생시켜주거나 기본값 처리될거니까. 
            "STREAMS_BOOTSTRAP_SERVERS", "k1:9092,k2:9092",
            "STREAMS_APPLICATION_ID", "my-app",

            // 기타 설정 General Case
            "STREAMS_AUTO_OFFSET_RESET", "earliest",
            "STREAMS_CONSUMER_AUTO_OFFSET_RESET", "latest",
            "STREAMS_PRODUCER_MAX_REQUEST_SIZE", "10485760"
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
    public void print(){

        // System.out.println(getConstantValueSet(CommonClientConfigs.class));
        // System.out.println(getConstantValueSet(TopicConfig.class));
        // System.out.println(getConstantValueSet(SaslConfigs.class));
        // System.out.println(getConstantValueSet(SecurityConfig.class));
        // System.out.println(getConstantValueSet(SslConfigs.class));
        // getConstantValueSet(CommonClientConfigs.class).forEach(System.out::println); //ㅇㅋ
        // getConstantValueSet(TopicConfig.class).forEach(System.out::println); //ㅇㅋ
        // filterContaining(getConstantValueSet(SaslConfigs.class),"sasl.").forEach(System.out::println); //ㅇㅋ
        // getConstantValueSet(SslConfigs.class).forEach(System.out::println);
        // getConstantValueSet(SecurityConfig.class).forEach(System.out::println);
    }

    public static Set<String> getConfigNames(Class<?> configClass) {
        // configNames() 메서드가 없는 클래스들을 위한 리플렉션 기반 대안
        // 클래스 변수를 조회하여 상수값들을 추출한다.
        Set<String> result = new LinkedHashSet<>();  

        Arrays.stream(configClass.getFields())
            .filter(field -> Modifier.isStatic(field.getModifiers())) // static 필드만 추출 (상수)
            .filter(field -> field.getType() == String.class)   // String 타입만 추출
            .filter(field -> !field.getName().startsWith("DEFAULT_")) // DEFAULT_로 시작하는 필드 제외
            .filter(field -> !field.getName().endsWith("_DOC")) // _DOC로 끝나는 필드 제외
            .filter(field -> !field.getName().endsWith("_NOTE")) // _NOTE로 끝나는 필드 제외
            .forEach(field -> {
                try {
                    String keyValue = (String) field.get(null); // static이라 null
                    // System.out.println(keyValue);
                    result.add(keyValue);
                } catch (IllegalAccessException exception) {
                    throw new RuntimeException(exception);
                }
            });
        return result;
    }
    public static Set<String> filterContaining(Set<String> values, String token) {
        // Set에서 특정단어 포함한 것만 남기기
        return values.stream()
            .filter(value -> value != null && value.contains(token))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

}

