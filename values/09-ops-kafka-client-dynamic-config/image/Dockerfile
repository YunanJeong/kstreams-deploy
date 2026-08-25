# docker build -t kstreams-xxxxxx:0.0.x .
# docker push my-repo/kstreams-xxxxxx:0.0.x

#
# 빌드 스테이지 
#
FROM maven:3.8.5-openjdk-17-slim AS build 
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

#
# 패키지 단계
# 
# alpine 같은 초경량이미지엔 누락된 라이브러리가 너무 많음(snappy 압축 등)
FROM eclipse-temurin:17-jre-noble
WORKDIR /app 
# 빌드 스테이지의 작업물을 현재 스테이지로 옮긴다.
COPY --from=build /app/target/kstreams-0.0.1-SNAPSHOT.jar .
CMD ["java", "-jar", "kstreams-0.0.1-SNAPSHOT.jar"]
