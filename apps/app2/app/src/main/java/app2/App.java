package app2;

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

    public static void main(final String[] args) throws Exception {
        String broker = System.getenv("KAFKA_BROKER");
        String srcTopic = System.getenv("KAFKA_SRC_TOPIC");
        String sinkTopic = System.getenv("KAFKA_SINK_TOPIC");
        System.out.println("Kafka broker: " + broker);
        logger.info("Kafka srcTopic: " + srcTopic);
        logger.info("Kafka sinkTopic: " + sinkTopic);

        // 설정값
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "filterkey-application");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, broker);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        // 스트림
        StreamsBuilder builder = new StreamsBuilder();
        // KStream<String, String> source = builder.stream(srcTopic);

        //다수 토픽 읽을 시, regex 사용방법
        KStream<String, String> source = builder.stream(Pattern.compile(srcTopic));

        ObjectMapper mapper = new ObjectMapper();
        // 필터링 0: 채팅로그만 남긴다.
        KStream<String, String> filteredStream = source.filter((key, value) -> {
            try {
                JsonNode jsonNode = mapper.readTree(value);      // Record(Stream)에서 value부분을 json으로 읽는다.
                String message = jsonNode.get("message").asText();
                String[] parsedMsg = message.split("\\|");
                String tableName = parsedMsg[0];
                return (tableName.equals(mChatLog0) || tableName.equals(mChatLog1));  //true가 아니면 버림
            } catch (Exception e) {
                return false;
            }
        });

        // 필터링 1: userId를 추출하여 Key로 할당한다.
        KStream<String, String> filterkeyStream = filteredStream.map((key, value) -> {
            try {
                JsonNode jsonNode = mapper.readTree(value);      // Record(Stream)에서 value부분을 json으로 읽는다.
                String message = jsonNode.get("message").asText();
                String[] parsedMsg = message.split("\\|");
                String userId = parsedMsg[8];
                return new KeyValue<>(userId, message); //
            } catch (Exception e) {
                e.printStackTrace();
                return new KeyValue<>(null, value); //
            }
        });
        filterkeyStream.to(sinkTopic);  //

        // 스트림 시작
        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }


}

