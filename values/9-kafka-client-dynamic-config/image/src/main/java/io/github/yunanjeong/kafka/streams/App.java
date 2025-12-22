package io.github.yunanjeong.kafka.streams;

import java.util.Properties;
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
        
        //AdminClient 및 NewTopic 클래스로 Kafka에 Sink Topic 생성가능
        
        //재실행할 때마다 로컬앱 완전초기화. 테스트시만 사용
        //kafkaStreams.cleanUp();
        
        LOG.info("Starting Main Appication ... Target Kafka Broker: " + props.get(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG));
        LOG.info("Application ID: " + props.get(StreamsConfig.APPLICATION_ID_CONFIG));
        LOG.info("All Kafka Client Properties:\n{}", props.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(java.util.stream.Collectors.joining("\n")));

        kafkaStreams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));
    }
}
