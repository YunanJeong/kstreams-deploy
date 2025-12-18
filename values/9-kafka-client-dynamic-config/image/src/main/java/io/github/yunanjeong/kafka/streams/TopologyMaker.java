package io.github.yunanjeong.kafka.streams;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.processor.TopicNameExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.yunanjeong.kafka.streams.serdes.FilebeatJsonDes;
import io.github.yunanjeong.kafka.streams.serdes.JsonNodeSerde;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.regex.Pattern;


public class TopologyMaker { // extends Security
    
    private static final Logger LOG = LoggerFactory.getLogger(TopologyMaker.class);
    private static final ObjectMapper objectMapper = 
        new ObjectMapper()
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true); // JSON 파싱 시 큰 숫자의 정밀도 유지 (BigDecimal)

    // private static final String INPUT_TOPIC = System.getenv("INPUT_TOPIC"); 
    private static final Pattern INPUT_TOPIC_REGEX = Pattern.compile(System.getenv("INPUT_TOPIC_REGEX"));  

    private static final String SCHEMA_STRING = """ 
        {
            "type": "struct",
            "fields": [
                {"field": "server_no", "type": "int32"},
                {"field": "date_time", "type": "string"},
                {"field": "uuid", "type": "string"},
                {"field": "content", "type": "string"}
            ]
        }
        """;
    private static final List<String> CURRENCY_LOG_TYPES = 
        List.of("currency_a", "currency_b", "currency_c", "currency_d");
    private static final List<String> BUY_LOG_TYPES = 
        List.of("exchange_buy", "layby_buy");

    private StreamsBuilder streamsBuilder = new StreamsBuilder();
    private JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
    private FilebeatJsonDes filebeatJsonDes = new FilebeatJsonDes();

    public Topology getMyTopology() throws NoSuchAlgorithmException {
        LOG.info("Getting topology ...");
        KStream<String, JsonNode> inputStream = streamsBuilder.stream(
            INPUT_TOPIC_REGEX,
            Consumed.with(Serdes.String(), filebeatJsonDes)
        );
        
        // Json 검증
        KStream<String, JsonNode> validStream = inputStream.filter(
            (key, value) -> value.get("deserial_error") == null
        );

        // 비즈니스 데이터 필터링
        KStream<String,JsonNode> bizStream = validStream.filter(
            (key, value) -> isBiz(value) 
        );


        outputStream.to("output.topic", Produced.with(Serdes.String(), jsonNodeSerde));
   
        return streamsBuilder.build();
    }

    private boolean isBiz(JsonNode value){
        if (value == null) return false;
        return true;
    }


}
