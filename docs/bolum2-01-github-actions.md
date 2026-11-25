# Bölüm 2.01 — GitHub Actions ile CI: test → build → GHCR'a push

> Atıf yapılan dosya: `.github/workflows/ci.yml`

## 1. Neden?

Testleri unutmadan her commit'te çalıştırmak, imajı herkesin laptop'unda ayrı ayrı değil tek bir yerde build etmek ve
her imajı hangi commit'ten geldiği belli bir etiketle saklamak: CI'ın çözdüğü acı bu. Hatalı kod `main`'e girmeden PR'da
kırmızı yanar; `main`'e giren her commit için de tekrar üretilebilir bir imaj oluşur.

## 2. Kavramlar

- **Workflow / job / step** `[junior]`: `ci.yml` bir workflow; içinde üç job var: `changes`, `build`, `gitops-update`.
  Her job temiz bir sanal makinede (`runs-on: ubuntu-latest`) koşar ve adımlarını sırayla çalıştırır.
- **Tetikleyiciler (`on:`)** `[junior]`: `pull_request` → yalnızca test; `main`'e `push` → test + build + push.
  `paths` filtresi sayesinde workflow yalnızca `services/**` ya da `ci.yml` değişince çalışır.
- **`needs` ve job çıktıları** `[senior]`: `changes` job'u `services`, `image_prefix` ve `tag` çıktılarını üretir; sonraki job'lar
  bunları `needs.changes.outputs.*` ile okur.
- **Dinamik matrix** `[senior]`: `dorny/paths-filter` değişen servisleri bir JSON dizisi olarak veriyor (ör. `["product-service"]`),
  `matrix.service` bu diziden besleniyor. İki servis değiştiyse iki paralel kopya koşar. `fail-fast: false`: biri kırılsa diğeri durmaz.
- **Koşullu adım (`if: github.event_name == 'push'`)** `[junior]`: PR'da imaj üretilmez, yalnızca test koşar.
- **`GITHUB_TOKEN` ve `permissions`** `[senior]`: GHCR'a girişte ek bir secret yok; GitHub her çalıştırmaya kısa ömürlü bir token
  veriyor. En az yetki ilkesi: workflow varsayılanı `contents: read`; yalnızca `build` job'u `packages: write` alıyor.
- **Etiket = kısa git SHA** `[junior]`: `tag=${GITHUB_SHA::7}`. "Hangi commit canlıda?" sorusunun cevabı imaj adında yazıyor.
- **İmaj öneki küçük harf** `[junior]`: `ghcr.io/${GITHUB_REPOSITORY_OWNER,,}`. GHCR büyük harf kabul etmiyor; fork'ta
  önek otomatik olarak fork sahibinin adı oluyor.
- **Build-arg ile sürüm gömme** `[senior]`: `build-args: APP_VERSION=<tag>`. Dockerfile'daki `ARG APP_VERSION` bu değeri imaja
  yazıyor; deploy sonrası `curl http://localhost/info/products` bu SHA'yı gösteriyor.
- **Önbellekler** `[senior]`: Maven için `setup-java`'nın `cache: maven` ayarı (anahtar: servisin `pom.xml`'i).
  Docker katmanları için `cache-from/cache-to: type=gha` (servis başına ayrı `scope`).
- **`concurrency`** `[senior]`: Aynı PR'a yeni commit gelirse eski çalışma iptal ediliyor; `main`'de her commit build ediliyor.
- **Artifact ile workflow'lar arası haber** `[architect]`: `build` job'u tam imaj adını `deploy-info/<servis>` dosyasına yazıp
  `image-<servis>` artifact'i olarak yüklüyor. `cd.yml` (Bölüm 2.02) bunu `workflow_run` üzerinden indiriyor.
- **OCI etiketleri** `[senior]`: `org.opencontainers.image.source` GHCR paketini repo'ya bağlıyor,
  `org.opencontainers.image.revision` imajın tam commit SHA'sını taşıyor.

## 3. Canlı akış

> Bu bölümdeki GitHub tarafı çıktıları bu sürümde çalıştırılmadı; workflow dosyası okunarak yazıldı (tahmini).

