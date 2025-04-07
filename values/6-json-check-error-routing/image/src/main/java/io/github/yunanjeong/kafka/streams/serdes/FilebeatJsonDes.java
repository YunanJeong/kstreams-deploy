package io.github.yunanjeong.kafka.streams.serdes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class FilebeatJsonDes implements Serde<JsonNode> {

    // ObjectMapper는 thread-safe하므로 static final로 선언
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSerializer serializer;
    private final JsonDeserializer deserializer;

    public FilebeatJsonDes() {
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
        // filebeat 랩핑 메시지 필드 명세
        //  # fields: filebeat 커스텀설정으로 남긴것
        //  # log(or source): filebeat가 수집한 파일의 경로와 오프셋
        //  # message: 원본파일로그(비즈니스 데이터)
        @Override
        public JsonNode deserialize(String topic, byte[] data) {
            try {
                JsonNode filebeatJsonNode = objectMapper.readTree(data);
                String messageStr = filebeatJsonNode.get("message").asText();
                
                // Json 검증 후 Return
                JsonNode messageJsonNode = objectMapper.readTree(messageStr);
                if (messageJsonNode.isObject() || messageJsonNode.isArray()){
                    return messageJsonNode;
                }else{
                    throw new IllegalArgumentException("필드값이 JsonNode는 맞는데, Json은 아님") ;
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                
                // filebeat 랩핑 메시지가 Json이 아닌 경우
                // message 필드값이 Json이 아닌 경우
                // message 필드가 없는 경우 등

                // 오류 정보를 포함한 JSON 객체로 반환
                ObjectNode errorNode = objectMapper.createObjectNode();
                errorNode.put("error", "Filebeat Message Deserialization Error");
                errorNode.put("data", new String(data)); // 원본 데이터를 문자열로 포함
                return errorNode;
            }
        }
    }
}


// FilebeatJsonDes는
  //Filebeat의 message(비즈니스 데이터)가 Json인 경우를 처리하고,
  //이슈케이스(기타 전송실패, 원본파일로그 손상 등)를 버리지말고 라우팅하여 모니터링하기 위해 사용함

// filebeat 랩핑 메시지 예시
  // filebeat 설정에 따라 다르나, 보편적인 경우를 나타낸 것임
  
// {
//     "@timestamp": "2023-10-10T12:34:56.789Z",
//     "message": "원본파일로그(비즈니스 데이터)",
//     "fields": {
//       "custom_field": "value"
//     },
//     "host": {
//       "name": "호스트 이름"
//     },
//     "log": {
//       "offset": 12345,
//       "file": {
//         "path": "/var/log/example.log"
//       }
//     },
//     "input": {
//       "type": "log"
//     },
//     "agent": {
//       "name": "filebeat",
//       "version": "7.10.0"
//     }
// }