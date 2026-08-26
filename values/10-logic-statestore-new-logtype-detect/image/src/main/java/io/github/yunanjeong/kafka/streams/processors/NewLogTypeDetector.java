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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

        // 레코드 메타데이터의 타임스탬프다. payload를 파싱한 값이 아니다.
        // TimestampExtractor가 뽑아주며, 기본 추출기는 메타데이터를 그대로 쓴다.
        // 그 값의 의미는 토픽의 message.timestamp.type이 정한다.
        //   CreateTime(기본) : 프로듀서가 찍은 시각 / LogAppendTime : 브로커가 적재하며 찍은 시각
        // 즉 로그가 실제로 발생한 시각(payload의 시각 필드)과는 다를 수 있다.
        long recordTimestamp = record.timestamp();
        Long lastSeen = store.get(logType);
        boolean isNew = (lastSeen == null) || (recordTimestamp - lastSeen > windowMs);

        // 지연 도착 레코드로 인해 최종 등장 시각이 과거로 되돌아가지 않도록 함
        store.put(logType, lastSeen == null ? recordTimestamp : Math.max(lastSeen, recordTimestamp));

        if (isNew) {
            LOG.info("New log type detected within {}: {}", window, logType);
            context.forward(record.withKey(logType).withValue(
                newLogTypeAlert(logType, recordTimestamp, lastSeen, record.value())));
        }
    }

    private String extractLogType(JsonNode value) {
        if (value == null) return null;

        JsonNode logTypeNode = value.get(logTypeField);
        if (logTypeNode == null || logTypeNode.isNull()) return null;

        String logType = logTypeNode.asText();
        return logType.isEmpty() ? null : logType;
    }

    /*
     * 알림 토픽으로 나가는 메시지 포맷.
     * serde가 역직렬화 실패 레코드에 붙이는 errorNode와 같은 뼈대를 쓴다. (JsonNodeSerde 참고)
     *   detected_at : 서버 시각 (RFC3339). 두 종류의 알림이 같은 의미로 갖는 유일한 시각 필드다.
     *   <마커 필드> : 무슨 알림인지 (serde는 deserial_error, 여기는 new_logtype_alert)
     *   data        : 원본 레코드
     * 알림 토픽 소비자가 마커 필드의 존재 여부만 보고 종류를 가를 수 있어야 하므로 이 뼈대를 지킨다.
     */
    private JsonNode newLogTypeAlert(String logType, long recordTimestamp, Long lastSeen, JsonNode source) {
        ObjectNode alert = objectMapper.createObjectNode();
        alert.put("detected_at", rfc3339(Instant.now()));
        alert.put("new_logtype_alert", "New log type detected");
        alert.put(logTypeField, logType);
        alert.put("window", window.toString());
        // 레코드 메타데이터의 시각이다. 로그 발생 시각이 아니다. (process() 주석 참고)
        alert.put("record_timestamp", rfc3339(Instant.ofEpochMilli(recordTimestamp)));   // 신규 판정의 기준
        alert.put("previous_seen_at", lastSeen == null ? null : rfc3339(Instant.ofEpochMilli(lastSeen))); // null이면 최초 등장, 이 값도 record_timestamp 기준
        alert.put("data", source == null ? null : source.toString());
        return alert;
    }

    // RFC3339 표기 (절대 시간). 시간대 표기법만 고르는 것이고 시각 자체가 바뀌지는 않는다.
    private static String rfc3339(Instant instant) {
        return instant.atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
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
