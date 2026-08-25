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
    
    private static final String INPUT_TOPIC = "source.topic";
    private static final String OUTPUT_TOPIC_A = "s3.topic";
    private static final String OUTPUT_TOPIC_B = "chat1.topic";
    private static final String OUTPUT_TOPIC_C = "chat2.topic";
    private StreamsBuilder streamsBuilder = new StreamsBuilder();
    private JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
    private ObjectMapper mapper = new ObjectMapper();
    
    public Topology getMyTopology() throws NoSuchAlgorithmException {
        logger.info("Streams Start");
        KStream<String, JsonNode> inputStream = streamsBuilder.stream(
            INPUT_TOPIC,
            Consumed.with(Serdes.String(), jsonNodeSerde));
        
        Map<String, KStream<String,JsonNode>> branches = inputStream.split()
            .branch((key, jsonNode) -> isS3(jsonNode),
                Branched.withFunction(s3stream -> processOutputStreamA(s3stream), "s3"))
            .branch((key, jsonNode) -> isChat(jsonNode),
                Branched.withFunction(chatStream -> processChat(chatStream),"chat"))
            .noDefaultBranch();     //나머지 버림 (chat.sendmessage 외 chat인 것들)
        
        // 분기된 스트림(branch)의 후속처리
          // 위와 같이 withFunction으로 처리한다.
          // 이슈: 아래처럼 branch를 받아서 후속처리가 안됨.(deprecated branch에선 가능했는데)
          // KStream<String, JsonNode> s3Stream = branches.get("s3");
          // s3Streams.filter(...)

        return streamsBuilder.build();
    }

    private boolean isS3(JsonNode jsonNode){
        // S3 업로드 대상인가 (chat.으로 시작하지 않는 것들)
        try{
            String action = jsonNode.get("action").asText();
            return !action.startsWith("chat.");
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }
    private boolean isChat(JsonNode jsonNode){
        // 분석 대상 채팅로그인가 (chat.sendmessage)
        try{
            String action = jsonNode.get("action").asText();
            return (action.equals("chat.sendmessage"));
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    private KStream<String, JsonNode> processOutputStreamA(KStream<String, JsonNode> s3Stream){
        logger.info("Processing OutputStream 1 ... ");
        if (s3Stream == null){
            return null;
        }
        s3Stream.to(OUTPUT_TOPIC_A, Produced.with(Serdes.String(), jsonNodeSerde));
        return s3Stream; //원본을 그대로 return 해줘야 branch가 정상작동
    }
    private KStream<String, JsonNode> processChat(KStream<String, JsonNode> chatStream){
        if (chatStream == null){
            return null;
        }
        processOutputStreamB(chatStream);
        processOutputStreamC(chatStream);
        return chatStream; //원본을 그대로 return 해줘야 branch가 정상작동
    }

    private void processOutputStreamB(KStream<String, JsonNode> chatStream){
        logger.info("Processing Output Stream 2 ... ");
        KStream<String, JsonNode> outputStream = chatStream.map((key, jsonNode) -> {   
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
        outputStream.to(OUTPUT_TOPIC_B, Produced.with(Serdes.String(), jsonNodeSerde));
    }

    private void processOutputStreamC(KStream<String, JsonNode> chatStream){
        logger.info("Processing Output Stream 3 ... ");

        KStream<String, JsonNode> outputStream = chatStream;
        outputStream.to(OUTPUT_TOPIC_C, Produced.with(Serdes.String(), jsonNodeSerde));
    }

}
