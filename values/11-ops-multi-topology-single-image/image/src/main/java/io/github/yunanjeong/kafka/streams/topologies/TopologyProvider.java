package io.github.yunanjeong.kafka.streams.topologies;

import org.apache.kafka.streams.Topology;

import io.github.yunanjeong.kafka.streams.TopologyConfig;

/**
 * 하나의 스트림 처리 로직(토폴로지) 단위.
 *
 * 단일 이미지 안에 여러 구현체가 들어있고, 환경변수 TOPOLOGY 값으로 하나가 선택된다.
 * 선택된 것만 build()가 호출되므로, 설정값 검증도 이 안에서 한다.
 */
public interface TopologyProvider {

    /** 환경변수 TOPOLOGY에 넣을 식별자. 레지스트리 안에서 유일해야 한다. */
    String name();

    /** 이 토폴로지가 하는 일에 대한 한 줄 설명. 잘못된 TOPOLOGY 값으로 뜰 때 안내용으로 쓰인다. */
    String description();

    /** 설정값을 받아 토폴로지를 만든다. 필요한 환경변수는 여기서 꺼내 쓴다. */
    Topology build(TopologyConfig config);
}
