package io.github.yunanjeong.kafka.streams;
import io.github.yunanjeong.kafka.streams.serdes.JsonNodeSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
// import java.util.regex.Pattern;
// import java.util.HashMap;
// import java.util.Map;
import java.util.Map;
import java.util.regex.Pattern;


public class TopologyMaker { // extends Security
    
    private static final Logger logger = LoggerFactory.getLogger(TopologyMaker.class);

    // 토픽명 환경변수는 helm value파일에서 관리
    private static final Pattern INPUT_TOPIC_REGEX = Pattern.compile(System.getenv("INPUT_TOPIC_REGEX"));  
    // private static final String INPUT_TOPIC = System.getenv("INPUT_TOPIC"); 
    private static final String OUTPUT_TOPIC_S3 = System.getenv("OUTPUT_TOPIC_S3");
    private static final String ERROR_TOPIC = System.getenv("ERROR_TOPIC");    
    
    private StreamsBuilder streamsBuilder = new StreamsBuilder();
    private JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
    private ObjectMapper objectMapper = new ObjectMapper();
    
    public Topology getMyTopology() throws NoSuchAlgorithmException {
        logger.info("Streams Start");
        KStream<String, JsonNode> inputStream = streamsBuilder.stream(
            INPUT_TOPIC_REGEX,
            Consumed.with(Serdes.String(), jsonNodeSerde)
        );

        // 원본데이터의 json Deserializa에 실패하여 error 필드가 포함된 메시지는 에러토픽으로 전송 후 제거(consume)
        Map<String, KStream<String, JsonNode>> inputBranches = inputStream.split(Named.as("input-"))
            .branch((key, value) -> value.get("error") == null, 
                Branched.as("valid"))
            .defaultBranch(
                Branched.withConsumer(stream -> stream.to(ERROR_TOPIC, Produced.with(Serdes.String(), jsonNodeSerde)), "invalid")
            );
        
        // filebeat 랩핑 메시지 중 message 필드만 추출  // message 필드 내부도 json 검증이 필요
        System.err.println("inputBranches Keys>>>>: " + inputBranches.keySet()   );
        System.err.println("inputBranches size>>>>: " + inputBranches.size()   );

        System.err.println("check validStream>>>>: " + inputBranches.get("input-valid"));
        KStream<String, JsonNode> validStream = inputBranches.get("input-valid");
        KStream<String, String> msgStream = validStream.mapValues(
            value -> value.get("message").asText()
        );
        
        // message가 json이면 S3용 토픽으로, 아니면 에러토픽으로 전송 후 제거(consume)
        Map<String, KStream<String,String>> msgBranches = msgStream.split(Named.as("msg-"))
            .branch((key, value) -> isJson(value),
                Branched.withConsumer(stream -> stream.to(OUTPUT_TOPIC_S3, Produced.with(Serdes.String(), Serdes.String())), "s3"))
            .defaultBranch(
                Branched.withConsumer(stream -> stream.to(ERROR_TOPIC, Produced.with(Serdes.String(), Serdes.String())), "error")
            );

        // 분기된 스트림(Branch)의 후속처리함수 // 각 분기 처리로직에 따라 다음 중 편한 걸 쓰면 된다.
        // withConsumer: 조건식에 맞는 스트림을 로직대로 처리하고, 소비(제거)함. return값 없음. 다음 분기에선 이후 처리할 나머지 스트림만 자동으로 남는다. return 없으므로 branches.get() 참조도 불가.  여기서 Consume은 Kafka Consumer를 의미하는게 아님.
        // withFunction: 조건식에 맞는 스트림을 로직대로 처리하고, 다음 분기로 넘겨줄 결과물 스트림을 명시적으로 return 해야 함.
        // https://kafka.apache.org/28/javadoc/org/apache/kafka/streams/kstream/BranchedKStream.html
        // branches map 접근키는 prefix가 강제됨. 빈 값 불가. 미설정시 default prefix 적용됨
        

        return streamsBuilder.build();
    }

    private boolean isJson(String content) {
        try {
            objectMapper.readTree(content);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }


    // # filebeat 랩핑 메시지 필드 명세
    // # # fields: filebeat 커스텀설정으로 남긴것
    // # # log: filebeat가 수집한 파일의 경로와 오프셋
    // # # message: 원본파일로그 내용 (json검증 및 에러 라우팅 필요)

}
