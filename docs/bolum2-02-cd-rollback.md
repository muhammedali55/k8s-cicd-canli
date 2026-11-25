# Bölüm 2.02 — CD (Yol A) ve otomatik rollback: bozuk sürüm canlıya çıkamaz

> Atıf yapılan dosyalar: `.github/workflows/cd.yml`, `k8s/base/*/deployment.yaml`,
> `services/product-service/src/main/java/com/muhammedalihoca/demo/product/health/DemoBrokenHealthIndicator.java`,
> `services/product-service/src/test/java/com/muhammedalihoca/demo/product/ActuatorEndpointsTest.java`

> Bu bölümdeki rollout/rollback çıktıları bu sürümde **çalıştırılmadı**; kod okunarak yazıldı ve "tahmini" diye işaretlendi.

## 1. Neden?

CI bize test edilmiş bir imaj verdi, ama testler her şeyi yakalamaz. Asıl korku şu: yeni sürüm canlıya çıkar, açılmaz ya da
trafiğe hazır olmaz, kullanıcılar hata görür ve biri gece yarısı elle geri alır. Bu bölümde deploy'u otomatikleştiriyor ve
üç güvenlik ağını birlikte gösteriyoruz: **readiness probe** bozuk pod'a trafik göndermez, **`maxUnavailable: 0`** eski
pod'ları öldürtmez, **`rollout status` + `rollout undo`** bozuk sürümü otomatik geri alır.

## 2. Kavramlar

- **Push-tabanlı deploy (Yol A)** `[junior]`: Pipeline cluster'a kendisi bağlanıp `kubectl set image` çalıştırır.
  (Yol B'de, Bölüm 2.04, cluster Git'ten kendisi çeker.)
- **`workflow_run` tetikleyicisi** `[senior]`: `cd.yml`, `CI` adlı workflow bittiğinde çalışır. Job koşulu: CI başarılı +
  `push` olayından gelmiş + dal `main` + `vars.DEPLOY_MODE != 'gitops'`.
- **Self-hosted runner** `[senior]`: GitHub'ın bulut makineleri laptop'taki kind cluster'ına erişemez. Bu yüzden job, kendi
  makinende çalışan bir runner'da (`runs-on: self-hosted`) koşar. Kurulum adımları `cd.yml`'in başındaki yorumda.
- **Context sabitleme** `[senior]`: Her komut `--context kind-k8s-cicd` ile çalışıyor; yanlış cluster'a deploy etmeyi önlüyor.
- **Ortam değişkeniyle güvenli script** `[senior]`: Artifact indirme sonucu script'e `${{ }}` ile gömülmüyor,
  `env: DOWNLOAD_OUTCOME` olarak veriliyor (script enjeksiyonuna karşı alışkanlık; `ci.yml` ile aynı yaklaşım).
- **Rolling update + readiness** `[senior]`: `maxSurge: 1`, `maxUnavailable: 0`. Önce 1 yeni pod açılır; o pod
  `/actuator/health/readiness` için 200 dönmeden eski pod'lara dokunulmaz.
- **`DEMO_BROKEN`** `[junior]`: `true` ise `DemoBrokenHealthIndicator` DOWN döner. Bu bileşen yalnızca **readiness**
  grubunda; sonuç olarak pod yaşar (liveness UP) ama trafik almaz (readiness 503).
- **Spring özellik önceliği** `[senior]`: Aynı ayar birden çok yerden gelirse kazanan belli: Java sistem özelliği (`-D...`) >
  işletim sistemi ortam değişkeni (ConfigMap/`env`) > `application.yml`. Senaryo 2b bunu kullanıyor.
- **`kubectl rollout status --timeout=120s`** `[junior]`: Rollout bitene kadar bekler; süre dolarsa sıfırdan farklı bir kodla çıkar.
- **`kubectl rollout undo`** `[junior]`: Deployment'ı bir önceki revizyona (ReplicaSet) döndürür.
- **Revizyon geçmişi** `[senior]`: `kubectl rollout history`. Pod template'indeki her değişiklik (imaj, env…) yeni bir revizyondur.
- **`progressDeadlineSeconds`** `[architect]`: Deployment'ta tanımlı değil, varsayılan 600 sn. Kubernetes kendisi
  **geri almaz**, yalnızca `Progressing=False` der. Geri alma kararı pipeline'da (`cd.yml`'deki `undo`).
