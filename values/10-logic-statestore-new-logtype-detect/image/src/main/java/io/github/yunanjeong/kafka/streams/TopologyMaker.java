package io.github.yunanjeong.kafka.streams;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.processor.TopicNameExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.yunanjeong.kafka.streams.processors.NewLogTypeDetector;
import io.github.yunanjeong.kafka.streams.serdes.FilebeatJsonDes;
import io.github.yunanjeong.kafka.streams.serdes.JsonNodeSerde;

import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;


public class TopologyMaker { // extends Security
    
    private static final Logger LOG = LoggerFactory.getLogger(TopologyMaker.class);
    private static final ObjectMapper objectMapper = 
        new ObjectMapper()
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true); // JSON 파싱 시 큰 숫자의 정밀도 유지 (BigDecimal)

    // private static final String INPUT_TOPIC = System.getenv("INPUT_TOPIC");
    private static final Pattern INPUT_TOPIC_REGEX = Pattern.compile(System.getenv("INPUT_TOPIC_REGEX"));

    // 신규 로그타입 검사용 설정 (환경변수 주입)
    // LOG_TYPE_FIELD     : 로그타입 값이 들어있는 JSON 필드의 key 이름 (e.g. "log_type")
    // NEW_LOGTYPE_WINDOW : "최근 특정 시간" 구간의 크기, ISO-8601 Duration 표기 (e.g. "PT30M", "PT1H", "P1D")
    private static final String LOG_TYPE_FIELD = System.getenv("LOG_TYPE_FIELD");
    private static final Duration NEW_LOGTYPE_WINDOW = Duration.parse(System.getenv("NEW_LOGTYPE_WINDOW"));

    // 알림 토픽. 역직렬화 실패 레코드와 신규 로그타입 검출 결과가 같이 모이는 곳이다.
    private static final String ALERT_TOPIC = System.getenv("ALERT_TOPIC");

    private StreamsBuilder streamsBuilder = new StreamsBuilder();
    private JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
    private FilebeatJsonDes filebeatJsonDes = new FilebeatJsonDes();

    public Topology getMyTopology() throws NoSuchAlgorithmException {
        LOG.info("Getting topology ...");
        KStream<String, JsonNode> inputStream = streamsBuilder.stream(
            INPUT_TOPIC_REGEX,
            Consumed.with(Serdes.String(), jsonNodeSerde)
        );
        
        // Json 검증
        KStream<String, JsonNode> validStream = inputStream.filter(
            (key, value) -> value.get("deserial_error") == null
        );

        // 비즈니스 데이터 필터링
        KStream<String,JsonNode> bizStream = validStream.filter(
            (key, value) -> isBiz(value) 
        );

        bizStream.to("output.topic", Produced.with(Serdes.String(), jsonNodeSerde));

        // 최근 NEW_LOGTYPE_WINDOW 구간 안에 새로 등장한 로그타입 검출 (상태저장소는 supplier가 함께 제공)
        KStream<String, JsonNode> newLogTypeStream = bizStream.process(
            NewLogTypeDetector.supplier(LOG_TYPE_FIELD, NEW_LOGTYPE_WINDOW)
        );

        newLogTypeStream.to(ALERT_TOPIC, Produced.with(Serdes.String(), jsonNodeSerde));

        return streamsBuilder.build();
    }

    // 비즈니스 데이터 처리 로직
    private boolean isBiz(JsonNode value){
        if (value == null) return false;
        return true;
    }


}
