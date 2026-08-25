package io.github.yunanjeong.kafka.streams.topologies.jsonfilter;

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
import io.github.yunanjeong.kafka.streams.topologies.TopologyProvider;

/**
 * stateless 예시: 역직렬화에 실패한 레코드를 걸러 출력 토픽으로 흘려보낸다.
 *
 * 사용 환경변수
 *   INPUT_TOPIC_REGEX : 입력 토픽 패턴
 *   OUTPUT_TOPIC      : 출력 토픽
 */
public class JsonFilterTopology implements TopologyProvider {

    private static final Logger LOG = LoggerFactory.getLogger(JsonFilterTopology.class);

    @Override
    public String name() {
        return "json-filter";
    }

    @Override
    public String description() {
        return "stateless: 유효한 JSON 레코드만 출력 토픽으로 통과";
    }

    @Override
    public Topology build(TopologyConfig config) {

        Pattern inputTopicRegex = Pattern.compile(config.require("INPUT_TOPIC_REGEX"));
        String outputTopic = config.require("OUTPUT_TOPIC");

        LOG.info("Building topology [{}]: {} -> {}", name(), inputTopicRegex, outputTopic);

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

        validStream.to(outputTopic, Produced.with(Serdes.String(), jsonNodeSerde));

        return streamsBuilder.build();
    }
}
