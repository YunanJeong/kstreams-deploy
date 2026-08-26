import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.yunanjeong.kafka.streams.TopologyMaker;
import io.github.yunanjeong.kafka.streams.serdes.JsonNodeSerde;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

/* 최근 특정 시간(윈도우) 동안 신규 로그타입이 추가됐는지 검사하는 로직 테스트 */
// TopologyMaker의 설정값은 static 필드로 환경변수를 읽으므로, 클래스 최초 로딩 전에 stub이 적용돼야 함
@ExtendWith(SystemStubsExtension.class)
public class TestTopologyMaker {

    private static final String LOG_TYPE_FIELD = "log_type";
    private static final String INPUT_TOPIC = "test.topic";
    private static final String ALERT_TOPIC = "alert.topic";
    private static final Duration WINDOW = Duration.ofHours(1);

    @SystemStub
    private EnvironmentVariables env =
        new EnvironmentVariables(
            "INPUT_TOPIC_REGEX", INPUT_TOPIC,
            "ALERT_TOPIC", ALERT_TOPIC,
            "LOG_TYPE_FIELD", LOG_TYPE_FIELD,
            "NEW_LOGTYPE_WINDOW", "PT1H"
        );

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static Properties testDriverProps() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        return props;
    }

    private static JsonNode log(String logType) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put(LOG_TYPE_FIELD, logType);
        node.put("message", "some log line");
        return node;
    }

    @Test
    @DisplayName("윈도우 내 최초 등장 로그타입만 신규로 검출된다")
    public void detectNewLogTypeOnlyOnce() throws Exception {

        JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
        Instant t0 = Instant.parse("2026-08-25T00:00:00Z");

        try (TopologyTestDriver driver =
                 new TopologyTestDriver(new TopologyMaker().getMyTopology(), testDriverProps())) {

            TestInputTopic<String, JsonNode> input = driver.createInputTopic(
                INPUT_TOPIC, Serdes.String().serializer(), jsonNodeSerde.serializer());
            TestOutputTopic<String, JsonNode> alerts = driver.createOutputTopic(
                ALERT_TOPIC, Serdes.String().deserializer(), jsonNodeSerde.deserializer());

            input.pipeInput("k", log("A"), t0);                                 // 최초 등장 -> 신규
            input.pipeInput("k", log("A"), t0.plus(Duration.ofMinutes(10)));    // 윈도우 내 재등장 -> 신규 아님
            input.pipeInput("k", log("B"), t0.plus(Duration.ofMinutes(20)));    // 최초 등장 -> 신규

            List<String> detected = alerts.readKeyValuesToList().stream().map(kv -> kv.key).toList();
            assertEquals(List.of("A", "B"), detected);
        }
    }

    @Test
    @DisplayName("윈도우보다 오래 사라졌다가 재등장한 로그타입은 다시 신규로 검출된다")
    public void detectAgainAfterWindowExpired() throws Exception {

        JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
        Instant t0 = Instant.parse("2026-08-25T00:00:00Z");

        try (TopologyTestDriver driver =
                 new TopologyTestDriver(new TopologyMaker().getMyTopology(), testDriverProps())) {

            TestInputTopic<String, JsonNode> input = driver.createInputTopic(
                INPUT_TOPIC, Serdes.String().serializer(), jsonNodeSerde.serializer());
            TestOutputTopic<String, JsonNode> alerts = driver.createOutputTopic(
                ALERT_TOPIC, Serdes.String().deserializer(), jsonNodeSerde.deserializer());

            input.pipeInput("k", log("A"), t0);
            input.pipeInput("k", log("A"), t0.plus(WINDOW).plus(Duration.ofMinutes(1)));

            List<String> detected = alerts.readKeyValuesToList().stream().map(kv -> kv.key).toList();
            assertEquals(List.of("A", "A"), detected);
        }
    }

    @Test
    @DisplayName("로그타입 필드가 없는 레코드는 검사 대상에서 제외된다")
    public void ignoreRecordWithoutLogTypeField() throws Exception {

        JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
        Instant t0 = Instant.parse("2026-08-25T00:00:00Z");

        try (TopologyTestDriver driver =
                 new TopologyTestDriver(new TopologyMaker().getMyTopology(), testDriverProps())) {

            TestInputTopic<String, JsonNode> input = driver.createInputTopic(
                INPUT_TOPIC, Serdes.String().serializer(), jsonNodeSerde.serializer());
            TestOutputTopic<String, JsonNode> alerts = driver.createOutputTopic(
                ALERT_TOPIC, Serdes.String().deserializer(), jsonNodeSerde.deserializer());

            ObjectNode noLogType = objectMapper.createObjectNode();
            noLogType.put("message", "some log line");
            input.pipeInput("k", noLogType, t0);

            assertTrue(alerts.isEmpty());
        }
    }

    @Test
    @DisplayName("검출 결과 메시지는 serde의 역직렬화 실패 레코드와 같은 뼈대로 나간다")
    public void alertPayloadContainsLogTypeAndTime() throws Exception {

        JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();
        Instant t0 = Instant.parse("2026-08-25T00:00:00Z");

        try (TopologyTestDriver driver =
                 new TopologyTestDriver(new TopologyMaker().getMyTopology(), testDriverProps())) {

            TestInputTopic<String, JsonNode> input = driver.createInputTopic(
                INPUT_TOPIC, Serdes.String().serializer(), jsonNodeSerde.serializer());
            TestOutputTopic<String, JsonNode> alerts = driver.createOutputTopic(
                ALERT_TOPIC, Serdes.String().deserializer(), jsonNodeSerde.deserializer());

            input.pipeInput("k", log("A"), t0);

            JsonNode alert = alerts.readValue();

            // 마커 필드 — 알림 토픽 소비자가 이걸로 종류를 가른다 (serde는 deserial_error)
            assertEquals("New log type detected", alert.get("new_logtype_alert").asText());
            assertEquals("A", alert.get(LOG_TYPE_FIELD).asText());
            assertEquals("PT1H", alert.get("window").asText());
            assertTrue(alert.get("previous_seen_at").isNull());   // 최초 등장

            // detected_at은 서버 시각이라 값을 고정할 수 없다. RFC3339로 파싱되는지만 확인
            assertNotNull(OffsetDateTime.parse(alert.get("detected_at").asText()));

            // record_timestamp는 레코드 메타데이터의 시각이라 결정적이다 (pipeInput에 넘긴 값)
            assertEquals(t0, OffsetDateTime.parse(alert.get("record_timestamp").asText()).toInstant());

            // data는 원본 레코드 (serde의 data와 같은 자리)
            assertEquals("A", objectMapper.readTree(alert.get("data").asText()).get(LOG_TYPE_FIELD).asText());
        }
    }
}
