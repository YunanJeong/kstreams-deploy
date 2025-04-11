package io.github.yunanjeong.kafka.streams;
import io.github.yunanjeong.kafka.streams.serdes.JsonNodeSerde;
import io.github.yunanjeong.kafka.streams.serdes.FilebeatJsonDes;
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
// import java.util.regex.Pattern;
// import java.util.HashMap;
// import java.util.Map;
import java.util.Map;
import java.util.regex.Pattern;


public class TopologyMaker { // extends Security
    
    private static final Logger LOG = LoggerFactory.getLogger(TopologyMaker.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // 토픽명 환경변수는 helm value파일에서 관리
    private static final Pattern INPUT_TOPIC_REGEX = Pattern.compile(System.getenv("INPUT_TOPIC_REGEX"));  
    // private static final String INPUT_TOPIC = System.getenv("INPUT_TOPIC"); 
    private static final String OUTPUT_TOPIC_S3 = System.getenv("OUTPUT_TOPIC_S3");
    private static final String ERROR_TOPIC = System.getenv("ERROR_TOPIC");    
    
    private StreamsBuilder streamsBuilder = new StreamsBuilder();
    private JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
    private FilebeatJsonDes filebeatJsonDes = new FilebeatJsonDes();

    public Topology getFilebeatTopology() throws NoSuchAlgorithmException {
        
        // 이 로그는 스트림즈 실행시 최초 1회만 실행됨  
        // 여기는 스트림 처리 방법을 정의하는 틀(Topology)을 만드는 곳이기 때문
        // Record마다 특정 로직(로깅)을 처리하려면 KStream 객체의 메소드 peek()를 사용해야 함 (Record 마다 적용되므로 부하 주의!!)
        LOG.info("Getting Topology ... ");

        KStream<String, JsonNode> inputStream = streamsBuilder.stream(
            INPUT_TOPIC_REGEX,
            Consumed.with(Serdes.String(), filebeatJsonDes)
        );

        // 메시지 포맷에 문제없을시 S3용 토픽으로, 아니면 에러토픽으로 전송 후 제거(consume)
        Map<String, KStream<String,JsonNode>> branches = inputStream.split(Named.as("msg-"))
            .branch((key, value) -> value.get("error") == null,
                Branched.withConsumer(stream -> stream.to(OUTPUT_TOPIC_S3, Produced.with(Serdes.String(), jsonNodeSerde)), "s3"))
            .defaultBranch(
                Branched.withConsumer(stream -> { 
                    // peek()는 데이터 스트림의 변경없이, Record마다 특정로직(로깅) 수행이 필요할 시 사용  // peek없이 그냥 로그찍으면 작동안함
                    stream.peek((key, value) -> LOG.warn("Sending invalid record to the topic={} ... key={}, value={}" , ERROR_TOPIC, key, value ) )
                          .to(ERROR_TOPIC, Produced.with(Serdes.String(), jsonNodeSerde));
                }, "error")
            );

        return streamsBuilder.build();
    }

    public Topology getMyTopology() throws NoSuchAlgorithmException {
        LOG.info("Getting Topology ... ");
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
        KStream<String, JsonNode> validStream = inputBranches.get("input-valid");
        KStream<String, String> msgStream = validStream.mapValues(
            value -> value.get("message").asText()
        );
        
        // message가 json이면 S3용 토픽으로, 아니면 에러토픽으로 전송 후 제거(consume)
        Map<String, KStream<String,String>> msgBranches = msgStream.split(Named.as("msg-"))
            .branch((key, value) -> isValidJsonString(value),
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

    private boolean isValidJsonString(String content) {
        try {
            JsonNode node = objectMapper.readTree(content);
            // 단일 문자열, 숫자, boolean, null 등은 Json이 아니지만, JsonNode로 취급되므로 다음 로직을 통해 제외시켜준다.
            // isObject()는 JsonNode가 ObjectNode({})인지 확인 // isArray()는 JsonNode가 ArrayNode([])인지 확인
            return node.isObject() || node.isArray();
        } catch (Exception e) {
            LOG.warn("Json 검증 실패", e);
            return false;
        }
    }

}
