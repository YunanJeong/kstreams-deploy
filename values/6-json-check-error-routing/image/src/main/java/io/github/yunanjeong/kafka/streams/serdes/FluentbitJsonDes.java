package io.github.yunanjeong.kafka.streams.serdes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class FluentbitJsonDes implements Serde<JsonNode> {

    // ObjectMapper는 thread-safe하므로 static final로 선언
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSerializer serializer;
    private final JsonDeserializer deserializer;

    public FluentbitJsonDes() {
        this.serializer = new JsonSerializer();
        this.deserializer = new JsonDeserializer();
    }
    
    @Override
    public Serializer<JsonNode> serializer() {
        return this.serializer;
    }

    @Override
    public Deserializer<JsonNode> deserializer() {
        return this.deserializer;
    }

    public static class JsonSerializer implements Serializer<JsonNode> {
        
        @Override
        public byte[] serialize(String topic, JsonNode data) {
            // serialize 사용하지 않음
            return null;
        }
    }

    public static class JsonDeserializer implements Deserializer<JsonNode> {

        @Override
        public JsonNode deserialize(String topic, byte[] data) {
            try {
                JsonNode fluentbitJsonNode = objectMapper.readTree(data);
                String logStr = fluentbitJsonNode.get("log").asText();
                
                // Json 검증 후 Return
                JsonNode logJsonNode = objectMapper.readTree(logStr);
                if (logJsonNode.isObject() || logJsonNode.isArray()){
                    return logJsonNode;
                }else{
                    throw new IllegalArgumentException("필드값이 JsonNode는 맞는데, Json은 아님") ;
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                
                // fluentbit 랩핑 메시지가 Json이 아닌 경우
                // log 필드값이 Json이 아닌 경우
                // log 필드가 없는 경우

                // 오류 정보를 포함한 JSON 객체로 반환
                ObjectNode errorNode = objectMapper.createObjectNode();
                errorNode.put("detected_at", Instant.now()   //RFC3339 서버 시각 (절대 시간)
                                                    .atZone(ZoneId.of("Asia/Seoul"))  // 시간대 표기법 선택(시간이 바뀌는 것은 아님)
                                                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)); 
                errorNode.put("deserial_error", "Fluentbit Log Deserialization Error");
                errorNode.put("data", new String(data)); // 원본 데이터를 문자열로 포함
                return errorNode;
            }
        }
    }
}

// FluentbitJsonDes는
  //Fluentbit의 log(비즈니스 데이터)가 Json인 경우를 처리하고,
  //이슈케이스(기타 전송실패, 원본파일로그 손상 등)를 버리지말고 라우팅하여 모니터링하기 위해 사용함

// fluentbit 랩핑 메시지 예시
  // fluentbit 설정에 따라 다르나, 보편적인 경우를 나타낸 것임

// {
//     "date": "2023-10-10T12:34:56",
//     "log": "원본파일로그(비즈니스 데이터)",
//     "kubernetes": {
//       "pod_name": "example-pod",
//       "namespace_name": "default",
//       "container_name": "example-container",
//       "docker_id": "abc123",
//       "labels": {
//         "app": "example"
//       },
//       "annotations": {
//         "annotation_key": "annotation_value"
//       }
//     }
// }