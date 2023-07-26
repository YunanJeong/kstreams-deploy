import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Properties;
import java.security.NoSuchAlgorithmException;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.haje01.kafka.streams.examples.HashSplitTopology;
import com.github.haje01.kafka.streams.examples.serdes.JsonNodeSerde;


public class HashSplitTest {

    private static String SOURCE_TOPIC = "source";
    private static String SINK_TOPIC_A = "sinkA";
    private static String SINK_TOPIC_B = "sinkB";

    private HashSplitTopology hashSplitTopology;
    private TopologyTestDriver testDriver;
    private TestInputTopic<String, JsonNode> sourceTopic;
    private TestOutputTopic<String, JsonNode> sinkTopicA;
    private TestOutputTopic<String, JsonNode> sinkTopicB;
    private JsonNodeSerde jsonNodeSerde;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        // 설정
        var props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        // 토폴로지
        hashSplitTopology = new HashSplitTopology(SOURCE_TOPIC, SINK_TOPIC_A, SINK_TOPIC_B);
        var topology = hashSplitTopology.createTopology();
        testDriver = new TopologyTestDriver(topology, props);

        // Serde 와 토픽
        jsonNodeSerde = new JsonNodeSerde();
        sourceTopic = testDriver.createInputTopic(
            SOURCE_TOPIC, Serdes.String().serializer(), jsonNodeSerde.serializer());
        sinkTopicA = testDriver.createOutputTopic(
            SINK_TOPIC_A, Serdes.String().deserializer(), jsonNodeSerde.deserializer());
        sinkTopicB = testDriver.createOutputTopic(
            SINK_TOPIC_B, Serdes.String().deserializer(), jsonNodeSerde.deserializer());
    } 

    @Test
    @DisplayName("주어진 JSON 메시지에서 userIdHash + userId 및 userIdHash + 내용의 두 토픽으로 분리.")
    void testScenario1() {
        // JSON 데이터
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode input = objectMapper.createObjectNode();
        input.put("user_id", "user1");
        input.put("message", "some chat message");
        ObjectNode input2 = objectMapper.createObjectNode();
        input2.put("user_id", "user2");
        input2.put("message", "other chat message");

        // When
        sourceTopic.pipeInput(input);
        sourceTopic.pipeInput(input2);
        // Then 
        var outputA = sinkTopicA.readRecord();
        var keyA = outputA.getKey();
        var valueA = outputA.getValue();
        System.out.println("keyA: " + keyA);
        System.out.println("valueA: " + valueA);
        // // Assertions
        // assertEquals("user1", key);
        // assertEquals("user1", values.get("user_id").asText());
        // assertEquals("ERROR", values.get("log_level").asText());
        // assertEquals("something wrong", values.get("message").asText());
        // assertThrows(java.util.NoSuchElementException.class, () -> {
        //     sinkTopic.readRecord();
        // });
    }
}
