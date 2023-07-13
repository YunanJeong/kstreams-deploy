# kafka-streams-deploy
Test to deploy K-Streams in Container Orchestration Environment


- 이미지 빌드
```bash
# 테스트 또는 개발용 빌드시
skaffold build -p my_first_streams

# 특정 레지스트리 지정시 예시
skaffold build -p my_first_streams --default-repo=127.0.0.0/8 --tag=0.0.1
```

- 개발용 실행 
```bash
skaffold dev -p my_first_streams
```

- 헬름 배포 예시
```bash
# 헬름차트 아카이브 생성
helm package helm/

# 헬름으로 배포하기
helm install kse helm/kse-0.0.1.tgz -f configs/my-first-streams.yaml
```

- memo
    - `helm/templates/deployments/streams.yaml`에서 init Container는 개발 중에는 삭제하면 편하다.