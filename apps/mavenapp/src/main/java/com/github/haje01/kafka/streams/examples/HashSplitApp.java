package com.github.haje01.kafka.streams.examples;

import java.util.Properties;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;


public class HashSplitApp {

    private static final Logger logger = LoggerFactory.getLogger(HashSplitApp.class);

    public static void main(final String[] args) throws NoSuchAlgorithmException {
        String broker = System.getenv("KAFKA_BROKER");
        String sourceTopic = System.getenv("KAFKA_SOURCE_TOPIC");
        String sinkTopicA = System.getenv("KAFKA_SINK_TOPIC_A");
        String sinkTopicB = System.getenv("KAFKA_SINK_TOPIC_B");
        logger.info("KAFKA_BROKER: " + broker);
        logger.info("KAFKA_SOURCE_TOPIC: " + sourceTopic);
        logger.info("KAFKA_SINK_TOPIC_A: " + sinkTopicA);
        logger.info("KAFKA_SINK_TOPIC_B: " + sinkTopicB);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "hashsplit-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, broker);

        var hashSplitTopology = new HashSplitTopology(sourceTopic, sinkTopicA, sinkTopicB);
        var topology = hashSplitTopology.createTopology();
        var streams = new KafkaStreams(topology, props);
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}
