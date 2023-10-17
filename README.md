# kafka-streams-deploy

KStreams Examples in Container Orchestration Environment

KStreams 관련 자주 사용될 로직, example, pratice 등을 여기서 작업한다.

[차트는 다른 저장소](https://github.com/YunanJeong/my-helm-charts/tree/main/charts/kafka-streams)에서 관리한다.

## 이미지 관련

많은 헬름 차트에서,
앱(image.repository)은 고정되어있고, 이미지 허브(image.registry)와 버전(image.tag)만이 자주 변경된다.

그러나 **Kafka Streams 차트에서는,
앱(image.repository)이 지속적으로 바뀌어야** 한다. 여러 요구사항과 유스케이스에 따른 비즈니스로직을 일반화하기 어렵기 때문이다.

Kafka Streams가 헬름으로 배포되려면, **이미지 자체(Docker Context)가 헬름 차트의 커스텀 Value**인 것처럼 함께 관리되어야 한다. 개별 스트림즈 작업시, 차트 코드는 수정하지않아도, 이미지와 Value는 동시에 자주 수정되기 때문이다.

## 디렉토리

```sh
.
├── chartrepo           # 헬름 차트 아카이브 파일 모음
├── memo
├── skaffold.yaml       # 스트림즈 앱 개발시 사용할 skaffold. 사용하는 차트 버전 기록.
└── values/             # 스트림즈 앱 이미지 및 helm value
    ├── gradleapp         # gradle example
    ├── mavenapp          # maven example
    └── ...
```

## 스트림즈 앱 개발

```sh
# 개발용 이미지 빌드
# skaffold build -p {skaffold's profile}
skaffold build -p mavenapp

# 개발모드
# skaffold dev -p {skaffold's profile}
skaffold dev -p mavenapp

# 배포용 이미지 빌드 및 push
# skaffold build -p {profile} --default-repo={registry} --tag={version} --push
skaffold build -p mavenapp -d "private.docker.wai/yunan" -t live --push
```

## 앱 배포

```sh
# 배포설치
# helm install {releaseName} {chart} -f {value.yaml}
helm install mavenapp chartrepo/kstreams-0.0.3.tgz -f values/mavenapp/value.yaml

# 환경 변수 적용 및 helm 사용
# envsubst < {value} | helm install {릴리즈이름} {차트] -f -
envsubst < mavenapp/value.yaml | helm install kstreams-mvaenapp ../chartrepo/kstreams-0.0.4.tgz -f -
```

## 참고

- 스트림즈 DSL에서 입력토픽으로 regex사용가능 (Java Pattern클래스 활용)
  - 입력토픽 argument 자리에 String 대신 Pattern입력 가능함
