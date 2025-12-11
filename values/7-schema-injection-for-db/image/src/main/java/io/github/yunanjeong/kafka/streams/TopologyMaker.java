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
            (key, value) -> isCurrency(value) || isBuy(value)
        );

        KStream<String, JsonNode> outputStream = bizStream.map((key, jsonNode) -> {   
            try {
                JsonNode schemaNode = objectMapper.readTree(SCHEMA_STRING);

                ObjectNode payloadNode = objectMapper.createObjectNode();
                payloadNode.put("server_no", jsonNode.get("server_no").asInt());
                payloadNode.put("date_time", TimeUtils.convert(jsonNode.get("date_time").asText()));
                payloadNode.put("uuid"     , jsonNode.get("uuid").asText());  
                payloadNode.put("content"  , objectMapper.writeValueAsString(jsonNode));

                ObjectNode finalNode = objectMapper.createObjectNode();
                finalNode.set("schema", schemaNode);
                finalNode.set("payload", payloadNode);
                
                String topicName = jsonNode.get("log_type").asText().toLowerCase(); // 출력 테이블명(토픽명) 동적 처리 용도
                String randomKey = jsonNode.get("uuid").asText(); // 고유값 할당하여 토픽 파티션마다 고른 분배 보장  // UUID.randomUUID().toString();  // DigestUtils.sha256Hex(input);

                String resultKey = topicName + "#" + randomKey;
                JsonNode resultValue = finalNode;
                return KeyValue.pair(resultKey, resultValue);
            } catch (Exception e) {
                LOG.error("Error in Editing: ", e); // e.printStackTrace();
                return KeyValue.pair(null, jsonNode);
            }
        });

        TopicNameExtractor<String, JsonNode> dynamicOutputTopicName = (key, value, recordContext) -> {
            // 출력토픽명을 동적으로 정의 및 반환하는 람다표현식(변수)
            // key: topicname_randomuuid,  value: 스키마 및 페이로드
            String outputTopicName = key.substring(0, key.indexOf('#')); // split보다 substring이 연산효율 좋음
            return "tb_log_" + outputTopicName;
        };
        
        outputStream.to(dynamicOutputTopicName, Produced.with(Serdes.String(), jsonNodeSerde));
   
        return streamsBuilder.build();
    }

    private boolean isCurrency(JsonNode value){
        if (value == null) return false;
        if (value.get("currency_type") == null) return false;
        if (value.get("log_type") == null) return false;
        
        int currencyType = value.get("currency_type").asInt();
        String logType = value.get("log_type").asText().toLowerCase();
        return (currencyType == 1) && CURRENCY_LOG_TYPES.contains(logType); 
    }
    private boolean isBuy(JsonNode value){
        if (value == null) return false;
        if (value.get("log_type") == null) return false;

        String logType = value.get("log_type").asText().toLowerCase();
        return BUY_LOG_TYPES.contains(logType);
    }

}
