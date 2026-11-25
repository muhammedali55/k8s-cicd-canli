# Bölüm 1.01 — AI ile proje: SPEC → plan → sözleşme → paralel ajanlar → review

> Atıf yapılan dosyalar: `SPEC.md`, `CLAUDE.md`, `.claude/agents/*.md`, `contracts/product-api.yaml`,
> `prompts/ANA_AJAN_PROMPTLARI.md`, `ONCE_BENI_OKU.md`

## 1. Neden?

"AI'a projeyi yaz de" yaklaşımı ilk 10 dakikada etkileyici görünür, sonra dağılır: isimler tutmaz, port'lar çakışır,
iki servis birbirini farklı anlar. Bu bölümün derdi kod yazmak değil, **AI'ın çalışacağı çerçeveyi** kurmak:
tek doğruluk kaynağı (SPEC), herkesin uyduğu kurallar (CLAUDE.md), servisler arası sözleşme (OpenAPI) ve
her adımın sonunda insan kontrolü. Hızı AI getirir, doğruluğu süreç getirir.

## 2. Kavramlar

- **SPEC (tek doğruluk kaynağı)** `[junior]`: `SPEC.md`. İsimler, port'lar, paketler, sürümler, endpoint'ler ve "bitti"
  tanımı burada. Ajanlar buradan sapamaz; bir çelişki görürlerse durup sorarlar (bkz. `SPEC.md`'nin en üstündeki not ve `CLAUDE.md`'nin ilk kuralı).
- **CLAUDE.md (proje kuralları)** `[junior]`: Claude Code'un her oturumda otomatik okuduğu dosya. Bizdeki kurallar: Lombok yok,
  DTO'lar `record`, her manifest'te Türkçe yorum, `latest` tag yasak, her container'da `requests/limits`.
- **Alt ajan (sub-agent)** `[senior]`: `.claude/agents/` altındaki her `.md` dosyası bir ajan. Başlığında `name`,
  `description` (ana ajan onu ne zaman çağıracağını buradan anlar) ve `tools` (izin verilen araçlar) var.
  Her ajanın kendi klasörü ve kendi "Doğrulama" komutları var.
- **Sözleşme-önce (contract-first)** `[senior]`: `contracts/product-api.yaml` (OpenAPI 3.1). product-service ajanı bu uçları
  **sunar**, order-service ajanı onları **çağırır**. İki ajan birbirinin kodunu görmez
  (`order-service-agent.md`: "product-service'in koduna bakma … tek bağın sözleşme").
- **Paralelleştirme ve bağımlılık grafiği** `[architect]`: Hangi iş hangisini bekler? Sözleşme yazılmadan servis ajanları
  başlayamaz, docs ajanı da herkes bitince başlar. İşin doğru bölündüğünü ajanların birbirinin dosyasına dokunmamasından anlarsın
  ("Yalnızca sana verilen klasörde çalış").
- **İnsan kontrol noktası (human-in-the-loop)** `[senior]`: `prompts/ANA_AJAN_PROMPTLARI.md`'deki her adımın sonunda bir
  **Kontrol** durağı var. Onay vermeden sonraki adıma geçilmez.
- **Review / entegrasyon doğrulaması** `[architect]`: Ajanlar tek tek "yeşil" olsa bile bütün yeşil olmayabilir.
  Adım 4'te çapraz kontrol yapılır: client ↔ sözleşme ↔ controller, manifest ↔ kod, pipeline imaj adı ↔ overlay.
- **Bulguyu doğrulamak** `[senior]`: Review'u yapan da bir AI. Onun bulguları da yanlış olabilir (aşağıda gerçek bir örnek var).
  Her bulgu, kanıtı (komut çıktısı) görülmeden "doğru" sayılmaz.

### Ajanlar ve sınırları

