import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.yunanjeong.kafka.streams.TopologyConfig;
import io.github.yunanjeong.kafka.streams.topologies.TopologyProvider;
import io.github.yunanjeong.kafka.streams.topologies.TopologyRegistry;

/* 환경변수 TOPOLOGY로 토폴로지를 고르는 부분의 테스트 */
public class TestTopologyRegistry {

    @Test
    @DisplayName("등록된 이름으로 토폴로지를 찾는다")
    public void findByName() {
        for (String name : TopologyRegistry.names()) {
            TopologyProvider provider = TopologyRegistry.find(name).orElse(null);
            assertNotNull(provider, "not found: " + name);
            assertEquals(name, provider.name());
        }
    }

    @Test
    @DisplayName("모르는 이름이거나 값이 없으면 비어있는 결과를 준다")
    public void findUnknown() {
        assertTrue(TopologyRegistry.find("no-such-topology").isEmpty());
        assertTrue(TopologyRegistry.find(null).isEmpty());
        assertTrue(TopologyRegistry.find("").isEmpty());
    }

    @Test
    @DisplayName("등록된 토폴로지 이름은 서로 겹치지 않는다")
    public void namesAreUnique() {
        List<String> names = TopologyRegistry.names();
        Set<String> unique = new HashSet<>(names);
        assertEquals(names.size(), unique.size(), "duplicated topology name in registry: " + names);
    }

    /*
     * 단일 이미지 패턴의 핵심 요건.
     * 레지스트리는 모든 토폴로지 클래스를 참조하지만, 선택되지 않은 토폴로지의 환경변수가 없다고 해서
     * 앱이 죽으면 안 된다. (설정값을 static 필드로 읽으면 여기서 깨진다)
     */
    @Test
    @DisplayName("선택한 토폴로지의 환경변수만 있으면 나머지가 없어도 빌드된다")
    public void unselectedTopologyConfigIsNotRequired() {
        TopologyConfig onlyCommon = TopologyConfig.of(Map.of(
            "INPUT_TOPIC_REGEX", "test.topic",
            "OUTPUT_TOPIC", "output.topic"
        ));

        // json-filter는 공통 설정만으로 빌드된다 (new-logtype-detect 전용 값은 없음)
        assertNotNull(TopologyRegistry.find("json-filter").orElseThrow().build(onlyCommon));
    }

    @Test
    @DisplayName("선택한 토폴로지의 필수 환경변수가 없으면 빌드 시점에 실패한다")
    public void missingRequiredConfigFails() {
        TopologyConfig onlyCommon = TopologyConfig.of(Map.of(
            "INPUT_TOPIC_REGEX", "test.topic",
            "OUTPUT_TOPIC", "output.topic"
        ));

        TopologyProvider stateful = TopologyRegistry.find("new-logtype-detect").orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> stateful.build(onlyCommon));
    }
}
