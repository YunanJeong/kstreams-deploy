#
# 빌드 단계 
#
FROM gradle:8.1.1-jdk11 as build 
WORKDIR /app
COPY . .
RUN gradle installDist

#
# 패키지 단계
#
FROM openjdk:11-jre-slim
WORKDIR /app 
COPY --from=build /app/app/build/install/app . 
CMD ["bin/app"]


# 일반적인 gradle 빌드파일 생성경로 Project/build/install/Project/

