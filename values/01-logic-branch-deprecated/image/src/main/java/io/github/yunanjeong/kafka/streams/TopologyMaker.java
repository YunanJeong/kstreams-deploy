package io.github.yunanjeong.kafka.streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import io.github.yunanjeong.kafka.streams.serdes.JsonNodeSerde;


public class TopologyMaker {
    
    private static final Logger logger = LoggerFactory.getLogger(TopologyMaker.class);

    private String inputTopic = "source.topic";
    private String outputTopicA = "s3.topic";
    private String outputTopicB = "chat1.topic";
    private String outputTopicC = "chat2.topic";
    private StreamsBuilder streamsBuilder = new StreamsBuilder();
    private JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
    private ObjectMapper mapper = new ObjectMapper();
    
    public Topology getMyTopology() throws NoSuchAlgorithmException {

        // 로그 Extraction
        KStream<String, JsonNode> inputStream = streamsBuilder.stream(
            inputTopic,
            Consumed.with(Serdes.String(), jsonNodeSerde));
        logger.info("Streams Start");

        // 로그 분기(branch)
          // 2.8버전부터 deprecated 되었으나 개편된 함수보다 은근히 쓰기 편하다...
          // 람다함수에서 true면 남기고, false면 다음 branch로 넘긴다. (마지막에 false면 버림)
          // branch는 if-else, switch와 같이 서로 배타적이다.
            //동일한 Record가 중복으로 여러 branch로 가는 것이 아니다.
            //두번째 branch는 첫번째 branch에서 처리되고 남은 스트림을 처리한다.
        KStream<String, JsonNode>[] branches = inputStream
            .branch((key, jsonNode) -> isS3(jsonNode),
                    (key, jsonNode) -> isChat(jsonNode),
                    (key, jsonNode) -> false  //no default
            );
        
        // branch 기술 순서대로 인덱스가 부여되며, 호출하여 후속처리 가능하다.
        KStream<String, JsonNode> s3Stream = branches[0];
        KStream<String, JsonNode> chatStream = branches[1];
         processOutputStreamA(s3Stream);

        // 동일한 스트림(chatStream)에 대해서도 각각 처리 가능하다.
          // 이 때는 분기(branch)와 달리, 동일한 내용을 두 번 처리해서 각각 다른 결과값을 내도록하는 것이다.
        processOutputStreamB(chatStream);
        processOutputStreamC(chatStream);
        
        return streamsBuilder.build();
    }

    private boolean isS3(JsonNode jsonNode){
        // S3 업로드 대상인가 
        try{
            String action = jsonNode.get("action").asText();
            return !action.startsWith("chat.");
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }
    private boolean isChat(JsonNode jsonNode){
        // 분석 대상 로그인가 
        try{
            String action = jsonNode.get("action").asText();
            return (action.equals("chat.sendmessage"));
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    private void processOutputStreamA(KStream<String, JsonNode> s3Stream){
        logger.info("Stream A");
        if (s3Stream == null){
            return ;
        }
        KStream<String, JsonNode> outputStreamA = s3Stream;
        outputStreamA.to(outputTopicA, Produced.with(Serdes.String(), jsonNodeSerde));
    }

    private void processOutputStreamB(KStream<String, JsonNode> chatStream){
        logger.info("Stream B");
        if (chatStream == null){
            return ;
        }
        
        KStream<String, JsonNode> outputStreamB = chatStream.map((key, jsonNode) -> {   
            try {
                String time = jsonNode.get("time").asText();
                String dataStr = jsonNode.get("data").asText();  //asText().getBytes("UTF-8");
                JsonNode dataJson= mapper.readTree(dataStr);
                String uid = dataJson.get("uid").asText();
                String body = dataJson.get("arguments").get("message").get("Body").asText();

                Map<String, Object> map = new HashMap<>();
                map.put("user", uid);
                map.put("message", body);
                map.put("datetime", time);
                JsonNode resultValue = mapper.valueToTree(map);

                return KeyValue.pair(uid, resultValue);
            } catch (Exception e) {
                e.printStackTrace();
                return KeyValue.pair(null, jsonNode);
            }
        });
        outputStreamB.to(outputTopicB, Produced.with(Serdes.String(), jsonNodeSerde));
    }

    private void processOutputStreamC(KStream<String, JsonNode> chatStream){
        logger.info("Stream C");
        if (chatStream == null){
            return ;
        }
        KStream<String, JsonNode> outputStreamC = chatStream;
        outputStreamC.to(outputTopicC, Produced.with(Serdes.String(), jsonNodeSerde));  
    }

}
