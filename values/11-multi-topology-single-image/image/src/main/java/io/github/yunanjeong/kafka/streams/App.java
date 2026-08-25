package io.github.yunanjeong.kafka.streams;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;

import io.github.yunanjeong.kafka.streams.topologies.TopologyProvider;
import io.github.yunanjeong.kafka.streams.topologies.TopologyRegistry;

/**
 * 단일 이미지 안에 여러 토폴로지를 담고, 환경변수 TOPOLOGY로 하나를 골라 실행한다.
 *
 * 같은 이미지로 릴리스만 나눠 띄우면(TOPOLOGY 값만 다르게) 스트림 처리별로 파드가 뜬다.
 * 이미지 빌드·배포 파이프라인은 하나로 유지하면서, 처리 단위별 스케일·자원은 따로 준다.
 * (application.id는 릴리스마다 반드시 달라야 한다. 같으면 서로 다른 로직이 한 컨슈머그룹을 나눠먹는다.)
 */
public class App {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    private static final String TOPOLOGY_ENV = "TOPOLOGY";

    public static void main(final String[] args) {

        TopologyConfig config = TopologyConfig.fromEnv();
        String selected = config.get(TOPOLOGY_ENV, null);

        TopologyProvider provider = TopologyRegistry.find(selected).orElse(null);
        if (provider == null) {
            LOG.error("Unknown or missing environment variable {}: {}", TOPOLOGY_ENV, selected);
            LOG.error("Available topologies:");
            TopologyRegistry.providers()
                .forEach(p -> LOG.error("  - {} : {}", p.name(), p.description()));
            System.exit(1);
            return;
        }

        LOG.info("Selected topology: {} ({})", provider.name(), provider.description());

        Topology topology = provider.build(config);
        Properties props = new KafkaClientPropertiesLoader().loadAndValidate();
        KafkaStreams kafkaStreams = new KafkaStreams(topology, props);

        props.stringPropertyNames().stream()
            .sorted()
            .forEach(key -> LOG.info("KafkaClientProperties Overrides: {}={}", key, props.getProperty(key)));

        LOG.info("Starting Main Appication ... Target Kafka Broker: " + props.get(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG));
        LOG.info("Application ID: " + props.get(StreamsConfig.APPLICATION_ID_CONFIG));

        kafkaStreams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));
    }
}
