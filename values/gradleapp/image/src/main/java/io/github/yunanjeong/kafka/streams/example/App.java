package io.github.yunanjeong.kafka.streams.example;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Pattern;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private static final String mChatLog0 = "chatlogflow";
    private static final String mChatLog1 = "chatflow";
    private static final String srcTopic = "src_topic";
    private static final String sinkTopic = "sink_topic";
    public static void main(final String[] args) throws Exception {
        String broker = System.getenv("KAFKA_BROKER");
        System.out.println("Kafka broker: " + broker);
        logger.info("Kafka srcTopic: " + srcTopic);
        logger.info("Kafka sinkTopic: " + sinkTopic);
    }


}

