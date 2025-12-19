package io.github.yunanjeong.kafka.streams;

import java.util.Map;
import java.util.Properties;

public class KafkaClientPropertiesLoader {
    
    /**
     * 특정 prefix를 가진 환경변수 key들을 찾아,
     * 해당 prefix를 제거하고 언더스코어(_)를 점(.)으로 바꾼 후
     * 소문자로 변환하여 Kafka 클라이언트 설정 키로 사용.
     * 
     * 예: "STREAMS_BOOTSTRAP_SERVERS" -> "bootstrap.servers"
     * 
     * @param prefix 환경변수 접두사
     * @return 변환된 Kafka 클라이언트 설정이 담긴 Properties 객체
     */
    public Properties loadFromEnvPrefix(Map<String, String> envMap, String prefix)  {
        Properties props = new Properties();
        
        envMap.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                String configKey = key.substring(prefix.length()).replace("_", ".").toLowerCase();
                props.put(configKey, value);
            }
        });
        
        return props;
    }

    public Properties loadFromEnv()  {
        return loadFromEnvPrefix(System.getenv(), "STREAMS_");
    }
}
