package io.github.yunanjeong.kafka.streams;

import java.util.Properties;

public class KafkaClientPropertiesLoader {
    

    public Properties loadFromEnvPrefix(String prefix)  {
        Properties props = new Properties();
        
        System.getenv().forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                String configKey = key.substring(prefix.length()).replace("_", ".").toLowerCase();
                props.put(configKey, value);
            }
        });
        
        return props;
    }
}
