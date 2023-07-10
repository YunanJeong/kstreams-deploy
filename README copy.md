# kafka-streams-examples

쿠버네티스 환경에서 Kafka Streams 예제들을 보여준다. 
- 자체 Kafka 클러스터 환경
- 가상의 로그 생성기 
- Gradle 로 Java 코드 빌드 및 의존성 해결 

폴더 구조는 아래와 같다.

```bash
configs/       # 프로파일별 설정 파일들
helm/          # Helm 차트
filterkey/     # filter + key 예제
producer/      # 가짜 로그 생성기
skaffold.yaml  # Skaffold 설정 파일
```

## 변수값 

Helm 차트 기본 변수값은 아래와 같다. 

```yaml
# 카프카 설정
kafka: 
  # 토픽 기본 파티션 수
  numPartitions: 2
  srcTopic: default-src
  sinkTopic: default-sink

# 로그 생성기 설정
producer:
  enabled: true
  image:
    registry: ""
    repository: "library/kse-producer"
    tag: 0.0.1
  # 로그 생성 타입
  type: none

# filterkey 예제 설정 
filterkey:
  # 파드 수
  replicas: 2
  image:
    registry: ""
    repository: "library/kse-filterkey"
    tag: 0.0.1

```

## 로그 생성기 (producer)

로그 생성기는 대상 토픽에 아래와 같은 가상의 로그를 발생시킨다 (기본 100개).

```json
{"user_id": "ID29938", "timestamp": 1688460924.2553785, "datetime": "2023-07-04 08:55:24.255", "log_level": "WARN", "message": "Choose this best true white movie Democrat major Democrat wide seat race."}
```

- 로그 레벨 (`log_level`) 은 `INFO`, `WARN`, `ERROR` 의 세 가지 종류이다.

## 필터 + 키 (filterkey) 예제

- 소스 토픽 (`filterkey-src`) 에는 JSON 형태의 문자열인 메시지가 있음
- 그것을 읽어와 JSON 으로 파싱 후
- 특정 필드의 값이 조건에 맞는 (`log_level` 이 `ERROR` 인) 것만 선택하고
- 지정된 필드 (`user_id`) 를 Key 로 싱크 토픽 (`filterkey-sink`) 에 저장
- 기본 `replicas` 값은 `2`

개발용 실행 
```bash
skaffold dev -p filterkey
```

producer 의 로그에 남은 `Errors` 수와, 싱크 토픽 (`filterkey-sink`) 의 메시지 수가 일치하면 성공한 것이다.
