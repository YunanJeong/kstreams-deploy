package io.github.yunanjeong.kafka.streams;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;

import io.github.yunanjeong.kafka.streams.jsonfilter.JsonFilterTopology;
import io.github.yunanjeong.kafka.streams.newlogtype.NewLogTypeTopology;

/**
 * 스트림 처리 여러 개를 이미지 하나로 다루고, 실행할 때 환경변수 TOPOLOGY로 하나를 고른다.
 * 프로세스 하나가 스트림 처리 하나를 돌린다. 같은 이미지로 릴리스만 나눠 띄우면 처리별로 파드가 뜬다.
 * (application.id는 릴리스마다 반드시 달라야 한다. 같으면 서로 다른 로직이 한 컨슈머그룹을 나눠먹는다.)
 *
 * 스트림 처리 추가하는 법
 *   1) <이름>/ 디렉토리를 만들고 Topology를 만들어 반환하는 클래스를 하나 넣는다.
 *      그 처리에만 쓰이는 헬퍼는 전부 같은 디렉토리에 둔다. (newlogtype/ 참고)
 *   2) 아래 buildTopology의 switch에 case 한 줄, import 한 줄 추가한다.
 *   3) helm value의 env.TOPOLOGY로 어느 것을 띄울지 지정한다.
 */
public class App {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    private static final String TOPOLOGY_ENV = "TOPOLOGY";

    public static void main(final String[] args) {
        try {
            run();
        } catch (IllegalArgumentException e) {
            LOG.error("Cannot start: {}", e.getMessage());
            System.exit(1);
        }
    }

    private static void run() {

        TopologyConfig config = TopologyConfig.fromEnv();

        // 토폴로지 선택과 생성은 Kafka에 접속하기 전에 끝낸다. 이 순서에 의미가 있다.
        // 설정이 잘못된 릴리스는 여기서 죽는다. 아직 컨슈머그룹에 합류하지 않았고 상태 디렉토리도
        // 안 건드렸으므로 남기는 흔적이 없다. (잘못된 처리 결과에 오프셋을 커밋하는 것보다 훨씬 싸다)
        Topology topology = buildTopology(config.require(TOPOLOGY_ENV), config);

        Properties props = new KafkaClientPropertiesLoader().loadAndValidate();
        KafkaStreams kafkaStreams = new KafkaStreams(topology, props);

        props.stringPropertyNames().stream()
            .sorted()
            .forEach(key -> LOG.info("KafkaClientProperties Overrides: {}={}", key, props.getProperty(key)));

        LOG.info("Starting Main Appication ... Target Kafka Broker: " + props.get(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG));
        LOG.info("Application ID: " + props.get(StreamsConfig.APPLICATION_ID_CONFIG));

        kafkaStreams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));
    }

    /**
     * 환경변수 TOPOLOGY 값으로 스트림 처리 하나를 고른다.
     *
     * 고른 것만 build되므로, 선택되지 않은 처리의 환경변수는 없어도 앱이 뜬다.
     * 필수값 검증은 각 build() 안에서 일어난다.
     *
     * 스트림 처리가 하나뿐이라 TOPOLOGY를 생략하고 싶으면 호출측을
     * config.require(TOPOLOGY_ENV) -> config.get(TOPOLOGY_ENV, "그-이름") 으로 바꾼다.
     * 둘 이상이면 필수로 둔다. 기본값을 두면 TOPOLOGY 오타를 조용히 삼켜서, 의도한 것과
     * 다른 로직이 기존 application.id로 도는데 아무도 모르는 상황이 된다.
     */
    public static Topology buildTopology(String name, TopologyConfig config) {
        LOG.info("Selected topology: {}", name);
        return switch (name) {
            case "jsonfilter" -> JsonFilterTopology.build(config);
            case "newlogtype" -> NewLogTypeTopology.build(config);
            default -> throw new IllegalArgumentException(
                "Unknown " + TOPOLOGY_ENV + ": '" + name + "'"
                    + " - available: [jsonfilter, newlogtype]");
        };
    }
}
