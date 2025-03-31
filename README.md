# kafka-streams-deploy

KStreams Examples in Container Orchestration Environment

- 쿠버네티스에서 KStreams 배포&관리 방법
- KStreams 예시, 기법, 템플릿 등을 기록

[차트는 다른 저장소](https://github.com/YunanJeong/my-helm-charts/tree/main/charts/kafka-streams)에서 관리한다.

## 이미지 관련

Kafka Streams를 Helm으로 배포할 때, 프로젝트 별로 **이미지 자체(Docker Context)를 Helm 차트의 커스텀 Value**와 함께 관리해야 한다. 개별 스트림즈 작업시, 차트 템플릿을 수정하지는 않더라도, 이미지(Java 코드)와 Value를 동시에 자주 수정해야하기 때문이다. 이 때문에 Helm 단독 사용보다는 빌드부터 배포까지 `skaffold`로 관리하는 것도 좋은 방법이다.

일반적인 오픈소스 Helm 차트에서는,
이미지 버전(image.tag)은 자주 바뀔 수 있으나 레지스트리(image.registry) 및 앱(image.repository)은 고정하여 사용하는 경우가 잦다.

그러나 **Kafka Streams 차트에서는,
앱(image.repository)이 요구사항에 따라 지속적으로 바뀌어야** 한다. 여러 요구사항과 유스케이스에 따라 달라지는 비즈니스로직을 단일버전 이미지나 Helm value로 일반화하여 재사용하기 어렵기 때문이다.

따라서, `Kafka Streams처럼 지속적 배포가 필요한 커스텀 앱은 프로젝트 별로 Docker Context와 Helm Value를 함께 관리`하는 것이 중요하다.

추가) Docker Context에 포함된 파일이 git저장소로 관리하기에 너무 크다면 원출처 URL 다운로드 방식 or Nexus 등으로 구축된 원격 파일 저장소를 활용할 수 있다.

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
helm install mavenapp chartrepo/kstreams-0.0.5.tgz -f values/mavenapp/value.yaml

# 환경 변수 적용 및 helm 사용
# envsubst < {value} | helm install {릴리즈이름} {차트] -f -
envsubst < mavenapp/value.yaml | helm install kstreams-mavenapp ../chartrepo/kstreams-0.0.5.tgz -f -
```

## 기타
