package com.github.haje01.kafka.streams.examples;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

import com.github.haje01.kafka.streams.examples.serdes.JsonNodeSerde;

public class HashSplitTopology {

    private static final int HASH_LENGTH = 12;

    public String sourceTopic;
    public String sinkTopicA;
    public String sinkTopicB;

    public HashSplitTopology(String sourceTopic, String sinkTopicA, String sinkTopicB) {
        this.sourceTopic = sourceTopic;
        this.sinkTopicA = sinkTopicA;
        this.sinkTopicB = sinkTopicB;
    }

    public Topology createTopology() throws NoSuchAlgorithmException {
        var streamsBuilder = new StreamsBuilder();
        var jsonNodeSerde = new JsonNodeSerde();
        var sourceStream = streamsBuilder.stream(
            sourceTopic, Consumed.with(Serdes.String(), jsonNodeSerde));
        
        // 유저 ID 와 그것의 해쉬키로 구성된 스트림
        KStream<String, String> topicA = sourceStream.map((userId, jsonNode) -> {
            String userIdHash = truncatedHash(userId, HASH_LENGTH);
            return KeyValue.pair(userIdHash, userId);
        });

        // 유저 ID 의 해쉬키와 나머지 필드로 구성된 스트림 
        KStream<String, JsonNode> topicB = sourceStream.map((userId, jsonNode) -> {
            String userIdHash = truncatedHash(userId, HASH_LENGTH);
            // 유저 ID 필드 제거
            ((ObjectNode) jsonNode).remove("user_id");
            return KeyValue.pair(userIdHash, jsonNode);
        });

        topicA.to(sinkTopicA, Produced.with(Serdes.String(), Serdes.String()));
        topicB.to(sinkTopicB, Produced.with(Serdes.String(), jsonNodeSerde));
        return streamsBuilder.build();
    }

    private String truncatedHash(String textToHash, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(textToHash.getBytes(StandardCharsets.UTF_8));
            String fullHash = String.format("%040x", new BigInteger(1, hash));
            return fullHash.substring(0, length);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available.");
        }   
    }
}
