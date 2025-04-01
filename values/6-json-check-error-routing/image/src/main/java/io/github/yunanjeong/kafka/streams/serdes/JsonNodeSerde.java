package io.github.yunanjeong.kafka.streams.serdes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class JsonNodeSerde implements Serde<JsonNode> {

    // ObjectMapper는 thread-safe하므로 static final로 선언
    private static final ObjectMapper objectMapper = new ObjectMapper();
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
                return objectMapper.readTree(data);
            } catch (Exception e) {
                e.printStackTrace();

                // JSON 형식이 아닌 경우 오류 정보를 포함한 JSON 객체 반환
                ObjectNode errorNode = objectMapper.createObjectNode();
                errorNode.put("error", "Invalid JSON format");
                errorNode.put("data", new String(data)); // 원본 데이터를 문자열로 포함
                return errorNode;
                
                // return null; // JSON 형식이 아닌 경우 null 반환
            }
        }
    }
}