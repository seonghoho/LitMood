# ── build ─────────────────────────────────────────────────────
FROM gradle:8.11-jdk21 AS build
WORKDIR /app
# 의존성 레이어를 먼저 확정해 소스 변경 시 재다운로드를 피한다
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts ./
RUN gradle dependencies --no-daemon > /dev/null 2>&1 || true
COPY --chown=gradle:gradle src ./src
RUN gradle bootJar --no-daemon
# Layered JAR 로 분해 — 변경 빈도가 다른 계층을 분리해 이미지 캐시 적중률을 높인다
RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination extracted

# ── runtime ───────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 변경 빈도가 낮은 레이어부터 복사한다
COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./

RUN addgroup -S app && adduser -S app -G app && chown -R app:app /app
USER app

EXPOSE 8080
# 컨테이너 메모리 한도를 인식해 힙을 잡는다 (K8s limits 와 정합)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseContainerSupport", \
            "org.springframework.boot.loader.launch.JarLauncher"]