1. **Workflow'u yukarıdan aşağı oku.** Ekranda `.github/workflows/ci.yml` açık olsun. Sıra: `on:` → `permissions` → `concurrency` →
   `changes` → `build` (özellikle `build-args` satırı) → `gitops-update` (bu sonuncusu Bölüm 2.04'te; şimdilik "varsayılan kapalı" de).

2. **Önce lokalde aynı testi koş** (CI'ın yaptığıyla birebir aynı komut):

   ```bash
   cd services/product-service && ./mvnw -B verify | grep "Tests run:" ; cd ../..
   ```

   Beklenen: son satırda `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` ve `BUILD SUCCESS`. (order-service'te 17.)

3. **Bir PR aç; yalnızca product-service değişsin.**

   ```bash
   git checkout -b demo/ci-pr
   # Küçük, zararsız bir değişiklik: InfoController'daki yorum satırına bir kelime ekle
   $EDITOR services/product-service/src/main/java/com/muhammedalihoca/demo/product/controller/InfoController.java
   git commit -am "demo: product-service küçük değişiklik"
   git push -u origin demo/ci-pr
   ```

   GitHub'da **Compare & pull request** ile PR'ı aç. Beklenen: Actions sekmesinde **CI** çalışır;
   `Değişen servisleri bul` job'unun ardından **yalnızca** `product-service` adlı build job'u gelir. `Docker Buildx kur`,
   `GHCR'a giriş yap` ve `İmajı build et ve push et` adımları **atlandı (skipped)** görünür; PR'da imaj üretilmez.
   `gitops-update` job'u da atlanır.

4. **PR'ı merge et.** Beklenen: `main`'e push olduğu için CI tekrar koşar ve bu kez tüm adımlar çalışır.
   Logda `ghcr.io/muhammedali55/product-service:<7-haneli-sha>` push edilir, build-arg olarak `APP_VERSION=<7-haneli-sha>` görünür
   ve `image-product-service` artifact'i yüklenir.

5. **İmajı gör.** GitHub → profil → **Packages** → `product-service`. Tag listesinde kısa SHA var.
   İlk push'ta paket **private** oluşur: **Package settings → Change visibility → Public** (kind'ın çekebilmesi için şart).

   ```bash
   git checkout main && git pull
   TAG=$(git rev-parse --short=7 HEAD)
   docker pull ghcr.io/muhammedali55/product-service:${TAG}
   docker image inspect ghcr.io/muhammedali55/product-service:${TAG} --format '{{.Config.Env}}'
   ```

   Beklenen: imaj iner ve `Env` listesinde `APP_VERSION=<sha>` görünür. (Paket hâlâ private ise `denied` / `unauthorized` hatası alırsın.)
   Not: `--short=7` merge commit'inin SHA'sını verir; CI de aynı commit için çalıştığı için tag'ler eşleşir.

6. **Önbelleği göster.** Aynı servis için ikinci bir küçük commit at. Beklenen: `Java 21 kur` adımında
   "Cache restored" yazar, Docker build'de katmanların çoğu `CACHED` olur ve toplam süre ilk çalışmadan belirgin şekilde kısalır.

7. **Path filtresini göster.** Yalnızca `docs/` altında bir değişiklik push et. Beklenen: CI **hiç tetiklenmez**
   (`paths` listesinde `docs/` yok).

## 4. Bozalım ve düzeltelim: testi kıran PR

1. Stok kuralını boz. `services/product-service/src/main/java/com/muhammedalihoca/demo/product/domain/Product.java` içindeki

   ```java
   if (quantity > stock) {
   ```

   satırını şuna çevir (bir fazla satmaya izin veren "küçük" bir hata):

   ```java
   if (quantity > stock + 1) {
   ```

2. Önce lokalde gör:

   ```bash
   cd services/product-service && ./mvnw -q test; cd ../..
   ```

   Beklenen (tahmini): `ProductServiceTest` içindeki **"Stok yetersizse hata fırlar ve stok değişmez"** testi kırılır.
   Hangi testlerin kırıldığını canlıda oku; stok kuralına dokunan controller testleri de etkilenebilir.

3. Branch'e push edip PR aç. Beklenen: CI kırmızı, `Test (./mvnw verify)` adımında `BUILD FAILURE`.
   İmaj build/push adımları zaten PR'da çalışmıyor. Branch protection'da "CI geçmeli" kuralı açıksa merge butonu kilitli.

4. Düzelt: değişikliği geri al (`git checkout -- <dosya>` ya da `git revert`) ve push et → CI yeşil.

Ders: Test, "bir fazla satma" gibi göze batmayan bir hatayı `main`'e girmeden yakaladı. İmaj hiç üretilmedi, cluster'a hiçbir şey gitmedi.

## 5. Sık sorulan sorular

**Neden `latest` değil de SHA? Okunması zor.**
SHA imajı commit'e bağlıyor: rollback "bir önceki SHA'ya dön" kadar net oluyor; iki ortamın aynı kodu çalıştırıp çalıştırmadığı
imaj adına bakınca anlaşılıyor. İnsan dostu bir sürüm (`v1.2.0`) istersen Git tag'iyle ek bir etiket de basılabilir.

**GHCR'a girmek için neden PAT ya da secret tanımlamadık?**
`GITHUB_TOKEN` her çalıştırmada otomatik üretiliyor ve çalıştırma bitince geçersiz oluyor. `permissions: packages: write`
ile paket yazma yetkisini yalnızca bu job'a verdik. Dosyaya yazılmış hiçbir token yok.

**Bir servis değişmediyse neden build edilmiyor? İki servisi her seferinde build etsek daha basit olmaz mı?**
Basit olurdu ama yavaş ve gereksiz: değişmeyen servise yeni SHA'lı bir imaj basmak, "ne değişti?" sorusunu bulanıklaştırır.
`ci.yml`'in kendisi değişince iki servis birden build ediliyor (iki filtrede de `.github/workflows/ci.yml` var).

**`./mvnw: Permission denied` hatası alırsam?**
`mvnw` dosyası Git'e çalıştırılabilir biti olmadan girmiş demektir (genelde Windows'tan commit'lenince olur).
Düzeltme: `git update-index --chmod=+x services/<servis>/mvnw` ve commit.

**Yabancı birinden gelen PR imajımızı push edebilir mi?**
Hayır. Fork'tan gelen PR'larda `GITHUB_TOKEN` salt-okunur, üstelik PR'da push adımları `if` ile zaten kapalı.
