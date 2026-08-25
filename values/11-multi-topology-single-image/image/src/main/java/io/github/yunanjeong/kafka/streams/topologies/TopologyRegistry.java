package io.github.yunanjeong.kafka.streams.topologies;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.yunanjeong.kafka.streams.topologies.jsonfilter.JsonFilterTopology;
import io.github.yunanjeong.kafka.streams.topologies.newlogtype.NewLogTypeTopology;

/**
 * 이미지에 포함된 토폴로지 목록.
 *
 * 토폴로지를 추가하려면 (1) TopologyProvider 구현체를 만들고 (2) 아래 PROVIDERS에 한 줄 추가하면 된다.
 * classpath 스캔 같은 자동 탐색을 쓰지 않고 명시적으로 나열하는 이유는,
 * shade(uber-jar)로 말아도 목록이 그대로 유지되고 이 파일만 보면 뭐가 들어있는지 알 수 있기 때문이다.
 */
public final class TopologyRegistry {

    private static final List<TopologyProvider> PROVIDERS = List.of(
        new JsonFilterTopology(),
        new NewLogTypeTopology()
    );

    private static final Map<String, TopologyProvider> BY_NAME = indexByName(PROVIDERS);

    private TopologyRegistry() {
    }

    public static Optional<TopologyProvider> find(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(BY_NAME.get(name.trim()));
    }

    /** 잘못된 TOPOLOGY 값으로 떴을 때 뭘 쓸 수 있는지 알려주기 위한 목록 */
    public static List<TopologyProvider> providers() {
        return PROVIDERS;
    }

    public static List<String> names() {
        return PROVIDERS.stream().map(TopologyProvider::name).toList();
    }

    private static Map<String, TopologyProvider> indexByName(List<TopologyProvider> providers) {
        Map<String, TopologyProvider> index = new LinkedHashMap<>();
        for (TopologyProvider provider : providers) {
            TopologyProvider duplicated = index.put(provider.name(), provider);
            if (duplicated != null) {
                throw new IllegalStateException("Duplicated topology name: " + provider.name());
            }
        }
        return Map.copyOf(index);
    }
}
