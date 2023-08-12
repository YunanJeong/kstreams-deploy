package io.github.yunanjeong.kafka.streams.examples.serdes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class JsonNodeSerde implements Serde<JsonNode> {

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
        
        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public byte[] serialize(String topic, JsonNode data) {
            try {
                return objectMapper.writeValueAsBytes(data);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class JsonDeserializer implements Deserializer<JsonNode> {
        
        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public JsonNode deserialize(String topic, byte[] data) {
            try {
                return objectMapper.readTree(data);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}