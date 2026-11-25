# Bölüm 2.03 — Jenkins: aynı pipeline, farklı motor

> Atıf yapılan dosyalar: `jenkins/Jenkinsfile`, `jenkins/docker-compose.yml`, `jenkins/Dockerfile`,
> `jenkins/plugins.txt`, `jenkins/README.md`

> Bu bölümdeki Jenkins çıktıları bu sürümde **çalıştırılmadı**; dosyalar okunarak yazıldı ve "tahmini" diye işaretlendi.

## 1. Neden?

Birçok şirkette CI/CD hâlâ (ve daha uzun süre) Jenkins'te: kod GitHub dışında olabilir, ağ kapalı olabilir, yıllardır biriken
job'lar vardır. Bu bölümün amacı yeni bir şey öğretmek değil, **aynı mantığın** (test → build → push → deploy → gerekirse
rollback) başka bir motorda nasıl ifade edildiğini göstermek. Kavramı anlayan aracı değiştirebilir.

## 2. Kavramlar

- **Controller / agent** `[junior]`: Jenkins sunucusu (controller) işleri dağıtır; işi yapan makine agent'tır.
  Bizde tek node var: `agent any` → Jenkins'in kendisi.
- **Declarative pipeline** `[junior]`: `pipeline { stages { stage('...') { steps { ... } } } }` yapısı.
  Stage'ler: **Checkout → Test → Build Image → Push → Deploy**. Her stage'in başında Actions'taki karşılığını söyleyen bir yorum var.
- **`when` koşulları** `[junior]`: Actions'taki `if:` karşılığı. Build/Push/Deploy yalnızca `main`'de ve değişen servis varsa çalışır.
- **Credentials** `[senior]`: Gizli bilgi dosyada değil, Jenkins'in Credentials deposunda duruyor:
  `ghcr-creds` (Username with password: GitHub kullanıcı adı + `write:packages` PAT) ve
  `kubeconfig` (Secret file: `kind get kubeconfig --internal` çıktısı). Loglarda maskeleniyorlar.
- **Eklentiler** `[senior]`: `plugins.txt` ile imaja gömülü: `workflow-aggregator`, `git`, `github` (webhook +
  `githubPush()`), `docker-workflow` (`docker.build`, `docker.withRegistry`), `credentials-binding`, `kubernetes-cli`
  (`withKubeConfig`), `pipeline-stage-view`.
- **Build-arg ile sürüm** `[senior]`: `docker.build(..., "--build-arg APP_VERSION=${env.TAG} services/${svc}")`. ci.yml'deki
  `build-args` ile aynı; Jenkins'in build ettiği imaj da `/info/products`'ta kendi SHA'sını gösterir.
- **Docker socket bağlama** `[architect]`: `/var/run/docker.sock` Jenkins container'ına bağlı ve container `user: root` ile çalışıyor.
  Pipeline'ın `docker build`'i aslında **host'un** Docker daemon'unda koşuyor. Bu bir eğitim kolaylığı: socket'e erişen her şey
  host'ta root'a eşdeğer (compose dosyasındaki uyarı).
- **Değişen servis tespiti** `[senior]`: `changedServices()`, `GIT_PREVIOUS_SUCCESSFUL_COMMIT` ile mevcut commit arasındaki
  `git diff`'e bakıyor. İlk build'de ya da `jenkins/Jenkinsfile` değiştiyse iki servis birden derleniyor.
- **Ağ: Jenkins → kind** `[architect]`: Jenkins container'ı için `127.0.0.1` kendisi demek. kind API sunucusuna ulaşmak için
  Jenkins `kind` Docker ağına bağlanıyor ve kubeconfig'in **iç** adresini (`https://k8s-cicd-control-plane:6443`) kullanıyor.
- **kubectl sürümü** `[senior]`: `jenkins/Dockerfile` `KUBECTL_VERSION=v1.37.0` kuruyor; cluster da v1.37.0 (desteklenen aralıkta).
- **`DEPLOY_MODE=gitops`** `[senior]`: Jenkins global ortam değişkeni. Tanımlıysa Deploy stage'i atlanıyor (Argo CD ile çakışmasın diye).

## 3. GitHub Actions ile karşılaştırma

