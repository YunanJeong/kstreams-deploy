package io.github.yunanjeong.kafka.streams.examples;

import io.github.yunanjeong.kafka.streams.examples.serdes.JsonNodeSerde;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;




public class TopologyMaker {
    
    private static final String mSrcTopic = "original_topicname";
    private static final String mSinkTopic = "output_topicname";
    StreamsBuilder streamsBuilder = new StreamsBuilder();
    JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
    ObjectMapper mapper = new ObjectMapper();

    public Topology getMyTopology() throws NoSuchAlgorithmException {

        // 입력 토픽을 Key는 String, Value는 JsonNode 형식으로 읽는다.
        // Serdes는 해당 용도를 위한 Kafka 제공 Object
        // JsonNodeSerde는 사용자정의 Object지만, KStreams에서 Serdes를 활용한 일반적인 방법이다.
        var inputStream = streamsBuilder.stream(
            mSrcTopic, Consumed.with(Serdes.String(), jsonNodeSerde));
        
        // 채팅 로그만 Extract
        KStream<String, JsonNode> filterStream = inputStream.filter((key, jsonNode) -> {
            try {
                String table = jsonNode.get("table").asText();
                return (table.equals("chat_message"));  //true가 아니면 버림
            } catch (Exception e) {
                return false;
            }
        });

        // 로그 Transform
        KStream<JsonNode, JsonNode> outputStream = filterStream.map((key, jsonNode) -> {   
            try {
                String time = jsonNode.get("time").asText();
                String dataStr = jsonNode.get("data").asText();  //asText().getBytes("UTF-8");
                JsonNode dataJson= mapper.readTree(dataStr);
                String uid = dataJson.get("uid").asText();
                String body = dataJson.get("arguments").get("message").get("Body").asText();          
                
                Map<String, Object> map = new HashMap<>();
                map.put("uid", uid);
                JsonNode resultKey = mapper.valueToTree(map);
                map.put("message", body);
                map.put("datetime", time);
                JsonNode resultValue = mapper.valueToTree(map);

                return KeyValue.pair(resultKey, resultValue);
            } catch (Exception e) {
                e.printStackTrace();
                return KeyValue.pair(null, jsonNode);
            }
        });
        
        outputStream.to(mSinkTopic, Produced.with(jsonNodeSerde, jsonNodeSerde));
        return streamsBuilder.build();
    }

}