| Ajan | Yazdığı yer | Araçlar | Kime dayanır |
| --- | --- | --- | --- |
| `product-service-agent` | `services/product-service/` | Read, Write, Edit, Bash, Glob, Grep | SPEC + sözleşme |
| `order-service-agent` | `services/order-service/` | Read, Write, Edit, Bash, Glob, Grep | SPEC + **yalnızca** sözleşme |
| `platform-agent` | `cluster/`, `k8s/` | Read, Write, Edit, Bash, Glob, Grep | SPEC'teki isim/port/health uçları |
| `pipeline-agent` | `.github/`, `jenkins/`, `gitops/` | Read, Write, Edit, Bash, Glob, Grep | SPEC |
| `docs-agent` | `docs/` | Read, Write, Edit, Glob, Grep (**Bash yok**) | Diğer herkesin çıktısı |

> Dikkat çekilecek nokta: docs-agent'ın Bash yetkisi yok. Bu notlardaki çıktıların bir kısmı cluster'da gerçekten
> çalıştırıldı ve öyle işaretlendi; geri kalanı kod okunarak çıkarıldı ve **"tahmini"** diye işaretlendi. Tahmini olanları
> canlıda kendi ekranında teyit et.

### Bağımlılık sırası

| Sıra | İş | Paralel mi? | Neden bekliyor |
| --- | --- | --- | --- |
| 1 | Plan (kod yok) | Hayır | İnsan onayı |
| 2 | Sözleşme (`contracts/product-api.yaml`) | Hayır | İki servis ajanı buna bağlı |
| 3 | product / order / platform / pipeline ajanları | **Evet, 4'ü birden** | Hepsi SPEC + sözleşmeyle yetinir |
| 4 | Birleştirme ve review | Hayır | Herkesin çıktısını görmesi gerekiyor |
| 5 | Cluster'da çalıştırma | Hayır | İmajlar ve manifest'ler hazır olmalı |
| 6 | Eğitmen notları (docs-agent) | Hayır | Tüm kod bitmiş olmalı |
| 7 | Ölçüm | Hayır | Her şey bitmiş olmalı |

## 3. Canlı akış

1. **Çerçeveyi göster.** Repo kökünde:

   ```bash
   ls -a
   cat SPEC.md
   cat CLAUDE.md
   ```

   Beklenen: SPEC'te teknoloji tablosu (Java 21, Spring Boot 4.1.x, PostgreSQL 17, kind, Gateway API + Traefik, Kustomize,
   Actions + Jenkins, Argo CD), "Ortak isimler ve port'lar (sözleşme — değiştirilemez)" tablosu, fonksiyonel kapsam ve
   "Bitti tanımı". Vurgula: product-service lokalde 8081, order-service 8082 portunda; container'da ikisi de 8080. GitHub kullanıcısı `muhammedali55`.

2. **Ajan tanımlarını göster.**

   ```bash
   ls .claude/agents/
   grep -n "^name:\|^tools:" .claude/agents/*.md
   ```

   Beklenen: 5 dosya (`docs-agent.md`, `order-service-agent.md`, `pipeline-agent.md`, `platform-agent.md`,
   `product-service-agent.md`) ve her birinin araç listesi. docs-agent'ta `Bash` olmadığını göster.

3. **Prompt dosyasını aç.** `prompts/ANA_AJAN_PROMPTLARI.md`'de 7 adım var. Claude Code'u repo kökünde başlat:

   ```bash
   claude
   ```

   Adım 1 prompt'unu yapıştır (plan, kod yok). Beklenen: ortak dosya listesi (sözleşme), bağımlılık tablosu,
   SPEC'teki belirsizlikler ve doğrulama yöntemi. **Kontrol:** İsimler ve port'lar SPEC ile uyumlu mu?

4. **Sözleşme.** Adım 2 prompt'unu yapıştır, ardından çıktıyı göster:

   ```bash
   grep -n "^  /api\|\"[0-9][0-9][0-9]\":" contracts/product-api.yaml
   ```

   Beklenen: `/api/products`, `/api/products/{id}`, `/api/products/{id}/decrease-stock` yolları ve
   200/201/400/404/409 kodları. Örnek veriler: Klavye 750/10, Mouse 350/25, Monitör 5200/5.

