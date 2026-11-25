# Jenkins — lokal kurulum, adım adım

Bu klasör, GitHub Actions pipeline'ının (`.github/workflows/ci.yml` + `cd.yml`) Jenkins'teki
karşılığını çalıştırır: **Checkout → Test → Build Image → Push → Deploy**.

| Dosya | Ne işe yarar |
| --- | --- |
| `docker-compose.yml` | Jenkins container'ı: port 8080, kalıcı volume, Docker socket |
| `Dockerfile` | Jenkins LTS (JDK 21) + Docker CLI + kubectl + eklentiler |
| `plugins.txt` | Önceden kurulan eklentiler |
| `Jenkinsfile` | Pipeline'ın kendisi |

> **Önemli:** Hiçbir token veya şifre bu repoya yazılmaz. Hepsi Jenkins'in Credentials ekranına girilir.

## 1. Jenkins'i başlat

```bash
cd jenkins
docker compose up -d --build
```

İlk build birkaç dakika sürer (Docker CLI, kubectl ve eklentiler imaja kurulur).

## 2. İlk açılış ve admin şifresi

1. Başlangıç şifresini al:
   ```bash
   docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
   ```
2. Tarayıcıda `http://localhost:8080` aç, şifreyi yapıştır.
3. "Customize Jenkins" ekranında **Select plugins to install** → üstten **None** → **Install**.
   (Gerekli eklentiler zaten `plugins.txt` ile kurulu.)
4. Kendi admin kullanıcını oluştur, "Jenkins URL" olarak `http://localhost:8080/` bırak.

## 3. Jenkins'i kind cluster'ına bağla

Jenkins bir container içinde çalışıyor. Onun için `127.0.0.1` kendisi demek; laptop'taki
kind API sunucusuna `127.0.0.1:xxxxx` üzerinden **ulaşamaz**. Çözüm: Jenkins'i kind'in Docker
ağına bağlayıp cluster'ın **iç** adresini kullanmak.

```bash
# (Cluster kurulu olmalı: ./cluster/setup-cluster.sh)
# 1) Jenkins container'ını "kind" ağına bağla
docker network connect kind jenkins

# 2) İç adresli kubeconfig üret (sunucu: https://k8s-cicd-control-plane:6443)
#    Dosyayı repo DIŞINA yaz; içinde cluster sertifikaları var.
kind get kubeconfig --name k8s-cicd --internal > ~/kubeconfig-k8s-cicd-internal

# 3) Kontrol: Jenkins içinden cluster'a erişebiliyor muyuz?
docker cp ~/kubeconfig-k8s-cicd-internal jenkins:/tmp/kc
docker exec jenkins kubectl --kubeconfig /tmp/kc get nodes
docker exec jenkins rm /tmp/kc
```

Cluster'ı sıfırdan kurarsan (`reset.sh`) ağ bağlantısını ve kubeconfig credential'ını yenile.

## 4. Credential'ları ekle

**Manage Jenkins → Credentials → System → Global credentials → Add Credentials**

| ID | Tür | İçerik |
| --- | --- | --- |
| `ghcr-creds` | Username with password | Username: GitHub kullanıcı adın. Password: GitHub → Settings → Developer settings → Personal access tokens (classic) → `write:packages` yetkili token |
| `kubeconfig` | Secret file | 3. adımda ürettiğin `~/kubeconfig-k8s-cicd-internal` dosyası |

ID'leri birebir böyle yaz; `Jenkinsfile` bu isimlerle arar.

## 5. Pipeline job'unu oluştur

1. **New Item** → ad: `k8s-cicd-canli` → **Pipeline** → OK.
2. **Triggers** bölümünde **GitHub hook trigger for GITScm polling** işaretle.
3. **Pipeline** bölümü:
   - Definition: **Pipeline script from SCM**
   - SCM: **Git**, Repository URL: `https://github.com/muhammedali55/k8s-cicd-canli.git`
   - Branch: `*/main`
   - Script Path: `jenkins/Jenkinsfile`
4. **Save** → **Build Now**. İlk build'de karşılaştıracak eski commit olmadığı için iki servis de derlenir.

## 6. GitHub webhook (push gelince otomatik build)

GitHub, laptop'taki `localhost:8080`'e doğrudan ulaşamaz. Bir tünel gerekir:

1. Tünel aç (birini seç):
   - `ngrok http 8080` → ekranda `https://xxxx.ngrok-free.app` adresi çıkar.
   - veya https://smee.io → "Start a new channel", ardından `npx smee-client --url <smee-adresi> --target http://localhost:8080/github-webhook/`
2. GitHub'da repo → **Settings → Webhooks → Add webhook**
   - Payload URL: `https://<tünel-adresi>/github-webhook/` (sondaki `/` önemli; smee kullanıyorsan smee adresi)
   - Content type: `application/json`
   - Events: **Just the push event**
3. Bir commit push et; Jenkins'te build otomatik başlamalı. GitHub'daki webhook sayfasında
   "Recent Deliveries" yeşil tik göstermeli.

Tünel kurmak istemezsen job'da **Poll SCM** (`H/2 * * * *`) işaretleyebilirsin; Jenkins 2 dakikada bir repo'yu kontrol eder.

## 7. GHCR paketini public yap

GHCR paketleri ilk push'ta **private** oluşur; kind imajı çekemez (`ImagePullBackOff`).
GitHub → profil → **Packages** → `product-service` (ve `order-service`) → **Package settings** →
**Change visibility → Public**. Bunu her paket için bir kez yapman yeterli.

## 8. Argo CD ile birlikte kullanma (önemli)

Argo CD (`gitops/`) `selfHeal: true` ile çalışır. Jenkins'in Deploy stage'i `kubectl set image`
yaptığında Argo CD bunu Git'ten sapma sayar ve **geri alır**. İkisini aynı anda açma:

- Argo CD kullanacaksan: **Manage Jenkins → System → Global properties → Environment variables** →
  `DEPLOY_MODE` = `gitops`. Deploy stage'i atlanır; Test, Build Image, Push çalışmaya devam eder.
