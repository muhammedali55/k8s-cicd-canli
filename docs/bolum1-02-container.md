# Bölüm 1.02 — Container: Spring Boot servisini imaja koymak

> Atıf yapılan dosyalar: `services/product-service/Dockerfile`, `services/order-service/Dockerfile`,
> `services/*/.dockerignore`, `services/*/src/main/resources/application.yml`

## 1. Neden?

"Benim makinemde çalışıyordu" cümlesinin kaynağı, uygulamanın her ortamda farklı bir JDK, farklı ayar ve farklı
bağımlılıkla çalışması. Container imajı uygulamayı çalışma ortamıyla birlikte paketler: CI'da build edilen imaj
laptop'ta, Jenkins'te ve Kubernetes'te **birebir aynı** şekilde çalışır. Kubernetes'e geçmeden önce bu paketin
içinde ne olduğunu bilmemiz gerekiyor.

## 2. Kavramlar

- **İmaj / container** `[junior]`: İmaj salt-okunur bir şablondur, container ise onun çalışan örneği.
- **Multi-stage build** `[junior]`: Dockerfile'da iki `FROM` var. `eclipse-temurin:21-jdk` ile derliyoruz, `eclipse-temurin:21-jre`
  ile çalıştırıyoruz. Derleme araçları (JDK, Maven, kaynak kod) son imaja girmiyor.
- **Katman önbelleği (layer cache)** `[senior]`: Dockerfile önce yalnızca `mvnw`, `pom.xml` ve `.mvn`'i kopyalayıp
  `dependency:go-offline` çalıştırıyor; kaynak kodu **sonra** kopyalıyor. Böylece kod değişince bağımlılık katmanı yeniden indirilmiyor.
- **`.dockerignore`** `[junior]`: `target/`, `.git` ve IDE dosyaları build bağlamına girmiyor. Build hızlanıyor, imaj tekrar üretilebilir oluyor.
- **Non-root kullanıcı** `[senior]`: Container root olarak çalışmıyor. İki servis de sabit, sayısal UID kullanıyor
  (`useradd --uid 10001`, `USER 10001`). Neden sayısal? Kubernetes'te `runAsNonRoot: true` açılırsa kubelet yalnızca sayısal UID'i
  doğrulayabiliyor; `USER app` gibi bir isim olsaydı pod başlamazdı.
- **Build-arg ile sürüm gömme (`ARG` + `ENV`)** `[senior]`: Çalışma aşamasında `ARG APP_VERSION=local` ve
  `ENV APP_VERSION=${APP_VERSION}` var. CI `--build-arg APP_VERSION=<kısa-sha>` verince sürüm imajın içine yazılıyor ve `/api/info` bunu gösteriyor.
  Verilmezse `local` kalıyor. `ARG` bilerek Maven katmanlarından **sonra** yazıldı: sürüm her build'de değişse bile bağımlılık önbelleği bozulmuyor.