5. **Paralel ajanlar.** Adım 3 prompt'uyla dört ajan aynı anda çalışır. Ekranı geniş tut, başlangıç ve bitiş saatini not et.
   Beklenen: her ajan kendi raporunu verir (oluşturduğu dosyalar, doğrulama komutları, SPEC'ten sapmalar).

6. **Ortaya çıkanı say.**

   ```bash
   find services/*/src cluster k8s .github jenkins gitops contracts -type f | wc -l
   ```

   Beklenen: birkaç düzine dosya (kesin sayı değişebilir; `target/` klasörleri dışarıda bırakıldı).

7. **Review (Adım 4).** Ana ajana çapraz kontrolü yaptır. Prompt'taki üç düzeltme onaylandı ve **repoda uygulandı**.
   Her birini dosyasıyla göster:

   | # | Bulgu | Repodaki şu anki durum |
   | --- | --- | --- |
   | a | `APP_VERSION` imaja Docker build-arg ile gömülsün | `services/*/Dockerfile`: `ARG APP_VERSION=local` + `ENV APP_VERSION=${APP_VERSION}`. `ci.yml` `build-args: APP_VERSION=<kısa-sha>`, `Jenkinsfile` `--build-arg APP_VERSION=<tag>` geçiyor. ConfigMap'lerde `APP_VERSION` yok, overlay'de `replacements` yok. |
   | b | `/api/info` dışarıdan görünsün | `k8s/base/gateway/product-route.yaml` ve `order-route.yaml`'da `/info/products` ve `/info/orders` kuralları var (`URLRewrite` → `/api/info`). |
   | c | order-service Deployment'ında `replicas` olmasın | `k8s/base/order-service/deployment.yaml`'da `replicas` alanı yok; sayıyı HPA (2–5) yönetiyor. |

   Aynı review'dan çıkıp uygulanan ek düzeltmeler:
   - order-service readiness grubu artık product ile aynı: `readinessState,db,demoBroken` (önceden `db` yoktu).
   - `ProductClient`, sözleşmede olmayan bir 4xx yanıtını artık **502 Bad Gateway** olarak dışarı veriyor (önceden 500'e düşüyordu).
   - product-service'e de `ActuatorEndpointsTest` eklendi (`/api/info`, probe'lar, `DEMO_BROKEN=true` → readiness 503).
   - order-service imajı da sayısal kullanıcıyla çalışıyor (`USER 10001`).

8. **Review'un kendi hatası: yanlış bulgu.** Bu, bölümün en önemli anı. Review sırasında ana ajan şunu iddia etti:
   *"order-service'teki `ActuatorEndpointsTest`'ten 0 test koşuyor; `@Nested` + `@SpringBootTest` yapısı testleri gizliyor."*
   İddia **yanlıştı**. `@Nested` sınıflar koşuyordu; ancak Surefire raporu bu testleri sınıf adıyla değil
   **`@DisplayName` ile** ("Normal sürüm", "Bozuk sürüm (DEMO_BROKEN=true)") yazıyor. Ajan rapordaki satırları sınıf adıyla aradı,
   bulamayınca "koşmuyor" sonucuna vardı. Doğrulama komutu:

   ```bash
   cd services/order-service && ./mvnw -B verify | grep "Tests run:" ; cd ../..
   ```

   Beklenen (gerçek sayı): son özet satırında `Tests run: 17` (OrderServiceTest 5 + OrderControllerTest 7 + ProductClientTest 2 +
   ActuatorEndpointsTest 3). product-service'te aynı komutun sonucu `Tests run: 14`. Sınıf bazındaki satırların biçimi tahminidir;
   iç içe sınıflar display name'leriyle görünür.

   Ders: **AI'ın review'u da review ister.** Bulguyu kabul etmeden önce kanıtı kendi ekranında gör.

9. **Ölçüm (Adım 7).** Dosya ve satır sayılarını ana ajana çıkart, sonra salona sor: "Bunu elle yazsaydık ne kadar sürerdi?"

## 4. Bozalım ve düzeltelim: "bu test gerçekten koşuyor mu?"

8. adımdaki tartışmayı sözle değil deneyle bitirelim. Test gerçekten koşuyorsa, test ettiği kodu bozduğumuzda kırmızı yanmalı.

1. `services/order-service/src/main/java/com/muhammedalihoca/demo/order/health/DemoBrokenHealthIndicator.java` içinde

   ```java
   if (broken) {
   ```

   satırını geçici olarak şöyle değiştir:

   ```java
   if (false) { // DEMO: anahtar hiç çalışmasın
   ```

2. Testleri koş:

   ```bash
   cd services/order-service && ./mvnw -B verify ; cd ../..
   ```

   Beklenen (tahmini metin): `BUILD FAILURE`. Kırılan test "Bozuk sürüm (DEMO_BROKEN=true)" grubundaki
   **"Readiness DOWN (503) döner, liveness UP kalır"** testi: 503 bekleniyordu, 200 geldi. Yani `@Nested` testler gerçekten koşuyor.

3. Düzelt:

   ```bash
   git checkout -- services/order-service/src/main/java/com/muhammedalihoca/demo/order/health/DemoBrokenHealthIndicator.java
   cd services/order-service && ./mvnw -B verify | grep "Tests run:" ; cd ../..
   ```

   Beklenen: `BUILD SUCCESS`, toplamda `Tests run: 17`, `Failures: 0`.

Ders: "Test koşmuyor" iddiasını çürütmenin en kısa yolu, testin yakalaması gereken hatayı bilerek yapmaktır.

## 5. Sık sorulan sorular

**Neden tek ajan değil de beş ajan?**
Bağlam (context) sınırlı. Her ajan yalnızca kendi klasörüne ve kendi kurallarına odaklanınca daha tutarlı iş çıkarıyor.
Ayrıca birbirinden bağımsız işler aynı anda koşuyor ve toplam süre kısalıyor.

**Ajanlar birbirinin dosyasını bozarsa ne olur?**
Önlem üç katmanlı: `CLAUDE.md`'deki "yalnızca sana verilen klasörde çalış" kuralı, her ajan tanımındaki "Sınırlar"
bölümü ve review adımı. Bunlara ek olarak Git kullanıyoruz: her adımdan sonra commit atmak geri dönmeyi ucuzlatır.

**Sözleşme yanlışsa ne olur?**
`CLAUDE.md`: "`contracts/product-api.yaml` sözleşmedir; değiştirme. Yanlış olduğunu düşünüyorsan raporunda belirt."
Ajan sözleşmeyi kendi kafasına göre değiştirmiyor; kararı insan veriyor. Böylece iki servis sessizce ayrışmıyor.

**Ajan tanımları ile kod neden birebir aynı değil?**
Örneğin `platform-agent.md` hâlâ "ConfigMap (`APP_VERSION`, ...)" diyor ama koddaki ConfigMap'lerde `APP_VERSION` yok.
Ajan tanımı başlangıç talimatı. Review'da (Adım 4) insan onayıyla verilen kararlar kodu değiştirdi. SPEC'in istediği
davranış ("`APP_VERSION` env, yoksa `local`") korunuyor; değişen tek şey, değerin imaja build sırasında gömülmesi.

**AI'ın ürettiği koda güvenebilir miyim?**
Ürettiği koda değil, **doğruladığın** koda güven. Her ajanın tanımında bir "Doğrulama" bölümü var (`./mvnw -q verify`,
`docker build`, `kubectl kustomize ...`). Bunlar ve insan review'u olmadan kod "bitti" sayılmaz. 8. adım da gösteriyor ki
review'un kendisi de doğrulanmalı.
