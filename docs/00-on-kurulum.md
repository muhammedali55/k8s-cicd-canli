# 00 — Ön kurulum (katılımcı rehberi)

> Bu dosyayı eğitimden **önce** baştan sona uygula; canlıda kurulumla vakit kaybetmeyelim.
> Hedef: "4. Son kontrol" bölümündeki komutların hepsi hatasız çalışsın.

## 1. Neden?

Eğitimde bir commit'in laptop'tan Kubernetes pod'una kadar yolculuğunu izleyeceğiz. Bu yolculuktaki her durak
(derleme, imaj, cluster, pipeline) bir araç istiyor. Araçlardan biri eksikse ya da yanlış sürümdeyse akış ilk adımda kopar.
Hepsini bir kez kurarsan eğitim boyunca yalnızca kavramlara odaklanırız.

## 2. Kavramlar

| Araç | Rozet | Ne işe yarar | Repoda nerede kullanılıyor |
| --- | --- | --- | --- |
| Docker | `[junior]` | Container imajı build eder ve çalıştırır. kind de cluster node'unu bir Docker container'ı olarak açar. | `services/*/Dockerfile`, `cluster/setup-cluster.sh`, `jenkins/docker-compose.yml` |
| kind | `[junior]` | "Kubernetes in Docker": laptop'ta tek komutla gerçek bir Kubernetes cluster'ı açar. | `cluster/kind-config.yaml` (cluster adı `k8s-cicd`, node imajı Kubernetes v1.37.0) |
| kubectl | `[junior]` | Kubernetes'in komut satırı istemcisi. | `cluster/*.sh`, `.github/workflows/cd.yml`, `jenkins/Jenkinsfile` |
| helm | `[senior]` | Kubernetes paket yöneticisi. Kendi chart'ımızı yazmıyoruz; yalnızca Traefik ve metrics-server'ı Helm ile kuruyoruz. | `cluster/setup-cluster.sh` |
| Git + GitHub hesabı | `[junior]` | Kod deposu. GitHub Actions, GHCR (imaj deposu) ve Argo CD'nin izlediği repo burada. | `.github/workflows/`, `gitops/argocd-app.yaml` |
| JDK 21 | `[junior]` | Servisleri lokalde derleyip test etmek için (`./mvnw verify`). Maven'ı ayrıca kurmana gerek yok, her serviste Maven Wrapper (`mvnw`) var. | `services/*/pom.xml` (Spring Boot 4.1.3, `java.version` 21) |

**Donanım önerisi:** En az 8 GB RAM'li bir makine kullan ve Docker'a en az 6 GB bellek ayır
(Docker Desktop → Settings → Resources). Cluster'da aynı anda 2 product-service, 2 ile 5 arası order-service (HPA),
PostgreSQL, Traefik ve metrics-server çalışacak; rolling update sırasında bir pod daha açılıyor.

## 3. Canlı akış: kurulum

### 3.1 macOS (Homebrew ile)

Homebrew yoksa önce https://brew.sh adresindeki tek satırlık komutla kur.

```bash
# Docker Desktop (kurduktan sonra uygulamayı bir kez aç, balina ikonu "running" olsun)
brew install --cask docker

# kind, kubectl, helm, git
brew install kind kubectl helm git

# JDK 21 (Eclipse Temurin, SPEC ile aynı dağıtım)
brew install --cask temurin@21
```

Beklenen: her komut `🍺 ... was successfully installed!` benzeri bir satırla biter.

### 3.2 Linux (Ubuntu / Debian)