- **`concurrency: cd-dev`** `[senior]`: Aynı anda tek deploy çalışır; iki rollout birbirini ezmez.

## 3. Canlı akış: sağlıklı bir deploy

**Ön koşullar:** Cluster ayakta (Bölüm 1.03), GHCR paketleri public (Bölüm 2.01), repo variable `DEPLOY_MODE` tanımlı
**değil** (ya da `gitops` dışında bir değer), Argo CD uygulaması **kurulu değil** (yoksa selfHeal yaptığımızı geri alır).

1. **Self-hosted runner'ı kur ve başlat.** GitHub → repo → Settings → Actions → Runners → **New self-hosted runner**.
   İşletim sistemini seç, ekrandaki indirme ve `./config.sh --url ... --token ...` komutlarını çalıştır
   (token ekranda; dosyaya yazma). Sonra:

   ```bash
   ./run.sh
   ```

   Beklenen: `Listening for Jobs`. Runner'ın cluster'ı gördüğünü aynı kullanıcıyla, başka bir terminalde kontrol et:

   ```bash
   kubectl --context kind-k8s-cicd get nodes
   ```

   Beklenen: `k8s-cicd-control-plane   Ready`.

2. **İzleme döngülerini aç** (iki ayrı terminal; bölüm boyunca açık kalsın):

   ```bash
   # Terminal A: kullanıcı ne görüyor?
   while true; do curl -s -o /dev/null -w "%{http_code} " http://localhost/api/products; sleep 1; done
   ```

   ```bash
   # Terminal B: hangi sürüm yanıt veriyor?
   while true; do curl -s http://localhost/info/products; echo; sleep 2; done
   ```

   Beklenen: A'da `200 200 200 ...`, B'de `{"service":"product-service","version":"<sürüm>"}` (lokal kurulumda build-arg'la verdiğin değer ya da `local`).

3. **Değişiklik push et.** Örneğin Bölüm 2.01'deki gibi product-service'te zararsız bir yorum değişikliği yap; doğrudan `main`'e
   ya da PR merge ile gönder. Beklenen (tahmini): önce **CI**, ardından **CD** workflow'u çalışır. CD logunda
   `=== product-service → ghcr.io/muhammedali55/product-service:<sha> ===`, rollout çıktısı ve
   `product-service başarıyla güncellendi.` görünür.

4. **Cluster'da doğrula.**

   ```bash
   kubectl -n demo get deploy product-service -o jsonpath='{.spec.template.spec.containers[0].image}'; echo
   kubectl -n demo rollout history deploy/product-service
   ```

   Beklenen (tahmini): imaj yeni SHA ile; history'de bir revizyon daha. Terminal A boyunca `200` dışında kod görülmemeli.
   Terminal B bir süre eski ve yeni sürümü karışık gösterir (rolling update), sonra yalnızca `"version":"<sha>"` kalır.
   Sürüm imaja gömülü olduğu için `kubectl set image` sonrasında da `/info/products` doğru SHA'yı gösteriyor.

5. **Git ile cluster ayrıştı.**

   ```bash
   grep -A2 "product-service" k8s/overlays/dev/kustomization.yaml
   ```

   Beklenen: Git'te hâlâ `newTag: 0.1.0` yazıyor, cluster'da ise SHA çalışıyor. Biri `kubectl apply -k k8s/overlays/dev`
   ya da `./cluster/setup-cluster.sh` çalıştırırsa cluster `0.1.0`'a döner. Bu, Bölüm 2.04'teki GitOps'un motivasyonu.

## 4. Bozalım ve düzeltelim: `DEMO_BROKEN=true` ile bozuk sürüm

### Senaryo 1: elle, adım adım (2–3 dakika, pipeline gerekmez)

1. İzleme döngüleri çalışıyor olsun (A: `200 200 ...`).

2. Bozuk sürümü "deploy et": pod template'ine `DEMO_BROKEN=true` ekle.

   ```bash
   kubectl -n demo set env deploy/product-service DEMO_BROKEN=true
   ```

   `env` ile verilen değer, ConfigMap'ten (`envFrom`) gelen `DEMO_BROKEN: "false"`'u ezer. Pod template'i değiştiği için
   yeni bir rollout başlar.

3. Rollout'u bekle:

   ```bash
   kubectl -n demo rollout status deploy/product-service --timeout=60s
   ```

   Beklenen (tahmini): `Waiting for deployment "product-service" rollout to finish: 1 out of 2 new replicas have been updated...`
   ve 60 sn sonra `error: timed out waiting for the rollout to finish`.

4. Pod'lara bak:

   ```bash
   kubectl -n demo get pods -l app=product-service
   ```

   Beklenen (tahmini): **3 pod**. Eski iki pod `1/1 Running`, yeni pod `0/1 Running`. `maxSurge: 1` fazladan bir pod açtı,
   `maxUnavailable: 0` eskilere dokundurmadı. Terminal A hâlâ `200 200 ...`; kullanıcı hiçbir şey fark etmedi.

5. Neden hazır değil?

   ```bash
   kubectl -n demo describe pod -l app=product-service | grep -i "readiness probe failed"
   ```

   Beklenen (tahmini): `Readiness probe failed: HTTP probe failed with statuscode: 503`.
   Bozuk pod'a doğrudan bakmak istersen (pod adını 4. adımdan al):

   ```bash
   kubectl -n demo port-forward pod/<0/1-olan-pod> 18081:8080 &
   sleep 2 && curl -s http://localhost:18081/actuator/health/readiness; echo; kill %1
   ```

   Beklenen (tahmini): `{"status":"DOWN","components":{"db":{"status":"UP"},"demoBroken":{"status":"DOWN",...},"readinessState":{"status":"UP"}}}`.

6. Geri al:

   ```bash
   kubectl -n demo rollout undo deploy/product-service
   kubectl -n demo rollout status deploy/product-service
   kubectl -n demo get pods -l app=product-service
   kubectl -n demo rollout history deploy/product-service
   ```

   Beklenen (tahmini): `deployment.apps/product-service rolled back`, `successfully rolled out`, yine 2 pod (bozuk pod silindi)
   ve history'de yeni bir revizyon numarası. Terminal A baştan sona `200`.

### Senaryo 2: pipeline üzerinden (cd.yml'in otomatik undo'su)

Bozuk sürümü bir commit'le göndermek istiyoruz. İlk deneme "testler bunu yakalar mı?" sorusunu cevaplıyor.

**2a. Kodda bozma: CI yakalar.**

1. Geçici bir branch'te product-service'in `DemoBrokenHealthIndicator.java` dosyasında

   ```java
   if (broken) {
   ```

   satırını şuna çevir:

   ```java
   if (true) { // DEMO: bilerek bozuk sürüm
   ```

   Commit'le, push et, PR aç.

2. Beklenen (tahmini): **CI kırmızı.** product-service'teki `ActuatorEndpointsTest` → "Normal sürüm" grubundaki
   **"Liveness ve readiness UP döner, Prometheus metrikleri açıktır"** testi readiness için 200 bekliyor, 503 alıyor.
   CI başarısız olduğu için `cd.yml`'in koşulu (`conclusion == 'success'`) sağlanmıyor ve deploy hiç başlamıyor.
   Salona söyle: "Bu test review'dan sonra eklendi. Eklenmeden önce bu commit yeşil geçip canlıya giderdi."

3. PR'ı kapat, branch'i sil.

**2b. İmajda bozma: testler göremez, probe yakalar.**

Testler **kodu** test ediyor, **imajı** değil. Bozukluğu yalnızca imaja koyalım:

1. Geçici bir branch'te `services/product-service/Dockerfile`'ın son satırını

   ```dockerfile
   ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
   ```

   şuna çevir:

   ```dockerfile
   ENTRYPOINT ["java", "-DDEMO_BROKEN=true", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
   ```

   `-DDEMO_BROKEN=true` bir Java sistem özelliği. Spring'de sistem özelliği ortam değişkeninden önceliklidir; yani ConfigMap'teki
   `DEMO_BROKEN: "false"` bunu ezemez. Commit, PR, merge.

2. Beklenen **CI** (tahmini): **yeşil.** `./mvnw verify` Dockerfile'ı hiç kullanmıyor; ci.yml'in `paths` filtresi
   (`services/**`) Dockerfile değişikliğini görüp imajı build ediyor ve push ediyor.

3. Beklenen **CD** logu (tahmini sıra):

   ```
   === product-service → ghcr.io/muhammedali55/product-service:<sha> ===
   deployment.apps/product-service image updated
   Waiting for deployment "product-service" rollout to finish: 1 out of 2 new replicas have been updated...
   error: timed out waiting for the rollout to finish
   Error: product-service rollout başarısız, geri alınıyor (rollout undo).
   deployment.apps/product-service rolled back
   ```

   Job **kırmızı** biter (`exit 1`) ve Actions özetinde kırmızı bir `::error::` notu görünür. Terminal A boyunca `200`;
   Terminal B hiçbir zaman yeni SHA'yı göstermez, çünkü bozuk pod hiç trafik almadı.

4. Düzelt: merge commit'ini geri al ve push et.

   ```bash
   git checkout main && git pull
   git revert -m 1 --no-edit HEAD     # HEAD bir merge commit'i ise; düz commit ise: git revert --no-edit HEAD
   git push
   ```

   Beklenen (tahmini): CI → CD yeşil; yeni SHA sağlıklı şekilde rollout olur ve Terminal B yeni SHA'yı gösterir.

Ders: Testler ve probe'lar farklı katmanları korur. 2a'yı testler yakaladı, 2b'yi yalnızca çalışma anındaki probe yakalayabildi.

### Yanlış yol: ConfigMap'i düzenlemek

`kubectl -n demo edit configmap product-service-config` ile `DEMO_BROKEN: "true"` yazarsan **hiçbir şey olmaz**: ConfigMap
değişikliği çalışan pod'ları yeniden başlatmaz. `rollout restart` yaparsan yeni pod'lar bozuk gelir; `rollout undo` o an
kurtarıyor gibi görünür, ama ConfigMap hâlâ `true` olduğu için bir pod yeniden başladığı anda bozukluk geri gelir.
Ders: **değişiklik pod template'inde (ya da imajda) olmalı ki revizyonla birlikte geri alınabilsin.**
Bitirince `DEMO_BROKEN`'ı tekrar `"false"` yapmayı unutma (ya da `kubectl apply -k k8s/overlays/dev` çalıştır).

## 5. Sık sorulan sorular

**Kubernetes bozuk sürümü kendisi geri almıyor mu?**
Hayır. Kubernetes yalnızca "ilerlemiyor" der (`progressDeadlineSeconds`, varsayılan 10 dk, sonra `ProgressDeadlineExceeded`).
Eski pod'ları korur ama geri alma kararını vermez; o kararı `cd.yml`'deki `rollout status` + `undo` veriyor.
Argo Rollouts / Flagger gibi araçlar bunu analizle otomatik yapabilir, ama kapsam dışı.

**`maxUnavailable: 0` olmasaydı ne olurdu?**
Varsayılan %25 ile 2 replikada (aşağı yuvarlanınca 0) aynı davranırdı. Ama replika sayısı arttıkça Kubernetes eski
pod'lardan bazılarını yeni pod hazır olmadan kapatabilir ve kapasite düşer. Biz açıkça 0 yazarak niyeti belgeliyoruz.

**order-service'te aynı senaryo farklı mı işler?**
Mantık aynı, ama order-service'in replika sayısını HPA yönetiyor (2–5). Pod sayısı o an kaç ise rollout ona göre ilerler;
`maxSurge: 1` yine bir fazla pod açar. Sayıları anlatmak kolay olsun diye canlıda product-service'i (sabit 2 replika) kullanıyoruz.

**İki servis aynı anda değişip biri bozuk çıkarsa?**
`cd.yml` servisleri sırayla dolaşır, bozuk olanı geri alır, sağlam olanı bırakır ve sonunda job'u kırmızı bitirir
(`failed=1`). Servisler arasında bir uyumsuzluk (ör. API değişikliği) varsa bu yarım durum dikkat ister.

**Self-hosted runner public repo'da güvenli mi?**
Riskli: başkasının PR'ı senin makinende kod çalıştırabilir. `cd.yml` PR'da değil, yalnızca `workflow_run` ile ve
`main` push'unda çalışıyor. Yine de `cd.yml`'deki yorumun dediği gibi dış katkıcılar için onay şartını aç.
