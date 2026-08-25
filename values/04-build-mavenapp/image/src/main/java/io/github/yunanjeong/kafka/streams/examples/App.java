package io.github.yunanjeong.kafka.streams.examples;

import java.util.Properties;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;

public class App {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(final String[] args) throws NoSuchAlgorithmException {
        String broker = System.getenv("KAFKA_BROKER");
        logger.info("KAFKA_BROKER: " + broker);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "my-example-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, broker);
        
        TopologyMaker topologyMaker = new TopologyMaker();
        Topology topology = topologyMaker.getMyTopology();
        KafkaStreams kafkaStreams = new KafkaStreams(topology, props);
        
        //AdminClient 및 NewTopic 클래스로 Kafka에 Sink Topic 생성가능
        
        //오프셋 초기화. 입력토픽의 처음부터 읽음. 테스트시만 사용
        //kafkaStreams.cleanUp();
        
        kafkaStreams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));
    }
}
