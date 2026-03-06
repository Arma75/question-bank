# 1단계: 빌드 스테이지
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Gradle 래퍼와 설정 파일들을 먼저 복사 (캐시 활용)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# 의존성 미리 다운로드 (소스 코드 변경 시에도 의존성 빌드 시간을 줄임)
RUN ./gradlew dependencies --no-daemon

# 소스 코드 복사 및 빌드
COPY src src
RUN ./gradlew clean bootJar --no-daemon

# 2단계: 실행 스테이지
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 빌드 스테이지에서 생성된 jar 파일만 복사
# build.gradle의 version과 rootProject.name에 따라 파일명이 달라질 수 있습니다.
COPY --from=build /app/build/libs/*.jar app.jar

# Render 등 호스팅 환경에서 사용할 포트 설정 (기본 8080)
EXPOSE 8080

# 메모리 최적화 옵션 추가 (Render 무료 티어 권장)
ENTRYPOINT ["java", "-Xmx400M", "-Xms400M", "-jar", "app.jar"]