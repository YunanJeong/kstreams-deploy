import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.yunanjeong.kafka.streams.TopologyConfig;
import io.github.yunanjeong.kafka.streams.serdes.JsonNodeSerde;
import io.github.yunanjeong.kafka.streams.topologies.TopologyRegistry;

/*
 * 각 토폴로지의 로직 테스트.
 * 설정값을 TopologyConfig로 주입받으므로 환경변수 stub 없이 테스트할 수 있다.
 */
public class TestTopologies {

    private static final String LOG_TYPE_FIELD = "log_type";
    private static final String INPUT_TOPIC = "test.topic";
    private static final String OUTPUT_TOPIC = "output.topic";
    private static final Duration WINDOW = Duration.ofHours(1);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static Properties testDriverProps() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        return props;
    }

    private static Topology topology(String name, Map<String, String> config) {
        return TopologyRegistry.find(name).orElseThrow().build(TopologyConfig.of(config));
    }

    private static JsonNode log(String logType) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put(LOG_TYPE_FIELD, logType);
        node.put("message", "some log line");
        return node;
    }

    // --- json-filter (stateless) ---

    private static Topology jsonFilterTopology() {
        return topology("json-filter", Map.of(
            "INPUT_TOPIC_REGEX", INPUT_TOPIC,
            "OUTPUT_TOPIC", OUTPUT_TOPIC
        ));
    }

    @Test
    @DisplayName("[json-filter] 역직렬화 실패 레코드는 걸러지고 나머지는 통과한다")
    public void jsonFilterPassesValidRecordsOnly() {

        JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
        Instant t0 = Instant.parse("2026-08-25T00:00:00Z");

        try (TopologyTestDriver driver = new TopologyTestDriver(jsonFilterTopology(), testDriverProps())) {

            TestInputTopic<String, JsonNode> input = driver.createInputTopic(
                INPUT_TOPIC, Serdes.String().serializer(), jsonNodeSerde.serializer());
            TestOutputTopic<String, JsonNode> output = driver.createOutputTopic(
                OUTPUT_TOPIC, Serdes.String().deserializer(), jsonNodeSerde.deserializer());

            ObjectNode broken = objectMapper.createObjectNode();
            broken.put("deserial_error", "not a json");

            input.pipeInput("k1", log("A"), t0);
            input.pipeInput("k2", broken, t0.plusSeconds(1));
            input.pipeInput("k3", log("B"), t0.plusSeconds(2));

            List<String> passed = output.readKeyValuesToList().stream().map(kv -> kv.key).toList();
            assertEquals(List.of("k1", "k3"), passed);
        }
    }

    // --- new-logtype-detect (stateful) ---

    private static Topology newLogTypeTopology() {
        return topology("new-logtype-detect", Map.of(
            "INPUT_TOPIC_REGEX", INPUT_TOPIC,
            "OUTPUT_TOPIC", OUTPUT_TOPIC,
            "LOG_TYPE_FIELD", LOG_TYPE_FIELD,
            "NEW_LOGTYPE_WINDOW", "PT1H"
        ));
    }

    @Test
    @DisplayName("[new-logtype-detect] 윈도우 내 최초 등장 로그타입만 신규로 검출된다")
    public void detectNewLogTypeOnlyOnce() {

        JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
        Instant t0 = Instant.parse("2026-08-25T00:00:00Z");

        try (TopologyTestDriver driver = new TopologyTestDriver(newLogTypeTopology(), testDriverProps())) {

            TestInputTopic<String, JsonNode> input = driver.createInputTopic(
                INPUT_TOPIC, Serdes.String().serializer(), jsonNodeSerde.serializer());
            TestOutputTopic<String, JsonNode> alerts = driver.createOutputTopic(
                OUTPUT_TOPIC, Serdes.String().deserializer(), jsonNodeSerde.deserializer());

            input.pipeInput("k", log("A"), t0);                                 // 최초 등장 -> 신규
            input.pipeInput("k", log("A"), t0.plus(Duration.ofMinutes(10)));    // 윈도우 내 재등장 -> 신규 아님
            input.pipeInput("k", log("B"), t0.plus(Duration.ofMinutes(20)));    // 최초 등장 -> 신규

            List<String> detected = alerts.readKeyValuesToList().stream().map(kv -> kv.key).toList();
            assertEquals(List.of("A", "B"), detected);
        }
    }

    @Test
    @DisplayName("[new-logtype-detect] 윈도우보다 오래 사라졌다가 재등장한 로그타입은 다시 신규로 검출된다")
    public void detectAgainAfterWindowExpired() {

        JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
        Instant t0 = Instant.parse("2026-08-25T00:00:00Z");

        try (TopologyTestDriver driver = new TopologyTestDriver(newLogTypeTopology(), testDriverProps())) {

            TestInputTopic<String, JsonNode> input = driver.createInputTopic(
                INPUT_TOPIC, Serdes.String().serializer(), jsonNodeSerde.serializer());
            TestOutputTopic<String, JsonNode> alerts = driver.createOutputTopic(
                OUTPUT_TOPIC, Serdes.String().deserializer(), jsonNodeSerde.deserializer());

            input.pipeInput("k", log("A"), t0);
            input.pipeInput("k", log("A"), t0.plus(WINDOW).plus(Duration.ofMinutes(1)));

            List<String> detected = alerts.readKeyValuesToList().stream().map(kv -> kv.key).toList();
            assertEquals(List.of("A", "A"), detected);
        }
    }

    @Test
    @DisplayName("[new-logtype-detect] 로그타입 필드가 없는 레코드는 검사 대상에서 제외된다")
    public void ignoreRecordWithoutLogTypeField() {

        JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
        Instant t0 = Instant.parse("2026-08-25T00:00:00Z");

        try (TopologyTestDriver driver = new TopologyTestDriver(newLogTypeTopology(), testDriverProps())) {

            TestInputTopic<String, JsonNode> input = driver.createInputTopic(
                INPUT_TOPIC, Serdes.String().serializer(), jsonNodeSerde.serializer());
            TestOutputTopic<String, JsonNode> alerts = driver.createOutputTopic(
                OUTPUT_TOPIC, Serdes.String().deserializer(), jsonNodeSerde.deserializer());

            ObjectNode noLogType = objectMapper.createObjectNode();
            noLogType.put("message", "some log line");
            input.pipeInput("k", noLogType, t0);

            assertTrue(alerts.isEmpty());
        }
    }

    @Test
    @DisplayName("[new-logtype-detect] 검출 결과 메시지에 로그타입과 검출 시각이 담긴다")
    public void alertPayloadContainsLogTypeAndTime() {

        JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
        Instant t0 = Instant.parse("2026-08-25T00:00:00Z");

        try (TopologyTestDriver driver = new TopologyTestDriver(newLogTypeTopology(), testDriverProps())) {

            TestInputTopic<String, JsonNode> input = driver.createInputTopic(
                INPUT_TOPIC, Serdes.String().serializer(), jsonNodeSerde.serializer());
            TestOutputTopic<String, JsonNode> alerts = driver.createOutputTopic(
                OUTPUT_TOPIC, Serdes.String().deserializer(), jsonNodeSerde.deserializer());

            input.pipeInput("k", log("A"), t0);

            JsonNode alert = alerts.readValue();
            assertEquals("new_log_type", alert.get("event").asText());
            assertEquals("A", alert.get(LOG_TYPE_FIELD).asText());
            assertEquals(t0.toEpochMilli(), alert.get("detected_at").asLong());
            assertEquals("PT1H", alert.get("window").asText());
            assertTrue(alert.get("previous_seen_at").isNull());
        }
    }
}
