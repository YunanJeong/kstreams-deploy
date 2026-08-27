import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
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

import io.github.yunanjeong.kafka.streams.App;
import io.github.yunanjeong.kafka.streams.TopologyConfig;
import io.github.yunanjeong.kafka.streams.serdes.JsonNodeSerde;

/*
 * 각 토폴로지의 로직 테스트.
 * 설정값을 TopologyConfig로 주입받으므로 환경변수 stub 없이 테스트할 수 있다.
 * App.buildTopology를 통과하므로 TOPOLOGY 선택 switch도 함께 검증된다.
 */
public class TestTopologies {

    private static final String LOG_TYPE_FIELD = "log_type";
    private static final String INPUT_TOPIC = "test.topic";
    private static final String OUTPUT_TOPIC = "output.topic";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static Properties testDriverProps() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        return props;
    }

    private static Topology topology(String name, Map<String, String> config) {
        return App.buildTopology(name, TopologyConfig.of(config));
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
            "LOG_TYPE_FIELD", LOG_TYPE_FIELD
        ));
    }

    @Test
    @DisplayName("[new-logtype-detect] 처음 등장한 로그타입만 신규로 검출된다")
    public void detectNewLogTypeOnlyOnce() {

        JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
        Instant t0 = Instant.parse("2026-08-25T00:00:00Z");

        try (TopologyTestDriver driver = new TopologyTestDriver(newLogTypeTopology(), testDriverProps())) {

            TestInputTopic<String, JsonNode> input = driver.createInputTopic(
                INPUT_TOPIC, Serdes.String().serializer(), jsonNodeSerde.serializer());
            TestOutputTopic<String, JsonNode> alerts = driver.createOutputTopic(
                OUTPUT_TOPIC, Serdes.String().deserializer(), jsonNodeSerde.deserializer());

            input.pipeInput("k", log("A"), t0);                                 // 최초 등장 -> 신규
            input.pipeInput("k", log("A"), t0.plus(Duration.ofMinutes(10)));    // 이미 등록됨 -> 신규 아님
            input.pipeInput("k", log("B"), t0.plus(Duration.ofMinutes(20)));    // 최초 등장 -> 신규

            List<String> detected = alerts.readKeyValuesToList().stream().map(kv -> kv.key).toList();
            assertEquals(List.of("A", "B"), detected);
        }
    }

    @Test
    @DisplayName("[new-logtype-detect] 한번 등장한 로그타입은 아무리 오래 뒤에 재등장해도 신규로 검출되지 않는다")
    public void doNotDetectAgainAfterLongGap() {

        JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
        Instant t0 = Instant.parse("2026-08-25T00:00:00Z");

        try (TopologyTestDriver driver = new TopologyTestDriver(newLogTypeTopology(), testDriverProps())) {

            TestInputTopic<String, JsonNode> input = driver.createInputTopic(
                INPUT_TOPIC, Serdes.String().serializer(), jsonNodeSerde.serializer());
            TestOutputTopic<String, JsonNode> alerts = driver.createOutputTopic(
                OUTPUT_TOPIC, Serdes.String().deserializer(), jsonNodeSerde.deserializer());

            input.pipeInput("k", log("A"), t0);
            input.pipeInput("k", log("A"), t0.plus(Duration.ofDays(90)));

            List<String> detected = alerts.readKeyValuesToList().stream().map(kv -> kv.key).toList();
            assertEquals(List.of("A"), detected);
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
    @DisplayName("[new-logtype-detect] 검출 결과 메시지에 마커 필드, 로그타입, 두 종류의 시각, 원본이 담긴다")
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

            // 마커 필드 — 출력 토픽 소비자가 이걸로 종류를 가른다
            assertEquals("New log type detected", alert.get("new_logtype_alert").asText());
            assertEquals("A", alert.get(LOG_TYPE_FIELD).asText());

            // detected_at은 서버 시각이라 값을 고정할 수 없다. RFC3339로 파싱되는지만 확인
            assertNotNull(OffsetDateTime.parse(alert.get("detected_at").asText()));

            // record_timestamp는 레코드 메타데이터의 시각이라 결정적이다 (pipeInput에 넘긴 값)
            assertEquals(t0, OffsetDateTime.parse(alert.get("record_timestamp").asText()).toInstant());

            // data는 원본 레코드
            assertTrue(alert.get("data").asText().contains(LOG_TYPE_FIELD));
        }
    }

    // --- TOPOLOGY 선택 ---

    private static final Map<String, String> ONLY_COMMON = Map.of(
        "INPUT_TOPIC_REGEX", INPUT_TOPIC,
        "OUTPUT_TOPIC", OUTPUT_TOPIC
    );

    @Test
    @DisplayName("모르는 TOPOLOGY 값은 후보 목록과 함께 실패한다")
    public void unknownTopologyFails() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> topology("no-such-topology", ONLY_COMMON));

        assertTrue(e.getMessage().contains("no-such-topology"), e.getMessage());
        assertTrue(e.getMessage().contains("json-filter"), e.getMessage());
        assertTrue(e.getMessage().contains("new-logtype-detect"), e.getMessage());
    }

    /*
     * 단일 이미지 패턴의 핵심 요건.
     * 고른 것만 build되므로, 선택되지 않은 스트림 처리의 환경변수는 없어도 된다.
     * (설정값을 static 필드에서 읽으면 여기서 깨진다)
     */
    @Test
    @DisplayName("선택한 처리의 환경변수만 있으면 나머지가 없어도 빌드된다")
    public void unselectedTopologyConfigIsNotRequired() {
        assertNotNull(topology("json-filter", ONLY_COMMON));
    }

    @Test
    @DisplayName("선택한 처리의 필수 환경변수가 없으면 빌드 시점에 실패한다")
    public void missingRequiredConfigFails() {
        // new-logtype-detect는 LOG_TYPE_FIELD가 더 필요하다
        assertThrows(IllegalArgumentException.class,
            () -> topology("new-logtype-detect", ONLY_COMMON));
    }
}