- **`-XX:MaxRAMPercentage=75`** `[senior]`: JVM heap'i container'ın bellek limitine göre ayarlıyor (K8s'te limit 512Mi).
- **12-factor yapılandırma (ortam değişkeni)** `[junior]`: `application.yml`'deki değerler lokal geliştirme için. Container'da
  `SERVER_PORT`, `SPRING_DATASOURCE_URL`, `DEMO_BROKEN`, `PRODUCT_SERVICE_URL` gibi ortam değişkenleriyle eziliyorlar.
  `docker run -e APP_VERSION=...` imaja gömülü değeri de ezer.
- **Port eşleme** `[junior]`: Container içinde her servis 8080'de dinliyor (`ENV SERVER_PORT=8080`). Lokal sözleşme:
  product 8081, order 8082. Yani `-p 8081:8080` ve `-p 8082:8080`.
- **Testler imajda koşmaz** `[architect]`: Dockerfile `package -DskipTests` kullanıyor; testler CI'da ayrı bir adımda
  (`./mvnw verify`) koşuyor. İmaj build'i hızlı kalıyor ve sorumluluklar ayrılıyor.

## 3. Canlı akış

Tüm komutlar **repo kökünden** çalıştırılır.

1. **Önce testler (container olmadan).**

   ```bash
   (cd services/product-service && ./mvnw -q verify) && echo "product OK"
   (cd services/order-service   && ./mvnw -q verify) && echo "order OK"
   ```

   Beklenen: `product OK` ve `order OK` (14 ve 17 test). Testler H2 ile koşuyor (`test` profili), PostgreSQL gerekmiyor.

2. **Dockerfile'ı satır satır oku.** `services/product-service/Dockerfile`: iki aşama, bağımlılık katmanı, non-root,
   `ARG/ENV APP_VERSION`, `ENV SERVER_PORT=8080`. Ardından order-service'inkini yan yana aç: mantık aynı, yorumlar farklı
   (iki ayrı ajan birbirini görmeden yazdı).

3. **İki imajı build et.** Tag'ler `cluster/setup-cluster.sh`'in aradığı adlarla aynı (`product-service:local`, `order-service:local`):

   ```bash
   docker build --build-arg APP_VERSION=demo1 -t product-service:local services/product-service
   docker build --build-arg APP_VERSION=demo1 -t order-service:local   services/order-service
   ```

   Beklenen: build ve çalışma aşamaları; ilk seferde bağımlılık indirme birkaç dakika sürer. Bu iki build cluster
   kurulumunda gerçekten çalıştırıldı ve başarıyla bitti.
   Aynı komutu hemen tekrar çalıştır: tüm adımlar `CACHED` olmalı ve birkaç saniyede bitmeli.

   > **zsh tuzağı (canlıda yaşandı):** Döngüyle build ediyorsan `$s:local` yazma. zsh `:l`'yi "küçük harf" değiştiricisi
   > sanıyor ve imaj adı `order-serviceocal` oluyor. Doğrusu:
   >
   > ```bash
   > for s in product-service order-service; do docker build -t "${s}:local" "services/${s}"; done
   > ```

4. **İmajı incele.**

   ```bash
   docker image ls | grep -E "product-service|order-service"
   docker history product-service:local
   docker image inspect product-service:local --format '{{.Config.User}} {{.Config.Env}}'
   ```

   Beklenen (tahmini biçim): `local` tag'li iki imaj, boyutları birkaç yüz MB (JRE + Spring Boot jar).
   `docker history`'de JDK katmanları **yok**. `inspect` çıktısında `10001` ve `APP_VERSION=demo1`, `SERVER_PORT=8080`.

5. **PostgreSQL'i bir Docker ağında başlat ve veritabanlarını aç.**

   ```bash
   docker network create demo-net
   docker run -d --name pg --network demo-net -p 5432:5432 \
     -e POSTGRES_PASSWORD=postgres postgres:17
   sleep 5 && docker exec pg pg_isready -U postgres
   docker exec pg psql -U postgres -c "CREATE DATABASE products;" -c "CREATE DATABASE orders;"
   ```

   Beklenen: `/var/run/postgresql:5432 - accepting connections`, ardından iki kez `CREATE DATABASE`.
   (`postgres` şifresi yalnızca lokal demo için; `CLAUDE.md` buna açıkça izin veriyor.)

6. **product-service'i çalıştır.**

   ```bash
   docker run -d --name product-service --network demo-net -p 8081:8080 \
     -e SPRING_DATASOURCE_URL=jdbc:postgresql://pg:5432/products \
     product-service:local
   docker logs -f product-service     # "Started ProductServiceApplication" görünce Ctrl+C
   ```

   ```bash
   curl -s http://localhost:8081/api/products
   curl -s http://localhost:8081/api/info
   curl -s http://localhost:8081/actuator/health/readiness
   ```

   Beklenen:
   - `[{"id":1,"name":"Klavye","price":750.00,"stock":10},{"id":2,"name":"Mouse","price":350.00,"stock":25},{"id":3,"name":"Monitör","price":5200.00,"stock":5}]`
     (cluster'da alınan gerçek çıktıyla aynı veri)
   - `{"service":"product-service","version":"demo1"}`: değer build-arg'dan geldi, `-e` vermedik.
   - (tahmini) `{"status":"UP","components":{...}}`: `db`, `demoBroken`, `readinessState` bileşenleri görünür
     (product-service'te `show-components: always` açık).

   Logda her istek tek satır olarak düşer, örneğin `GET /api/products 200 35ms` (`RequestLoggingFilter`).

7. **Non-root olduğunu kanıtla.**

   ```bash
   docker exec product-service id
   ```

   Beklenen (tahmini): `uid=10001(app) gid=10001(app) groups=10001(app)`.

8. **order-service'i çalıştır ve sipariş ver.** order-service, product-service'e ağ içindeki **container adıyla** ulaşıyor:

   ```bash
   docker run -d --name order-service --network demo-net -p 8082:8080 \
     -e SPRING_DATASOURCE_URL=jdbc:postgresql://pg:5432/orders \
     -e PRODUCT_SERVICE_URL=http://product-service:8080 \
     order-service:local
   sleep 15
   curl -s -X POST http://localhost:8082/api/orders \
     -H 'Content-Type: application/json' -d '{"productId":1,"quantity":2}'
   ```

   Beklenen: HTTP 201 ve
   `{"id":1,"productId":1,"quantity":2,"totalPrice":1500.00,"status":"CREATED","createdAt":"..."}`
   (cluster'daki gerçek çıktıyla aynı biçim). Ardından `curl -s http://localhost:8081/api/products/1` → `stock` artık `8`.

9. **Ortam değişkeni build-arg'ı ezer.**

   ```bash
   docker run --rm -e APP_VERSION=elle-verdim --network demo-net \
     -e SPRING_DATASOURCE_URL=jdbc:postgresql://pg:5432/products -p 8093:8080 -d --name product-env product-service:local
   sleep 15 && curl -s http://localhost:8093/api/info; docker rm -f product-env
   ```

   Beklenen (tahmini): `"version":"elle-verdim"`. İmajdaki `ENV` bir varsayılandır, çalışma anında ezilebilir.
   Kubernetes'te bu yüzden ConfigMap'e `APP_VERSION` koymuyoruz: koysaydık imajdaki doğru değeri ezerdi.

10. **Bozuk sürüm anahtarını önizle** (Bölüm 2.02'ye hazırlık):

    ```bash
    docker run -d --name product-broken --network demo-net -p 8091:8080 \
      -e SPRING_DATASOURCE_URL=jdbc:postgresql://pg:5432/products -e DEMO_BROKEN=true \
      product-service:local
    sleep 15
    curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8091/actuator/health/readiness
    curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8091/actuator/health/liveness
    ```

    Beklenen: readiness `503`, liveness `200`. Bu davranış product-service'teki `ActuatorEndpointsTest`'te
    ("Readiness DOWN (503) döner, liveness UP kalır") de test ediliyor. Uygulama "canlı ama trafiğe hazır değil";
    Kubernetes'te bunun karşılığı: pod yeniden başlatılmaz ama trafik de almaz.

11. **Temizlik.**

    ```bash
    docker rm -f product-broken order-service product-service pg
    docker network rm demo-net
    ```

> 5–10. adımlardaki `docker run` akışı bu sürümde çalıştırılmadı; JSON örnekleri dışındaki çıktılar tahminidir.

## 4. Bozalım ve düzeltelim: "container içinde localhost başka yerdir"

`application.yml`'deki varsayılan veritabanı adresi `jdbc:postgresql://localhost:5432/products`. Bu adres lokal IDE'de doğru,
container'da yanlış. Ağı ve `SPRING_DATASOURCE_URL`'i vermeden çalıştır:

```bash
docker network create demo-net
docker run -d --name pg --network demo-net -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:17
sleep 5 && docker exec pg psql -U postgres -c "CREATE DATABASE products;"

docker run --rm --name product-bad -p 8081:8080 product-service:local
```

Beklenen (tahmini metin): açılışta Flyway / Hikari hatası; özünde `Connection to localhost:5432 refused`. Postgres ayakta ve
5432 host'a açık olduğu halde! Çünkü container içinde `localhost` **container'ın kendisi**, laptop değil.

Düzeltme: container'ı aynı Docker ağına al ve Postgres'e container adıyla (`pg`) ulaş:

```bash
docker run --rm --name product-good --network demo-net -p 8081:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://pg:5432/products product-service:local
```

Kubernetes'te de mantık aynı: ConfigMap'teki `jdbc:postgresql://postgres:5432/products` adresindeki `postgres`,
bir Service'in DNS adı (`k8s/base/postgres/service.yaml`).

Temizlik: `docker rm -f pg; docker network rm demo-net`.

## 5. Sık sorulan sorular

**Neden `latest` tag kullanmıyoruz?**
`latest`, "hangi commit canlıda?" sorusunun cevabını siler ve rollback'i imkânsızlaştırır. `CLAUDE.md` bunu yasaklıyor.
Lokalde `:local`, cluster'da başlangıçta `0.1.0`, CI'da kısa git SHA kullanıyoruz.

**Testleri neden Dockerfile içinde koşmuyoruz?**
CI pipeline'ı testleri zaten `./mvnw verify` ile ayrı bir adımda koşuyor (bkz. Bölüm 2.01). İmaj build'inde tekrar koşmak
süreyi ikiye katlar. Testcontainers da kullanılmıyor (SPEC: Jenkins'te Docker-in-Docker derdi olmasın); testler H2 ile koşuyor.

**`APP_VERSION` neden ConfigMap'te değil de imajda?**
Çünkü sürüm imajın bir özelliği. ConfigMap'te olsaydı `kubectl set image` (Yol A) imajı değiştirir ama ConfigMap'teki sürüm
yazısı eski kalırdı; `/api/info` yalan söylerdi. Sürüm imaja gömülünce hangi yolla deploy edilirse edilsin doğru değer gelir.

**Distroless ya da Alpine imaj daha küçük olmaz mıydı?**
Olurdu. SPEC çalışma imajı olarak `eclipse-temurin:21-jre`'yi seçiyor: tanıdık, içinde `bash` var ve eğitimde
`docker exec` ile içine bakmak kolay. Üretimde boyut ve saldırı yüzeyi için daha küçük imajlar düşünülebilir.

**İki servisin Dockerfile'ı neden birebir aynı değil?**
İki farklı ajan birbirini görmeden yazdı. Mantık aynı, farklar kozmetik (yorumlar, `MaxRAMPercentage=75` / `75.0`).
Önemli farklar (ör. ilk sürümde order-service'teki `USER app`) review'da sayısal `USER 10001`'e eşitlendi.
