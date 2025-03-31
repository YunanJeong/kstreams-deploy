package io.github.yunanjeong.kafka.streams.examples;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.processor.TopicNameExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

//import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

//import java.util.regex.Pattern;
// import java.util.HashMap;
// import java.util.UUID;
import java.util.Map;
import io.github.yunanjeong.kafka.streams.examples.serdes.JsonNodeSerde;


public class TopologyMaker {
    
    private static final Logger logger = LoggerFactory.getLogger(TopologyMaker.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String INPUT_TOPIC = System.getenv("INPUT_TOPIC"); // 환경변수는 helm value파일에서 관리
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OUTPUT_FORMAT = DateTimeFormatter.ofPattern("_yyyyMMdd");
    
    // JAVA 15 이상에서 TEXT BLOCKS 지원
    // schema registry를 사용하지않고 그냥 record에 schema를 넣을 때 다음과 같은 형식으로 쓰면된다.
    // 카프카 지원 스키마 타입: https://kafka.apache.org/20/javadoc/org/apache/kafka/connect/data/Schema.Type.html
    // 사용가능한 시간 형식: https://kafka.apache.org/20/javadoc/org/apache/kafka/connect/data/package-summary.html
    private static final String SCHEMA_STRING = """ 
        {
            "type": "struct",
            "fields": [
                {"field": "svrid", "type": "int32"},
                {"field": "dteventtime", "type": "string", "name": "org.apache.kafka.connect.data.Timestamp" },
                {"field": "loguuid", "type": "int64"},
                {"field": "content", "type": "string"}
            ]
        }
        """;

    // private Pattern inputTopicPattern = Pattern.compile(INPUT_TOPIC);
    private StreamsBuilder streamsBuilder = new StreamsBuilder();
    private JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
    
    public Topology getMyTopology() {
        logger.info("Topology Starts");
        KStream<String, JsonNode> inputStream = streamsBuilder.stream(
            INPUT_TOPIC,
            Consumed.with(Serdes.String(), jsonNodeSerde));
        
        // 분기 처리    
        Map<String, KStream<String,JsonNode>> branches = inputStream.split()
            .branch((key, jsonNode) -> isTarget(jsonNode),
                Branched.withFunction(stream -> processTargetLog(stream), "target"))
            .noDefaultBranch();  //나머지 버림
        
        // 분기된 스트림(branch)의 후속처리는 위와 같이 withFunction으로 처리한다.
        // 이슈: 아래처럼 branch를 받아서 후속처리가 안됨.(deprecated branch에선 가능했는데)
        // KStream<String, JsonNode> s3Stream = branches.get("s3");
        // s3Streams.filter(...)

        return streamsBuilder.build();
    }

    private boolean isTarget(JsonNode jsonNode){
        // 내가 처리할 로그인가?
        try{
            String name = jsonNode.get("name").asText();
            return name.equalsIgnoreCase("logtype1") || name.equalsIgnoreCase("logtype2");
        } catch (Exception e){  // Json 에러 대응
            e.printStackTrace();
            return false;
        }
    }

    private KStream<String, JsonNode> processTargetLog(KStream<String, JsonNode> stream){
        logger.info("Processing Target Log ... ");
        if (stream == null){
            return null;
        }

        KStream<String, JsonNode> outputStream = stream.map((key, jsonNode) -> {   
            try {
                JsonNode schemaNode = objectMapper.readTree(SCHEMA_STRING);

                ObjectNode payloadNode = objectMapper.createObjectNode();
                payloadNode.put("svrid", jsonNode.get("SvrId").asInt());
                payloadNode.put("dteventtime", jsonNode.get("dtEventTime").asText());
                payloadNode.put("loguuid", jsonNode.get("logUUID").asLong());  // int64 
                payloadNode.put("content", objectMapper.writeValueAsString(jsonNode));
    
                ObjectNode finalNode = objectMapper.createObjectNode();
                finalNode.set("schema", schemaNode);
                finalNode.set("payload", payloadNode);
                
                // dynamicOutputTopicName에 "name"을 넘겨줘야하는데, record key로 하는게 제일 효율적
                // 람다 표현식 내부에선 표현식 외부 변수에 값 할당 불가
                // 객체를 쓰면되긴한데 메모리 비효율적
                // 근데 key에 특정 "name"만 쓰면, 토픽의 파티션을 고르게 사용하지 못하므로 uuid를 붙여준다. 랜덤생성해도 되고 로그 uid가 있으면 그걸 써도됨.
                String tableName = jsonNode.get("name").asText().toLowerCase(); // 출력 테이블명(토픽명) 동적 처리 용도
                String randomKey = jsonNode.get("logUUID").asText(); // 고유값 할당하여 토픽 파티션마다 고른 분배 보장  // UUID.randomUUID().toString(); 

                String resultKey = tableName + "_" + randomKey;
                JsonNode resultValue = finalNode;
                return KeyValue.pair(resultKey, resultValue);
            } catch (Exception e) {
                e.printStackTrace();
                return KeyValue.pair(null, jsonNode);
            }
        });

        TopicNameExtractor<String, JsonNode> dynamicOutputTopicName = (key, value, recordContext) -> {
            // 출력토픽명을 동적으로 정의 및 반환하는 람다표현식(변수)
            // key: tablename_randomuuid,  value: 정산로그 및 스키마
            String tableName = key.substring(0, key.indexOf('_')); // split보다 substring이 연산효율 좋음
            String dateSuffix = getDateSuffix(value.get("payload").get("dteventtime").asText());
            return "my_log_" + tableName + dateSuffix;
        };

        outputStream.to(dynamicOutputTopicName, Produced.with(Serdes.String(), jsonNodeSerde));
        return stream; //원본을 그대로 return 해줘야 branch가 정상작동
    }

    public String getDateSuffix(String dateTime){
        // "yyyy-MM-dd HH:mm:ss"을 "_yyyyMMdd"로 반환
        LocalDateTime date = LocalDateTime.parse(dateTime, INPUT_FORMAT);
        return date.format(OUTPUT_FORMAT);
    }

}
