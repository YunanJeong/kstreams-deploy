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


public class MyTest {

    private static String SOURCE_TOPIC = "source";
    private static String SINK_TOPIC_A = "sinkA";
    private static String SINK_TOPIC_B = "sinkB";

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, JsonNode> sourceTopic;
    private TestOutputTopic<String, JsonNode> sinkTopicA;
    private TestOutputTopic<String, JsonNode> sinkTopicB;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        // 설정
        var props = new Properties();


        // 토폴로지

        // Serde 와 토픽

    } 

    @Test
    @DisplayName("주어진 JSON 메시지에서 userIdHash + userId 및 userIdHash + 내용의 두 토픽으로 분리.")
    void testScenario1() {
        
    }
}
