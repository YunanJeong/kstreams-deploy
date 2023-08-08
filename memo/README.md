# memo

- KafkaStreams 객체
  - 객체생성시, Topology와 Properties를 인자로 받는다.
  - start() 메소드: 스트림즈 절차를 실행하는 코드

- StreamsBuilder 객체
  - stream(...) 메소드: return KStream 객체
  - table(...) 메소드: return KTable 객체
  - build() 메소드: return Topology 객체
- KStream 객체
  - filter() => 조건만족하는 것만 처리하고, 나머지는 버릴 때 사용
  - map() => 1:1 처리. 하나의 Record를 수정해서 뱉어낸다.
  - flatMap() => 1:N 처리 하나의 Record에서 조건에 따라 여러 Record를 만들어낸다. (e.g. 문자열을 단어들로 분리)
  - branch()
    - deprecated.  2.8버전부터 split() 사용
    - KStream<>이 아니라 KStream<>[] 로 선언된 객체에서 사용한다.
  - repartition() => Record 조건에 따라 다른 처리를 한다. (분기개념은 아니다. 출력은 하나의 outputstream으로 나온다.)

- BranchedKStream, Map<String, KStream<K,V>>
  - split() => 여러 stream으로 "분기"할 때 사용. filter를 여러 개 사용한 것과 비슷하다고 보면 된다.


- Topology 클래스
  - 스트림즈 앱 1개에 Topology 객체 1개가 일반적
    => StreamsBuilder도 Topology 구조를 만들 때 사용하므로 객체 1개만 쓰는 것이 일반적
  - Topology 객체를 여러 개써도 상관없다.
    => Topology마다 독립적인 파이프라인으로 실행됨
      => 이 때는 Properties의 APPLICATION_ID_CONFIG도 중복되지 않는 이름을 부여해야 함
    => 같은 src_topic을 분기해서 처리할 때 Topology를 여러 개 쓰면 중복읽기되어 비효율적
      => 이런 경우, 하나의 Topology에서 branch를 쓰는 게 낫다.
