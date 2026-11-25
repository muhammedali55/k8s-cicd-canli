# order-service imajı — iki aşamalı (multi-stage) build.
# 1. aşama: JDK + Maven Wrapper ile jar üretilir.
# 2. aşama: yalnızca JRE ve jar alınır; imaj küçük kalır, derleme araçları üretime gitmez.
# Testler burada çalışmaz; CI'da (./mvnw verify) zaten koşuluyor.

# ---------- 1. aşama: build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Önce yalnızca Maven dosyalarını kopyala: pom.xml değişmedikçe bağımlılık katmanı cache'ten gelir.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

# Sonra kaynak kodu kopyala ve jar'ı üret.
COPY src src
RUN ./mvnw -q -B package -DskipTests \
    && cp target/order-service-*.jar app.jar

# ---------- 2. aşama: çalışma imajı ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# root olmayan kullanıcı: container ele geçirilse bile yetkiler kısıtlı kalır.
# Sabit, sayısal UID: K8s'te runAsNonRoot: true ile uyumlu (isimle verilen kullanıcıyı K8s doğrulayamaz).
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home app

COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER 10001

# Sürüm bilgisi imaja build sırasında gömülür: CI kısa git SHA'yı verir
#   docker build --build-arg APP_VERSION=a1b2c3d ...
# Verilmezse "local" kalır. ARG bu aşamada olduğu için Maven katmanlarının önbelleği bozulmaz.
ARG APP_VERSION=local
ENV APP_VERSION=${APP_VERSION}

# Container içinde her servis 8080'de dinler (SPEC §3).
ENV SERVER_PORT=8080
EXPOSE 8080

# MaxRAMPercentage: JVM, container'ın memory limitine göre heap ayarlasın.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
