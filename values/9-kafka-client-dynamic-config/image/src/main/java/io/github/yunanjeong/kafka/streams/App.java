package io.github.yunanjeong.kafka.streams;

import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;

public class App {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(final String[] args) throws NoSuchAlgorithmException {

        Topology topology = new TopologyMaker().getMyTopology();
        Properties props = new KafkaClientPropertiesLoader().loadAndValidate();
        KafkaStreams kafkaStreams = new KafkaStreams(topology, props);

        LOG.info("Starting Main Appication ... Target Kafka Broker: " + props.get(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG));
        LOG.info("Application ID: " + props.get(StreamsConfig.APPLICATION_ID_CONFIG));
        LOG.info("KafkaClientProperties Overrides: ");
        props.stringPropertyNames().stream()
            .sorted()
            .forEach(key -> LOG.info("{}={}", key, props.getProperty(key)));

        kafkaStreams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));
    }
}
