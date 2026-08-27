package io.github.yunanjeong.kafka.streams.newlogtype;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Set;

/**
 * 신규 로그타입 검출기.
 *
 * - 상태저장소에 로그타입별 "최초 등장 시각"을 유지한다. 등록된 로그타입 목록 그 자체다.
 * - 저장소에 없는 로그타입이면 신규로 판정하고, 그때 한 번만 기록한다.
 * - 신규 판정시 key=로그타입인 검출 메시지를 다음 단계로 내보낸다.
 *
 * 시간 구간(윈도우) 개념이 없다. 로그타입은 사람이 제품을 변경할 때만 늘어나고 저절로 사라지지 않으므로,
 * 기간으로 집계하지 않고 등록 목록으로 유지한다. 그래서
 *   - 항목 수는 로그타입 수로 고정된다 (파티션당). 만료 정리가 필요 없다.
 *   - store.put은 로그타입이 처음 등장할 때만 실행된다. 이후 레코드는 store.get만 한다.
 *     즉 정상 운영 중 changelog 쓰기가 발생하지 않는다.
 *   - 한번 등장한 로그타입은 오래 안 보이다가 다시 와도 신규로 재판정되지 않는다.
 *
 * 설정값(로그타입 필드명)은 생성자로 주입받는다. 환경변수 읽기는 호출측 책임.
 *
 * NewLogTypeTopology 전용 헬퍼이므로 public이 아니다. 같은 패키지 밖에서는 보이지 않으므로,
 * 다른 스트림 처리가 실수로 끌어다 쓰는 일이 컴파일 단계에서 막힌다.
 * 여러 스트림 처리가 공유해야 하는 물건이 생기면 그때 상위 패키지로 올리고 public으로 연다.
 */
class NewLogTypeDetector implements Processor<String, JsonNode, String, JsonNode> {

    private static final Logger LOG = LoggerFactory.getLogger(NewLogTypeDetector.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 로그타입별 "최초 등장 시각"을 담는 상태저장소 이름
    // persistentKeyValueStore -> 로컬은 RocksDB, 복구용 원격 백업은 changelog 토픽(자동 생성)
    static final String STORE_NAME = "logtype-first-seen-store";

    private final String logTypeField;

    private ProcessorContext<String, JsonNode> context;
    private KeyValueStore<String, Long> store;

    NewLogTypeDetector(String logTypeField) {
        this.logTypeField = Objects.requireNonNull(logTypeField, "logTypeField");
    }

    /**
     * 검출기와 그에 필요한 상태저장소를 함께 제공하는 supplier.
     *
     * stores()로 저장소를 같이 넘기면 Streams가 알아서 등록·연결해주므로
     * 호출측에서 addStateStore/저장소 이름을 따로 신경쓸 필요가 없다.
     *
     * @param logTypeField 로그타입 값이 들어있는 JSON 필드의 key 이름
     */
    static ProcessorSupplier<String, JsonNode, String, JsonNode> supplier(String logTypeField) {
        return new ProcessorSupplier<String, JsonNode, String, JsonNode>() {

            @Override
            public Processor<String, JsonNode, String, JsonNode> get() {
                return new NewLogTypeDetector(logTypeField);
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
    }

    @Override
    public void process(Record<String, JsonNode> record) {
        String logType = extractLogType(record.value());
        if (logType == null) return;

        // 이미 등록된 로그타입이면 아무것도 하지 않는다 (store.put도 하지 않음)
        if (store.get(logType) != null) return;

        // 레코드 메타데이터의 타임스탬프다. payload를 파싱한 값이 아니다.
        // TimestampExtractor가 뽑아주며, 기본 추출기는 메타데이터를 그대로 쓴다.
        // 그 값의 의미는 토픽의 message.timestamp.type이 정한다.
        //   CreateTime(기본) : 프로듀서가 찍은 시각 / LogAppendTime : 브로커가 적재하며 찍은 시각
        long recordTimestamp = record.timestamp();
        store.put(logType, recordTimestamp);

        LOG.info("New log type detected: {}", logType);
        context.forward(record.withKey(logType).withValue(
            newLogTypeAlert(logType, recordTimestamp, record.value())));
    }

    private String extractLogType(JsonNode value) {
        if (value == null) return null;

        JsonNode logTypeNode = value.get(logTypeField);
        if (logTypeNode == null || logTypeNode.isNull()) return null;

        String logType = logTypeNode.asText();
        return logType.isEmpty() ? null : logType;
    }

    private JsonNode newLogTypeAlert(String logType, long recordTimestamp, JsonNode source) {
        ObjectNode alert = objectMapper.createObjectNode();
        alert.put("detected_at", rfc3339(Instant.now()));                   // 서버 시각
        alert.put("new_logtype_alert", "New log type detected");            // 마커 필드
        alert.put(logTypeField, logType);
        // 레코드 메타데이터의 시각이다. 로그 발생 시각이 아니다. (process() 주석 참고)
        alert.put("record_timestamp", rfc3339(Instant.ofEpochMilli(recordTimestamp))); // 이 로그타입의 최초 등장 시각으로 기록된 값
        alert.put("data", source == null ? null : source.toString());       // 원본 레코드
        return alert;
    }

    // RFC3339 표기 (절대 시간). 시간대 표기법만 고르는 것이고 시각 자체가 바뀌지는 않는다.
    private static String rfc3339(Instant instant) {
        return instant.atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