| Konu | GitHub Actions (`ci.yml` + `cd.yml`) | Jenkins (`Jenkinsfile`) |
| --- | --- | --- |
| Tanım dosyası | YAML, iki workflow | Groovy tabanlı declarative pipeline, tek dosya |
| Çalıştığı yer | CI: GitHub'ın bulut runner'ı; CD: self-hosted runner | Kendi kurduğumuz Jenkins container'ı (`docker compose`) |
| Tetikleme | `on: push / pull_request` + `paths` | `githubPush()` webhook (tünel gerekir) ya da Poll SCM |
| Değişen servis | `dorny/paths-filter` | `changedServices()` + `git diff` |
| İki servis | Matrix, **paralel**, ayrı makinelerde | `for` döngüsü, **sırayla**, aynı makinede (ortak Maven önbelleği) |
| Maven önbelleği | `setup-java` `cache: maven` | `MAVEN_OPTS=-Dmaven.repo.local=/var/jenkins_home/.m2/repository` (kalıcı volume) |
| Docker build | Buildx + `build-push-action`, GHA katman önbelleği | `docker.build(...)` host'un Docker daemon'unda |
| Sürümü imaja gömme | `build-args: APP_VERSION=<tag>` | `--build-arg APP_VERSION=${env.TAG}` |
| İmaj öneki | `ghcr.io/${GITHUB_REPOSITORY_OWNER,,}`, otomatik | `IMAGE_PREFIX = 'ghcr.io/muhammedali55'`, sabit (fork'ta elle değişir) |
| Registry girişi | Otomatik `GITHUB_TOKEN`, secret yok | `ghcr-creds` credential'ı (PAT) |
| Tag | `${GITHUB_SHA::7}` | `scmVars.GIT_COMMIT.take(7)` |
| Deploy | Ayrı workflow (`cd.yml`), `workflow_run` + artifact | Aynı pipeline'da `Deploy` stage'i, `withKubeConfig` |
| Rollback | `rollout status` başarısızsa `rollout undo`, job kırmızı | Aynısı; `error(...)` ile build kırmızı |
| Eşzamanlılık | `concurrency` grupları | `disableConcurrentBuilds()` |
| PR'da test | Var (`pull_request`) | Bu job `*/main`'i izliyor; PR desteği için Multibranch Pipeline gerekir |
| GitOps modu | `vars.DEPLOY_MODE == 'gitops'` → `gitops-update` job'u tag'i Git'e yazar | `DEPLOY_MODE=gitops` → Deploy atlanır; tag'i Git'e yazan stage **bilinçli olarak yok** |
| Bakım yükü | GitHub yönetir | Sunucu, eklenti güncellemeleri ve yedekleme bizde |
| Arayüz | Actions sekmesi | Stage View, konsol logu |

## 4. Canlı akış

`jenkins/README.md` bunu adım adım anlatıyor; aşağısı canlı için hızlı akış.

1. **Jenkins'i başlat.**

   ```bash
   cd jenkins
   docker compose up -d --build
   cd ..
   ```

   Beklenen (tahmini): ilk build birkaç dakika sürer (Docker CLI, kubectl, eklentiler), sonra `Container jenkins Started`.

2. **İlk şifre ve kurulum sihirbazı.**

   ```bash
   docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
   ```

   `http://localhost:8080` → şifreyi yapıştır → **Select plugins to install → None → Install** (eklentiler zaten kurulu)
   → admin kullanıcısı oluştur.

3. **Jenkins'i kind'a bağla** (cluster ayakta olmalı).

   ```bash
   docker network connect kind jenkins
   kind get kubeconfig --name k8s-cicd --internal > ~/kubeconfig-k8s-cicd-internal
   docker cp ~/kubeconfig-k8s-cicd-internal jenkins:/tmp/kc
   docker exec jenkins kubectl --kubeconfig /tmp/kc get nodes
   docker exec jenkins rm /tmp/kc
   ```

   Beklenen (tahmini): `k8s-cicd-control-plane   Ready`. kubeconfig dosyası repo **dışında** (`~`) duruyor; içinde cluster sertifikaları var.

4. **Credential'ları ekle:** Manage Jenkins → Credentials → System → Global credentials → Add Credentials:
   `ghcr-creds` (Username with password) ve `kubeconfig` (Secret file → `~/kubeconfig-k8s-cicd-internal`). ID'leri birebir böyle yaz.

5. **Job'u oluştur:** New Item → `k8s-cicd-canli` → Pipeline → "Pipeline script from SCM" → Git →
   `https://github.com/muhammedali55/k8s-cicd-canli.git`, Branch `*/main`, Script Path `jenkins/Jenkinsfile` → Save → **Build Now**.

   Beklenen konsol özeti (tahmini): `Dal: origin/main | Tag: <sha7> | Değişen servisler: product-service,order-service`
   (ilk build'de karşılaştıracak commit yok, bu yüzden ikisi birden). Ardından sırayla iki `./mvnw -B verify`,
   `--build-arg APP_VERSION=<sha7>` içeren iki `docker build`, iki push, iki `kubectl set image` + `rollout status`.
   Stage View'de 5 yeşil kutu.

6. **Sürümü dışarıdan doğrula.**

   ```bash
   curl -s http://localhost/info/products
   curl -s http://localhost/info/orders
   ```

   Beklenen (tahmini): iki servis de `"version":"<sha7>"` gösterir; Jenkins'in konsolundaki tag ile aynı.

7. **İkinci build'de değişen servis mantığını göster.** Yalnızca order-service'te bir yorumu değiştirip push et, sonra Build Now.
   Beklenen (tahmini): `Değişen servisler: order-service`.

8. **(İsteğe bağlı) Webhook.** `ngrok http 8080` ya da smee.io ile bir tünel aç, GitHub → Settings → Webhooks →
   `https://<tünel>/github-webhook/`. Push sonrası build otomatik başlar. Tünel yoksa job'da Poll SCM (`H/2 * * * *`) kullan.

> **Actions ve Jenkins aynı anda açıksa:** ikisi de aynı commit için deploy eder (aynı tag'le GHCR'a iki kez push, iki kez
> `set image`). Canlıda birini gösterirken diğerinin deploy'unu kapat: Actions'ta self-hosted runner'ı durdur
> ya da Jenkins'te `DEPLOY_MODE=gitops` tanımla.

## 5. Bozalım ve düzeltelim: "Jenkins cluster'ı göremiyor"

1. Jenkins container'ını kind ağından çıkar (ya da cluster'ı `reset.sh` ile yeniden kurduğunu ve ağı unuttuğunu varsay):

   ```bash
   docker network disconnect kind jenkins
   ```

2. Bir servise küçük bir değişiklik push et ve Build Now'a bas (değişen servis yoksa Deploy zaten atlanır).
   Beklenen (tahmini): Test, Build Image ve Push yeşil; **Deploy kırmızı**. Konsolda şuna benzer bir hata:
   `dial tcp: lookup k8s-cicd-control-plane ... no such host`.
   Dikkat: Jenkinsfile'da `set image` adımı `sh` ile doğrudan çalışıyor; o adım hata verince stage orada kırılıyor ve
   `rollout undo` hiç denenmiyor. Cluster'a zaten ulaşılamadığı için geri alınacak bir şey de yok.

3. Düzelt:

   ```bash
   docker network connect kind jenkins
   ```

   `reset.sh` ile cluster yeniden kurulduysa sertifikalar da değişir: kubeconfig'i yeniden üret ve `kubeconfig`
   credential'ını güncelle (README, 3. ve 4. adım). Build Now → Deploy yeşil.

Ders: Pipeline'ın "çalıştığı yer" ile "deploy ettiği yer" arasındaki ağ, CI/CD tasarımının bir parçası. Actions'ta aynı
sorunu self-hosted runner ile çözmüştük.

## 6. Sık sorulan sorular

**Jenkins mi, Actions mı?**
Kod GitHub'daysa ve özel bir ağ gereksinimi yoksa Actions bakım yükü olmadan başlar. Jenkins; şirket içi ağ, farklı SCM'ler,
özel donanım ya da mevcut bir yatırım varsa mantıklı. Kavramlar (stage, credential, koşul, artifact) birebir taşınıyor.

**Neden Jenkins'te servisler paralel değil?**
Tek agent ve ortak bir Maven önbelleği var; iki `./mvnw` aynı anda aynı `.m2` klasörüne yazarsa çakışabilir.
`parallel { }` bloğu ve ayrı agent'larla paralelleştirmek mümkün; demo için sadelik seçildi.

**GitOps modunda Jenkins ne yapar?**
`DEPLOY_MODE=gitops` iken Test, Build Image ve Push çalışıyor, Deploy atlanıyor. Jenkinsfile'da yeni tag'i
`kustomization.yaml`'a yazan bir stage **bilinçli olarak eklenmedi**; Yol B'de tag'i Git'e yazan tek yer Actions'taki
`gitops-update` job'u. Jenkins'e de eklemek istersen ona repo'ya yazma yetkili bir credential vermen gerekir; bu da ayrıca
yönetilmesi gereken bir gizli bilgi demek.

**Fork'ta çalışıyorum; Jenkins'te neyi değiştirmeliyim?**
`Jenkinsfile`'daki `IMAGE_PREFIX` sabit (`ghcr.io/muhammedali55`). Senin PAT'in yalnızca kendi hesabının paketlerine yazabilir;
`ghcr.io/<senin-adın>` (küçük harf) yap. Job'daki repo URL'sini de kendi fork'una çevir.

**`githubPush()` tetiklemiyor, neden?**
GitHub laptop'taki `localhost:8080`'e ulaşamaz; tünel (ngrok/smee) şart. Webhook URL'sinin sonundaki `/` önemli
(`/github-webhook/`). GitHub'daki webhook sayfasında "Recent Deliveries" yeşil olmalı.
