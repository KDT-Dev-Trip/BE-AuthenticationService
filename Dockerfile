# ==================================================
# Multi-stage Dockerfile for Authentication Service
# ==================================================

# ========================
# Build Stage
# ========================
FROM gradle:8.10-jdk17 AS builder

WORKDIR /build

# Gradle 래퍼와 빌드 파일 복사
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./

# 종속성 다운로드 (캐시 최적화)
RUN ./gradlew dependencies --no-daemon

# 소스 코드 복사
COPY src/ src/

# 애플리케이션 빌드
RUN ./gradlew build -x test --no-daemon

# JAR 파일 위치 확인
RUN ls -la build/libs/

# ========================
# Test Stage
# ========================
FROM builder AS test

# 테스트 실행
RUN ./gradlew test integrationTest --no-daemon

# 테스트 결과 및 커버리지 리포트 생성
RUN ./gradlew jacocoTestReport --no-daemon

# ========================
# Runtime Base Stage
# ========================
FROM eclipse-temurin:17-jre-alpine AS runtime-base

# 보안 및 성능을 위한 시스템 설정
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# 필수 패키지 설치
RUN apk add --no-cache \
    curl \
    jq \
    tzdata \
    dumb-init

# 타임존 설정
ENV TZ=Asia/Seoul
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 애플리케이션 디렉토리 생성
WORKDIR /app

# 로그 디렉토리 생성
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

# ========================
# Production Stage
# ========================
FROM runtime-base AS production

# 빌드된 JAR 파일 복사
COPY --from=builder /build/build/libs/*.jar app.jar

# JAR 파일 권한 설정
RUN chown appuser:appgroup app.jar

# 헬스체크 스크립트 추가
COPY --chown=appuser:appgroup scripts/healthcheck.sh /app/healthcheck.sh
RUN chmod +x /app/healthcheck.sh

# 사용자 전환
USER appuser

# JVM 튜닝 및 환경 변수 설정
ENV JAVA_OPTS="-Xms512m -Xmx1024m \
               -XX:+UseG1GC \
               -XX:G1HeapRegionSize=16m \
               -XX:+UseStringDeduplication \
               -XX:+OptimizeStringConcat \
               -XX:+UseCompressedOops \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.backgroundpreinitializer.ignore=true"

# 애플리케이션 포트
EXPOSE 8080

# 헬스체크 설정
HEALTHCHECK --interval=30s --timeout=30s --start-period=60s --retries=3 \
    CMD /app/healthcheck.sh

# 애플리케이션 시작 명령
ENTRYPOINT ["dumb-init", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# ========================
# Development Stage
# ========================
FROM runtime-base AS development

# 개발 도구 설치
RUN apk add --no-cache \
    bash \
    git \
    vim \
    htop

# 개발용 JAR 파일 복사
COPY --from=builder /build/build/libs/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

# 개발용 JVM 설정 (디버깅 활성화)
ENV JAVA_OPTS="-Xms256m -Xmx512m \
               -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
               -Dspring.devtools.restart.enabled=true \
               -Dspring.profiles.active=dev"

EXPOSE 8080 5005

CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# ========================
# Metadata
# ========================
LABEL maintainer="DevOps Platform Team"
LABEL version="2.0.0"
LABEL description="OAuth 2.0 Authentication Service with SSO support"
LABEL vendor="DevOps Platform"
LABEL org.opencontainers.image.source="https://github.com/your-org/BE-AuthenticationService"
LABEL org.opencontainers.image.documentation="https://github.com/your-org/BE-AuthenticationService/blob/main/README.md"
LABEL org.opencontainers.image.licenses="MIT"