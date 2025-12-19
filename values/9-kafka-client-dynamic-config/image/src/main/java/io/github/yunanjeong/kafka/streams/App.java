package io.github.yunanjeong.kafka.streams;

import java.util.Properties;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;

public class App {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(final String[] args) throws NoSuchAlgorithmException {
        String consumerGroup = System.getenv("CONSUMER_GROUP");
        String broker = System.getenv("KAFKA_BROKER");
        String autoOffsetReset = System.getenv("AUTO_OFFSET_RESET");
        String producerCompressionType = System.getenv("PRODUCER_COMPRESSION_TYPE");

        Properties props = new Properties();
        KafkaClientPropertiesLoader kcpl = new KafkaClientPropertiesLoader();

        // props.put(StreamsConfig.APPLICATION_ID_CONFIG, consumerGroup);
        // props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, broker);
        // props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, producerCompressionType);

        TopologyMaker topologyMaker = new TopologyMaker();
        Topology topology = topologyMaker.getMyTopology();
        KafkaStreams kafkaStreams = new KafkaStreams(topology, props);
        
        //AdminClient 및 NewTopic 클래스로 Kafka에 Sink Topic 생성가능
        
        //재실행할 때마다 로컬앱 완전초기화. 테스트시만 사용
        //kafkaStreams.cleanUp();

        LOG.info("Starting Main Appication ... Target Kafka Broker: " + broker);
        kafkaStreams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));
    }
}
