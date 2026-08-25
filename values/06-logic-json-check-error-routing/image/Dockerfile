#
# 빌드 스테이지 
#
FROM maven:3.8.5-openjdk-17-slim as build 
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

#
# 패키지 단계
#
# openjdk에서 jre는 17 공식 이미지 미지원
FROM openjdk:17-jdk-slim
WORKDIR /app 
# 빌드 스테이지의 작업물을 현재 스테이지로 옮긴다.
COPY --from=build /app/target/kstreams-0.0.1-SNAPSHOT.jar .
CMD ["java", "-jar", "kstreams-0.0.1-SNAPSHOT.jar"]
