package io.github.yunanjeong.kafka.streams.newlogtype;

import java.util.regex.Pattern;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yunanjeong.kafka.streams.TopologyConfig;
import io.github.yunanjeong.kafka.streams.serdes.JsonNodeSerde;

/**
 * stateful 예시: 처음 등장한 로그타입을 검출해 출력 토픽으로 내보낸다.
 * (상태저장소 = 로컬 RocksDB + changelog 토픽)
 *
 * 검출 로직은 같은 패키지의 NewLogTypeDetector에 있다. 이 스트림 처리 전용 헬퍼이며,
 * 패키지 밖으로 공개하지 않는다.
 *
 * 사용 환경변수
 *   INPUT_TOPIC_REGEX : 입력 토픽 패턴
 *   OUTPUT_TOPIC      : 검출 결과를 내보낼 토픽
 *   LOG_TYPE_FIELD    : 로그타입 값이 들어있는 JSON 필드의 key 이름 (e.g. "log_type")
 */
public final class NewLogTypeTopology {

    private static final Logger LOG = LoggerFactory.getLogger(NewLogTypeTopology.class);

    private NewLogTypeTopology() {
    }

    public static Topology build(TopologyConfig config) {

        Pattern inputTopicRegex = Pattern.compile(config.require("INPUT_TOPIC_REGEX"));
        String outputTopic = config.require("OUTPUT_TOPIC");
        String logTypeField = config.require("LOG_TYPE_FIELD");

        LOG.info("Building topology: {} -> {} (field={})",
            inputTopicRegex, outputTopic, logTypeField);

        StreamsBuilder streamsBuilder = new StreamsBuilder();
        JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();

        KStream<String, JsonNode> inputStream = streamsBuilder.stream(
            inputTopicRegex,
            Consumed.with(Serdes.String(), jsonNodeSerde)
        );

        // Json 검증
        KStream<String, JsonNode> validStream = inputStream.filter(
            (key, value) -> value != null && value.get("deserial_error") == null
        );

        // 처음 등장한 로그타입 검출 (상태저장소는 supplier가 함께 제공)
        KStream<String, JsonNode> newLogTypeStream = validStream.process(
            NewLogTypeDetector.supplier(logTypeField)
        );

        newLogTypeStream.to(outputTopic, Produced.with(Serdes.String(), jsonNodeSerde));

        return streamsBuilder.build();
    }
}
