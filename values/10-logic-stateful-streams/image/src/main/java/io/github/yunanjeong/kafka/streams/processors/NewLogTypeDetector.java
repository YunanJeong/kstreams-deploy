package io.github.yunanjeong.kafka.streams.processors;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 최근 특정 시간(window) 동안 신규로 추가된 로그타입 검출기.
 *
 * - 상태저장소에 로그타입별 "최종 등장 시각"을 유지한다.
 * - 저장소에 없거나(최초 등장), 마지막 등장이 윈도우보다 오래된 로그타입(그 사이 사라졌다가 재등장)이면 신규로 판정한다.
 * - 신규 판정시 key=로그타입인 검출 메시지를 다음 단계로 내보낸다.
 * - 윈도우를 벗어난 항목은 주기적으로 제거하여 저장소 크기를 윈도우 크기에 비례하도록 유지한다.
 *
 * 설정값(로그타입 필드명, 윈도우)은 생성자로 주입받는다. 환경변수 읽기는 호출측(TopologyMaker) 책임.
 */
public class NewLogTypeDetector implements Processor<String, JsonNode, String, JsonNode> {

    private static final Logger LOG = LoggerFactory.getLogger(NewLogTypeDetector.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 로그타입별 "최종 등장 시각"을 담는 상태저장소 이름
    // persistentKeyValueStore -> 로컬은 RocksDB, 복구용 원격 백업은 changelog 토픽(자동 생성)
    public static final String STORE_NAME = "logtype-last-seen-store";

    private final String logTypeField;
    private final Duration window;
    private final long windowMs;

    private ProcessorContext<String, JsonNode> context;
    private KeyValueStore<String, Long> store;

    public NewLogTypeDetector(String logTypeField, Duration window) {
        this.logTypeField = Objects.requireNonNull(logTypeField, "logTypeField");
        this.window = Objects.requireNonNull(window, "window");
        this.windowMs = window.toMillis();
    }

    /**
     * 검출기와 그에 필요한 상태저장소를 함께 제공하는 supplier.
     *
     * stores()로 저장소를 같이 넘기면 Streams가 알아서 등록·연결해주므로
     * 호출측에서 addStateStore/저장소 이름을 따로 신경쓸 필요가 없다.
     *
     * @param logTypeField 로그타입 값이 들어있는 JSON 필드의 key 이름
     * @param window       "최근 특정 시간" 구간의 크기
     */
    public static ProcessorSupplier<String, JsonNode, String, JsonNode> supplier(String logTypeField, Duration window) {
        return new ProcessorSupplier<String, JsonNode, String, JsonNode>() {

            @Override
            public Processor<String, JsonNode, String, JsonNode> get() {
                return new NewLogTypeDetector(logTypeField, window);
            }

            @Override
            public Set<StoreBuilder<?>> stores() {
                return Set.of(
                    Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(STORE_NAME),
                        Serdes.String(),
                        Serdes.Long()
                    )
                );
            }
        };
    }

    @Override
    public void init(ProcessorContext<String, JsonNode> context) {
        this.context = context;
        this.store = context.getStateStore(STORE_NAME);

        // 스트림 시각(레코드에 찍힌 시각) 기준으로 윈도우 주기마다 만료 항목 정리
        context.schedule(window, PunctuationType.STREAM_TIME, this::purgeExpired);
    }

    @Override
    public void process(Record<String, JsonNode> record) {
        String logType = extractLogType(record.value());
        if (logType == null) return;

        long eventTime = record.timestamp();
        Long lastSeen = store.get(logType);
        boolean isNew = (lastSeen == null) || (eventTime - lastSeen > windowMs);

        // 지연 도착 레코드로 인해 최종 등장 시각이 과거로 되돌아가지 않도록 함
        store.put(logType, lastSeen == null ? eventTime : Math.max(lastSeen, eventTime));

        if (isNew) {
            LOG.info("New log type detected within {}: {}", window, logType);
            context.forward(record.withKey(logType).withValue(newLogTypeAlert(logType, eventTime, lastSeen)));
        }
    }

    private String extractLogType(JsonNode value) {
        if (value == null) return null;

        JsonNode logTypeNode = value.get(logTypeField);
        if (logTypeNode == null || logTypeNode.isNull()) return null;

        String logType = logTypeNode.asText();
        return logType.isEmpty() ? null : logType;
    }

    private JsonNode newLogTypeAlert(String logType, long eventTime, Long lastSeen) {
        ObjectNode alert = objectMapper.createObjectNode();
        alert.put("event", "new_log_type");
        alert.put(logTypeField, logType);
        alert.put("detected_at", eventTime);
        alert.put("window", window.toString());
        alert.put("previous_seen_at", lastSeen); // null이면 최초 등장
        return alert;
    }

    // 마지막 등장이 윈도우를 벗어난 로그타입 제거 -> 이후 다시 등장하면 신규로 판정됨
    private void purgeExpired(long streamTime) {
        List<String> expired = new ArrayList<>();
        try (KeyValueIterator<String, Long> it = store.all()) {
            while (it.hasNext()) {
                KeyValue<String, Long> kv = it.next();
                if (streamTime - kv.value > windowMs) expired.add(kv.key);
            }
        }
        expired.forEach(store::delete);
    }
}