```bash
# Temel paketler + Git + JDK 21
sudo apt-get update
sudo apt-get install -y curl ca-certificates git openjdk-21-jdk

# Docker Engine (resmi kurulum script'i) ve kullanıcını docker grubuna ekle
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"
newgrp docker        # ya da oturumu kapatıp aç

# Mimariyi bul (amd64 / arm64)
ARCH=$(uname -m); [ "$ARCH" = "x86_64" ] && ARCH=amd64; [ "$ARCH" = "aarch64" ] && ARCH=arm64

# kind (kind-config.yaml'daki yorumda belirtilen sürüm: v0.33.0)
curl -Lo ./kind "https://kind.sigs.k8s.io/dl/v0.33.0/kind-linux-${ARCH}"
chmod +x ./kind && sudo mv ./kind /usr/local/bin/kind

# kubectl (cluster sürümüyle aynı: v1.37.0)
curl -LO "https://dl.k8s.io/release/v1.37.0/bin/linux/${ARCH}/kubectl"
sudo install -m 0755 kubectl /usr/local/bin/kubectl && rm kubectl

# helm (resmi kurulum script'i)
curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

> Fedora/RHEL kullanıyorsan `apt-get` yerine `sudo dnf install -y git java-21-openjdk-devel` kullan; diğer adımlar aynı.

### 3.3 Windows

Repodaki script'ler (`cluster/*.sh`) **bash** script'i. Bu yüzden Windows'ta önerilen yol **WSL2 + Ubuntu**:

1. PowerShell'i **yönetici olarak** aç:

   ```powershell
   # WSL2 ve Ubuntu
   wsl --install -d Ubuntu

   # Docker Desktop (Windows tarafına kurulur)
   winget install -e --id Docker.DockerDesktop
   ```

2. Bilgisayarı yeniden başlat. Docker Desktop'ı aç → **Settings → Resources → WSL integration** → Ubuntu'yu işaretle.
3. Başlat menüsünden **Ubuntu** terminalini aç ve **3.2 Linux** bölümündeki komutları uygula, ama
   **Docker Engine kurulum satırlarını ATLA** (Docker'ı Docker Desktop sağlıyor).

Araçları WSL olmadan, doğrudan PowerShell'den kullanmak istersen (script'ler hariç):

```powershell
winget install -e --id Kubernetes.kind
winget install -e --id Kubernetes.kubectl
winget install -e --id Helm.Helm
winget install -e --id Git.Git
winget install -e --id EclipseAdoptium.Temurin.21.JDK
```

> Bu durumda `./cluster/reset.sh` gibi komutları **Git Bash** içinden çalıştırman gerekir. Eğitimde WSL ile ilerleyeceğiz.

### 3.4 GitHub hesabı ve erişim

1. https://github.com adresinde bir hesabın olsun.
2. Bir SSH anahtarı oluştur ve GitHub'a ekle (Settings → SSH and GPG keys → New SSH key):

   ```bash
   ssh-keygen -t ed25519 -C "github"
   cat ~/.ssh/id_ed25519.pub     # bu satırı GitHub'a yapıştır
   ```

3. Repo'yu klonla (SPEC'teki repo: `github.com/muhammedali55/k8s-cicd-canli`):

   ```bash
   git clone https://github.com/muhammedali55/k8s-cicd-canli.git
   cd k8s-cicd-canli
   ```

4. Bölüm 2'deki Jenkins kısmını kendin de deneyeceksen bir **Personal Access Token (classic)** oluştur, yetkisi
   `write:packages` olsun. Yolu: GitHub → Settings → Developer settings → Personal access tokens. **Token'ı hiçbir dosyaya yazma**;
   onu Jenkins'in Credentials ekranına gireceğiz (`jenkins/README.md`, 4. adım).

> **Kendi fork'unda çalışacaksan** şunları kendi kullanıcı adınla değiştirmen gerekiyor (GHCR imaj adları **küçük harf** olmalı):
> - `jenkins/Jenkinsfile` → `IMAGE_PREFIX = 'ghcr.io/muhammedali55'` (Jenkins kendi hesabının paketine push etmeli)
> - `gitops/argocd-app.yaml` → `repoURL` (Argo CD senin repo'nu izlemeli)
>
> `k8s/` altındaki imaj adlarını (`ghcr.io/muhammedali55/...`) **değiştirmene gerek yok**. Bunlar yalnızca eşleştirme anahtarı:
> GitHub Actions imaj önekini `GITHUB_REPOSITORY_OWNER`'dan kendisi hesaplıyor, `ci.yml`'deki `gitops-update` job'u da
> eşleştirmeyi sabit `BASE_IMAGE_PREFIX: ghcr.io/muhammedali55` ile yapıp senin önekini `newName` alanına yazıyor.
> Değiştirirsen `ci.yml`'deki `BASE_IMAGE_PREFIX`'i de aynı değere çekmen gerekir.

### 3.5 Base imajları önceden çek (internet yavaşsa çok işe yarar)

```bash
docker pull eclipse-temurin:21-jdk
docker pull eclipse-temurin:21-jre
docker pull postgres:17
```

`setup-cluster.sh` lokalde `postgres:17` varsa onu kind'a yüklemeyi dener. Yükleme başarısız olursa
(Docker Desktop'ta çok platformlu imajlarda bazen oluyor) script durmaz, uyarı verir ve cluster imajı internetten çeker.

## 4. Son kontrol (her biri hatasız çalışmalı)

```bash
docker version                 # Client ve Server bölümleri birlikte görünmeli
docker run --rm hello-world    # "Hello from Docker!" yazmalı
kind version                   # kind v0.33.0 ... (ya da daha yeni)
kubectl version --client       # Client Version: v1.37.x (cluster ile en fazla 1 minor fark)
helm version                   # version.BuildInfo{Version:"v3..." ...} (v4 de olur)
git --version                  # git version 2.x
java -version                  # openjdk version "21..." (Temurin ya da OpenJDK)
ssh -T git@github.com          # "Hi <kullanıcı-adın>! You've successfully authenticated..."
```

Repo'nun içinden ek bir kontrol yap: JDK ve Maven Wrapper çalışıyor mu? İlk seferde bağımlılıklar indiği için birkaç dakika sürebilir.

```bash
(cd services/product-service && ./mvnw -q verify) && echo "product-service OK"
```

Beklenen: birkaç dakika sonra yalnızca `product-service OK` satırı (14 test sessizce geçer).

## 5. Bozalım ve düzeltelim: en sık iki kurulum sorunu

**Sorun 1: 80 portu dolu.** `kind-config.yaml` host'un 80 portunu cluster'a bağlıyor. Bu portu başka bir program
(IIS, Apache, nginx, Skype ya da başka bir kind cluster'ı) tutuyorsa cluster kurulurken şuna benzer bir hata görürsün:
`Bind for 0.0.0.0:80 failed: port is already allocated`.

```bash
# macOS / Linux: 80'i kim tutuyor?
sudo lsof -i :80
# Windows (PowerShell)
netstat -ano | findstr :80
```

Çözüm: o programı durdur. Başka bir kind cluster'ıysa `kind get clusters` ile bul, `kind delete cluster --name <ad>` ile sil.

**Sorun 2 (Linux): `permission denied while trying to connect to the Docker daemon socket`.**
Kullanıcın `docker` grubunda değil ya da grup değişikliği oturuma yansımamış.
Çözüm: `sudo usermod -aG docker "$USER"`, ardından oturumu kapatıp aç (ya da `newgrp docker`).

## 6. Sık sorulan sorular

**Docker Desktop yerine Podman, Colima ya da Rancher Desktop kullanabilir miyim?**
kind bunlarla da çalışabilir. Ancak eğitim Docker Desktop (macOS/Windows) ve Docker Engine (Linux) ile test ediliyor.
Canlıda sorun yaşamamak için bunları tercih et.

**Maven'ı ayrıca kurmam gerekiyor mu?**
Hayır. Her serviste `mvnw` (Maven Wrapper) var ve doğru Maven sürümünü kendisi indiriyor. Yalnızca JDK 21 gerekli.

**Apple Silicon (M1–M4) Mac'te sorun olur mu?**
Hayır. `eclipse-temurin`, `postgres:17` ve kind node imajı ARM64 için de yayınlanıyor.

**kubectl sürümüm cluster'dan farklı, sorun olur mu?**
kubectl, cluster sürümünden en fazla bir minor sürüm ileride ya da geride olmalı. Cluster sürümünü
`cluster/kind-config.yaml` belirliyor: Kubernetes v1.37.0. Jenkins imajı da aynı sürümü kuruyor (`jenkins/Dockerfile`, `KUBECTL_VERSION=v1.37.0`).

**macOS'ta zsh kullanıyorum, dikkat etmem gereken bir şey var mı?**
Evet, bir tane: döngülerde `$s:local` yazma. zsh `:l` ifadesini "küçük harfe çevir" değiştiricisi sanıyor ve imaj adı
`order-serviceocal` gibi bozuluyor. Her zaman süslü parantez kullan: `"${s}:local"`.

**`claude` (Claude Code) kurmam gerekiyor mu?**
Katılımcı olarak hayır. Bölüm 1'de AI ajanlarla proje üretimini eğitmen gösterecek; senin ihtiyacın olan her şey repoda hazır.
