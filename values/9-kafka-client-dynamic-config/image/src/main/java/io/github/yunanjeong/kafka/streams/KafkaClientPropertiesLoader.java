package io.github.yunanjeong.kafka.streams;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SecurityConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.streams.StreamsConfig;


public class KafkaClientPropertiesLoader {
    
    private static final Set<String> AVAILABLE_CONFIG_NAMES = Collections.unmodifiableSet(buildAvailableConfigNames());

    public Properties load()  {
        return loadFromEnvPrefix(System.getenv(), "STREAMS_");
    }

    public Properties loadAndValidate()  {
        return loadFromEnvPrefixAndValidate(System.getenv(), "STREAMS_");
    }

    public Properties loadFromEnvPrefixAndValidate(Map<String, String> envs, String prefix)  {
        Properties props = loadFromEnvPrefix(envs, prefix);
        validateConfigNamesOrThrow(AVAILABLE_CONFIG_NAMES, props.stringPropertyNames());
        return props;
    }

    public Properties loadFromEnvPrefix(Map<String, String> envs, String prefix)  {
        /**
         * 특정 prefix를 가진 환경변수 key들을 찾아,
         * 해당 prefix를 제거하고 언더스코어(_)를 점(.)으로 바꾼 후
         * 소문자로 변환하여 Kafka 클라이언트 설정 키로 사용.
         * 
         * 예: "STREAMS_BOOTSTRAP_SERVERS" -> "bootstrap.servers"
         * 
         * @return 변환된 Kafka 클라이언트 설정이 담긴 Properties 객체
         */
        Properties props = new Properties();
        
        envs.forEach((envKey, envValue) -> {
            if (envKey.startsWith(prefix)) {
                String propertyName = envKey.substring(prefix.length()).replace("_", ".").toLowerCase();
                props.put(propertyName, envValue);
            }
        });

        return props;
    }

    private void validateConfigNamesOrThrow(Set<String> availNames, Set<String> names) {
        /**
         * 유효한 Kafka 클라이언트 설정 키인지 검사.
         * 특히 성능 옵션 설정에서 오타 발생시 무시되고 기본값이 사용될 수 있는데, 당장 알아차리기 힘드므로 Exception 발생시킴.
         */

        List<String> invalidNames = new ArrayList<>();

        for (String name : names) {
            if (name.startsWith(StreamsConfig.CLIENT_TAG_PREFIX)) continue; // client.tag.* 전체허용. 무작위 key-value 쌍이 올 수 있음
            if (name.startsWith("metrics.context.")) continue; // metrics.context.* 전체허용. 뒤에 무작위 key-value 쌍이 올 수 있음
            if (name.startsWith(StreamsConfig.TOPIC_PREFIX)) continue; // topic.* 전체허용. 이는 카프카 브로커 버전에 따라 다를 수 있기 때문

            if (!availNames.contains(name)) {
                invalidNames.add(name);
            }
        }

        // 타당치 않은 키가 있으면 예외 발생
        if (!invalidNames.isEmpty()) {
            throw new IllegalArgumentException("Unknown Kafka Client Properties Detected: " + invalidNames);
        }

    }


    private static Set<String> buildAvailableConfigNames() {
        /* 
         * 사용가능한 주요 설정키 집합을 생성한다.
         * 오타 및 유효성 검증용이므로 문자열 하드코딩을 피하고 API로 추출한다.
         * 버전에 따라 일부 키가 다를 수 있음
         * 
         * 이 함수 결과(Set)에 포함하지 않음:
         * - StreamsConfig.InternalConfig 관련설정 (거의 안 씀)
         *
         * 이 함수 결과(Set)에 포함하지 않으며, 검증 로직에서 prefix로 전체 허용:
         * - client.tag.<key>=<value>
         * - topic.<key>=<value>
         * - metrics.context.<key>=<value>
         */

        List<Set<String>> namesList = new ArrayList<>();
        // 주요 Kafka Client 설정 키들
        namesList.add(StreamsConfig.configDef().names());
        namesList.add(ConsumerConfig.configNames());
        namesList.add(ProducerConfig.configNames());
        namesList.add(AdminClientConfig.configNames());

        // 추가로 configNames() 메서드가 없는 클래스들에서 키 이름 추출
        namesList.add(getConfigNames(CommonClientConfigs.class));
        namesList.add(getConfigNames(TopicConfig.class));
        namesList.add(getConfigNames(SslConfigs.class));
        namesList.add(filterContaining(getConfigNames(SaslConfigs.class), "sasl."));
        namesList.add(getConfigNames(SecurityConfig.class));
        
        // 접두사 붙인 키들도 추가
        namesList.add( withPrefix(StreamsConfig.CONSUMER_PREFIX,         ConsumerConfig.configNames())    );
        namesList.add( withPrefix(StreamsConfig.MAIN_CONSUMER_PREFIX,    ConsumerConfig.configNames())    );
        namesList.add( withPrefix(StreamsConfig.RESTORE_CONSUMER_PREFIX, ConsumerConfig.configNames())    );
        namesList.add( withPrefix(StreamsConfig.GLOBAL_CONSUMER_PREFIX,  ConsumerConfig.configNames())    );
        namesList.add( withPrefix(StreamsConfig.PRODUCER_PREFIX,         ProducerConfig.configNames())    );
        namesList.add( withPrefix(StreamsConfig.ADMIN_CLIENT_PREFIX,     AdminClientConfig.configNames()) );

        // 모든 설정이름 합치고 중복 제거
        Set<String> result = new HashSet<>();
        for (Set<String> names : namesList) {
            result.addAll(names);
        }

        return result;
    }

    private static Set<String> getConfigNames(Class<?> configClass) {
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
    private static Set<String> withPrefix(String prefix, Set<String> names) {
        // Set 각 요소에 prefix 붙여서 반환
        Set<String> result = new HashSet<>();
        for (String name : names) {
            result.add(prefix + name);
        }
        return result;
    }
    private static Set<String> filterContaining(Set<String> values, String token) {
        // Set에서 특정단어 포함한 것만 남긴 후 반환
        return values.stream()
            .filter(value -> value != null && value.contains(token))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }


}
