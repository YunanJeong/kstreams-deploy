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
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, consumerGroup);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, broker);
        
        // # AUTO_OFFSET_RESET_CONFIG (auto.offset.reset)
        // #  최초실행시(consumer-group이 없을 때) 입력 토픽을 처음(earliest)부터 읽을지, 최신(latest)부터 읽을지 결정
        // #  consumer-group이 있으면 아무리 재실행해도 consumer-group의 설정을 따름
        // #  보통 consumer의 default는 latest이지만, "KStreams에서의 default는 earliest임"
        if (autoOffsetReset == "latest"){
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset); 
        }

        // output 토픽 압축 // gzip, snappy, lz4, zstd
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, producerCompressionType);

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
