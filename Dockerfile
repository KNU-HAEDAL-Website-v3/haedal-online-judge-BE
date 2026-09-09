# ===== 1단계: 빌드 - JDK 컨테이너 안에서 bootJar 생성 (서버에 자바 설치 불필요) =====
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 빌드 스크립트만 먼저 복사해 의존성을 받아두면, src만 바뀐 재빌드에서 이 레이어를 재사용한다
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew   # git에 실행 비트 없이 올라간 경우 대비 - 없으면 ./gradlew: Permission denied 로 빌드 실패
RUN ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon

# ===== 2단계: 실행 - JRE만 담아 이미지를 가볍게 =====
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
