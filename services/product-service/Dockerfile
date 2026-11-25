# product-service imajı (multi-stage).
# 1. aşama: JDK + Maven Wrapper ile jar'ı derler.
# 2. aşama: yalnızca JRE + jar; non-root kullanıcıyla çalışır.

# ---------- 1) Build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Önce sadece wrapper + pom: bağımlılıklar ayrı katmanda önbelleğe alınır,
# kod değişince her seferinde yeniden indirilmez.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

# Sonra kaynak kod. Testler CI'da ayrı adımda koşar; burada atlanır.
COPY src src
RUN ./mvnw -q -B package -DskipTests && cp target/product-service-*.jar app.jar

# ---------- 2) Run ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Root olmayan kullanıcı (sabit UID: K8s'te runAsNonRoot ile uyumlu)
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home app

COPY --from=build --chown=app:app /workspace/app.jar app.jar

USER 10001

# Sürüm bilgisi imaja build sırasında gömülür: CI kısa git SHA'yı verir
#   docker build --build-arg APP_VERSION=a1b2c3d ...
# Verilmezse "local" kalır. ARG bu aşamada olduğu için Maven katmanlarının önbelleği bozulmaz.
ARG APP_VERSION=local
ENV APP_VERSION=${APP_VERSION}

# Container içinde servis her zaman 8080'de dinler.
ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
