package io.github.yunanjeong.kafka.streams.serdes;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class JsonNodeSerde implements Serde<JsonNode> {

    // ObjectMapper는 thread-safe하므로 static final로 선언
    private static final ObjectMapper objectMapper = 
        new ObjectMapper()
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true); // JSON 파싱 시 큰 숫자의 정밀도 유지 (BigDecimal)

    private final JsonSerializer serializer;
    private final JsonDeserializer deserializer;

    public JsonNodeSerde() {
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
            try {
                return objectMapper.writeValueAsBytes(data);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    public static class JsonDeserializer implements Deserializer<JsonNode> {

        @Override
        public JsonNode deserialize(String topic, byte[] data) {
            try {
                JsonNode dataJsonNode = objectMapper.readTree(data);
                if (dataJsonNode.isObject() || dataJsonNode.isArray()){
                    return dataJsonNode;
                }else{
                    throw new IllegalArgumentException("JsonNode는 맞는데, Json은 아님") ;
                }
            } catch (Exception e) {
                e.printStackTrace();

                // JSON 형식이 아닌 경우 오류 정보를 포함한 JSON 객체 반환
                ObjectNode errorNode = objectMapper.createObjectNode();
                errorNode.put("detected_at", Instant.now()   //RFC3339 서버 시각 (절대 시간)
                                                    .atZone(ZoneId.of("Asia/Seoul"))  // 시간대 표기법 선택(시간이 바뀌는 것은 아님)
                                                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)); 
                errorNode.put("deserial_error", "Invalid JSON format");
                errorNode.put("data", new String(data)); // 원본 데이터를 문자열로 포함
                return errorNode;
                
                // return null; // JSON 형식이 아닌 경우 null 반환
            }
        }
    }
}