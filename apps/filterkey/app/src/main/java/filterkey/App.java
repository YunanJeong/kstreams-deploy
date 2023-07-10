package filterkey;

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

import java.util.Arrays;
import java.util.Properties;

public class App {

   public static void main(final String[] args) throws Exception {
       String broker = System.getenv("KAFKA_BROKER");
       String srcTopic = System.getenv("KAFKA_SRC_TOPIC");
       String sinkTopic = System.getenv("KAFKA_SINK_TOPIC");
       System.out.println("Kafka broker: " + broker);
       System.out.println("Kafka srcTopic: " + srcTopic);
       System.out.println("Kafka sinkTopic: " + sinkTopic);

       // 로그 레벨이 ERROR 인 로그만 필터링
       String filterField = "log_level";
       String filterValue = "ERROR";
       // user_id 를 키로 저장 
       String keyField = "user_id";

       // 설정값
       Properties props = new Properties();
       props.put(StreamsConfig.APPLICATION_ID_CONFIG, "filterkey-application");
       props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, broker);
       props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
       props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

       // 스트림
       StreamsBuilder builder = new StreamsBuilder();
       KStream<String, String> source = builder.stream(srcTopic);

       // 필터링 
       KStream<String, String> filteredStream = source.filter((key, value) -> {
           try {
               ObjectMapper mapper = new ObjectMapper();
               JsonNode jsonNode = mapper.readTree(value);
               String val = jsonNode.get(filterField).asText();
               return filterValue.equals(val);
           } catch (Exception e) {
               return false;
           }
       });

       // 키 지정
       KStream<String, String> filterkeyStream = filteredStream.map((key, value) -> {
           try {
               ObjectMapper mapper = new ObjectMapper();
               JsonNode jsonNode = mapper.readTree(value);
               String newKey = jsonNode.get(keyField).asText();
               return new KeyValue<>(newKey, value);
           } catch (Exception e) {
               e.printStackTrace();
               return new KeyValue<>(null, value);
           }
       });
       filterkeyStream.to(sinkTopic); 

       // 스트림 시작
       KafkaStreams streams = new KafkaStreams(builder.build(), props);
       streams.start();

       Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
   }
}