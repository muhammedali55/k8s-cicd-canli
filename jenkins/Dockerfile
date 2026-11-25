# Jenkins imajı: resmi LTS (JDK 21) + Docker CLI + kubectl + plugins.txt'teki eklentiler.
# docker-compose.yml bu dosyayı build eder; elle "docker build" yapmanız gerekmez.

# Resmi Jenkins LTS imajı, içinde Java 21 var (servislerin ./mvnw verify'ı için de yeterli)
FROM jenkins/jenkins:lts-jdk21

# Paket kurmak için geçici olarak root'a geç
USER root

# Docker CLI ve Buildx eklentisi (Docker'ın resmi apt deposundan).
# Yalnızca CLI kuruyoruz; asıl Docker daemon'u host'ta, socket üzerinden ona bağlanacağız.
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl gnupg \
 && install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
      > /etc/apt/sources.list.d/docker.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends docker-ce-cli docker-buildx-plugin \
 && rm -rf /var/lib/apt/lists/*

# kubectl (Deploy stage'i için). Sürüm sabit; kind cluster sürümüyle en fazla 1 minor fark olmalı.
# v1.37.0 = cluster/kind-config.yaml'daki node sürümüyle aynı
ARG KUBECTL_VERSION=v1.37.0
RUN curl -fsSLo /usr/local/bin/kubectl \
      "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/$(dpkg --print-architecture)/kubectl" \
 && chmod +x /usr/local/bin/kubectl

# Eklentileri imaja göm: Jenkins ilk açıldığında hazır olsunlar
USER jenkins
COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
