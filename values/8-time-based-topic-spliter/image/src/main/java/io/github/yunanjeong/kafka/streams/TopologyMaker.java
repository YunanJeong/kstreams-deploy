package io.github.yunanjeong.kafka.streams;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.processor.TopicNameExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yunanjeong.kafka.streams.serdes.JsonNodeSerde;

import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;


public class TopologyMaker { 
    
    private static final Logger LOG = LoggerFactory.getLogger(TopologyMaker.class);
    private static final Pattern INPUT_TOPIC_REGEX = Pattern.compile(System.getenv("INPUT_TOPIC_REGEX"));  

    private StreamsBuilder streamsBuilder = new StreamsBuilder();
    private JsonNodeSerde jsonNodeSerde = new JsonNodeSerde();

    public Topology getMyTopology() throws NoSuchAlgorithmException {
        LOG.info("Getting topology ...");
        KStream<String, JsonNode> inputStream = streamsBuilder.stream(
            INPUT_TOPIC_REGEX,
            Consumed.with(Serdes.String(), jsonNodeSerde)
        );
        
        // Json 검증
        KStream<String, JsonNode> validStream = inputStream.filter(
            (key, value) -> value.get("deserial_error") == null
        );

        TopicNameExtractor<String, JsonNode> dynamicOutputTopicName = (key, value, recordContext) -> {
            // 출력토픽명을 동적으로 정의 및 반환하는 람다표현식(변수)
            // key: topicname_randomuuid,  value: 스키마 및 페이로드, recordContext: 파티션, 오프셋 등 메타정보

            long epochSeconds = value.get("time").asLong();
            String yearMonth = TimeUtils.getYearMonthFromTimestamp(epochSeconds);

            String srcTopicName = recordContext.topic();       // jdbc.mum2.log_xxxxx
            int index = srcTopicName.indexOf("log_");
            String prefix = srcTopicName.substring(0, index);  // jdbc.mum2.
            String srcLogType = srcTopicName.substring(index); // log_xxxxx
   
            return prefix + srcLogType + "_" + yearMonth;      // jdbc.mum2.filtered_log_xxxxx_YYYY_MM
        };
       
        validStream.to(dynamicOutputTopicName, Produced.with(Serdes.String(), jsonNodeSerde));
   
        return streamsBuilder.build();
    }


}
