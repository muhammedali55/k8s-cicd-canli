# Sözlük

Rozetler: `[junior]` ilk gün bilinmesi gereken, `[senior]` sistemi işletirken gereken, `[architect]` tasarım kararı verirken gereken.
"Repoda" sütunu terimin bu projede nerede geçtiğini gösterir.

| Terim | Rozet | Kısa açıklama | Repoda |
| --- | --- | --- | --- |
| Actuator | `[junior]` | Spring Boot'un sağlık, bilgi ve metrik uçları (`/actuator/health`, `/actuator/prometheus`). | `services/*/src/main/resources/application.yml` |
| Agent (Jenkins) | `[junior]` | Jenkins'te işi fiilen çalıştıran makine ya da süreç. Bizde `agent any` → Jenkins'in kendisi. | `jenkins/Jenkinsfile` |
| Alt ajan (sub-agent) | `[senior]` | Ana AI ajanının belirli bir klasör ve görevle başlattığı ayrı ajan. | `.claude/agents/*.md` |
| Application (Argo CD) | `[junior]` | Argo CD'ye "hangi Git yolunu hangi cluster/namespace'e uygula" diyen nesne. | `gitops/argocd-app.yaml` |
| `ARG` / `ENV` (Dockerfile) | `[senior]` | `ARG` yalnızca build sırasında yaşayan değişken; `ENV` imaja yazılıp çalışma anında da görünen ortam değişkeni. `ARG APP_VERSION` → `ENV APP_VERSION`. | `services/*/Dockerfile` |
| Artifact (CI) | `[senior]` | Bir CI çalışmasının sakladığı dosya; başka bir job ya da workflow indirebilir. | `ci.yml` → `image-<servis>`, `cd.yml` indirir |
| Base / Overlay | `[senior]` | Kustomize'da ortak manifest'ler (base) ve ortama özel değişiklikler (overlay). | `k8s/base`, `k8s/overlays/dev` |
| `BASE_IMAGE_PREFIX` | `[senior]` | GitOps job'unun overlay'deki imaj girdisini bulmak için kullandığı sabit ad (`ghcr.io/muhammedali55`). Fork'ta da doğru eşleşme sağlar. | `ci.yml` `gitops-update` |
| Build-arg | `[senior]` | `docker build --build-arg` ile build'e verilen değer. Bizde `APP_VERSION=<kısa-sha>`. | `ci.yml` `build-args`, `Jenkinsfile` |
| CI / CD | `[junior]` | Sürekli entegrasyon (her commit'te test + build) / sürekli teslimat-dağıtım (otomatik deploy). | `.github/workflows/ci.yml`, `cd.yml` |
| CLAUDE.md | `[junior]` | Claude Code'un her oturumda okuduğu proje kuralları dosyası. | `CLAUDE.md` |
| ConfigMap | `[junior]` | Gizli olmayan ayarları pod'a ortam değişkeni ya da dosya olarak veren Kubernetes nesnesi. | `k8s/base/*/configmap.yaml` |
| Container | `[junior]` | İmajın izole çalışan örneği. | `services/*/Dockerfile` |
| Context (kubectl) | `[junior]` | kubectl'in hangi cluster'a hangi kimlikle konuştuğu. Bizde `kind-k8s-cicd`. | `cd.yml` (`KUBE_CONTEXT`) |
| Contract-first (sözleşme önce) | `[senior]` | Servisler arası API'yi koddan önce yazıp iki tarafı ona göre geliştirmek. | `contracts/product-api.yaml` |
| Credential (Jenkins) | `[senior]` | Jenkins'in sakladığı ve loglarda maskelediği gizli bilgi. | `ghcr-creds`, `kubeconfig` |
| CRD | `[senior]` | Kubernetes'e yeni bir nesne tipi ekleyen tanım (ör. `Gateway`, `HTTPRoute`, Argo CD `Application`). | `setup-cluster.sh` (Gateway API CRD'leri v1.7.0) |
| `DEMO_BROKEN` | `[junior]` | `true` iken readiness'i DOWN yapan eğitim anahtarı. Liveness'ı etkilemez. | `DemoBrokenHealthIndicator.java`, ConfigMap'ler, `ActuatorEndpointsTest` |
| `DEPLOY_MODE` | `[senior]` | Yol A / Yol B anahtarı. `gitops` iken CI tag'i Git'e yazar, `cd.yml` ve Jenkins Deploy atlanır. | `ci.yml`, `cd.yml`, `Jenkinsfile` |
| Deployment | `[junior]` | Pod'ların istenen kopya sayısını ve sürüm geçişini yöneten nesne. | `k8s/base/*/deployment.yaml` |
| Desired state (istenen durum) | `[senior]` | YAML'da tanımlanan hedef; Kubernetes ve Argo CD gerçek durumu buna yaklaştırır. | Tüm `k8s/` |
| `@DisplayName` / `@Nested` | `[senior]` | JUnit 5'te testin okunur adı / iç içe test grubu. Surefire raporu iç içe testleri display name ile yazar; sınıf adıyla aranırsa "koşmuyor" sanılabilir. | `services/*/src/test/.../ActuatorEndpointsTest.java` |
| Drift | `[senior]` | Cluster'daki gerçek durumun Git'tekinden sapması. | `selfHeal: true` |
| Flyway | `[junior]` | Veritabanı şemasını sürümlü SQL dosyalarıyla kuran araç. | `src/main/resources/db/migration/` |
| Gateway | `[senior]` | Gateway API'de dış trafiğin giriş kapısı (listener'lar). | `k8s/base/gateway/gateway.yaml` |
| Gateway API | `[senior]` | Ingress'in halefi olan Kubernetes standardı: GatewayClass → Gateway → HTTPRoute. | `k8s/base/gateway/` |
| GatewayClass | `[architect]` | Gateway'leri hangi implementasyonun (bizde Traefik) yöneteceğini söyler. | `cluster/traefik-values.yaml` (`traefik`) |
| GHCR | `[junior]` | GitHub Container Registry, `ghcr.io`. | `ci.yml`, `Jenkinsfile` |
| GitOps | `[senior]` | İstenen durumun Git'te tutulduğu, cluster'ın Git'ten çektiği deploy modeli. | `gitops/`, `ci.yml` `gitops-update` |
| `GITHUB_TOKEN` | `[senior]` | GitHub'ın her workflow çalışmasına verdiği kısa ömürlü token. | `ci.yml`, `cd.yml` |
| Headless Service | `[senior]` | `clusterIP: None`; pod'lara doğrudan DNS adı verir (StatefulSet ile). | `k8s/base/postgres/service.yaml` |
| Helm | `[senior]` | Kubernetes paket yöneticisi; biz yalnızca hazır chart kuruyoruz (Traefik, metrics-server). | `setup-cluster.sh` |
| HPA | `[senior]` | Horizontal Pod Autoscaler: metriklere göre replika sayısını ayarlar. Yönettiği Deployment'ta `replicas` yazılmaz. | `k8s/base/order-service/hpa.yaml` |
| HTTPRoute | `[senior]` | Gateway'e gelen isteği yol ya da başlık kurallarıyla bir Service'e yönlendirir. | `k8s/base/gateway/*-route.yaml` |
| İmaj (image) | `[junior]` | Uygulama ve çalışma ortamının salt-okunur paketi. | `ghcr.io/muhammedali55/<servis>:<tag>` |
| Ingress / ingress-nginx | `[architect]` | Eski HTTP giriş API'si ve onun en yaygın controller'ı. ingress-nginx Mart 2026'da emekliye ayrıldı; biz kullanmıyoruz. | SPEC §2 |
| kind | `[junior]` | Kubernetes'i Docker container'ları içinde çalıştıran araç. | `cluster/kind-config.yaml` |
| `kind load docker-image` | `[senior]` | Lokal Docker imajını registry olmadan kind node'una kopyalar. `IMAGE_SOURCE=local` (varsayılan) bunu kullanır. | `setup-cluster.sh` |
| Kustomize | `[senior]` | Şablonsuz YAML özelleştirme aracı; `kubectl -k` ile yerleşik gelir. | `k8s/` |
| Liveness probe | `[junior]` | "Takıldın mı?" kontrolü; başarısızsa container yeniden başlatılır. | `/actuator/health/liveness` |
| Matrix (Actions) | `[senior]` | Aynı job'u farklı parametrelerle paralel çalıştırma. | `ci.yml` `build` job'u |
| `maxSurge` / `maxUnavailable` | `[senior]` | Rolling update'te fazladan açılabilecek / aynı anda kapanabilecek pod sayısı. Bizde 1 / 0. | Deployment'lar |
| metrics-server | `[senior]` | Pod CPU/bellek ölçümlerini sağlayan bileşen; HPA için gerekli. | `setup-cluster.sh` |
| Multi-stage build | `[junior]` | Dockerfile'da derleme ve çalışma aşamalarını ayırma. | `services/*/Dockerfile` |
| Namespace | `[junior]` | Cluster içinde mantıksal bölme. Bizde `demo`, `traefik`, `argocd`. | `k8s/overlays/dev/kustomization.yaml` |
| Non-root | `[senior]` | Container'ın root olmayan bir kullanıcıyla çalışması. Bizde sayısal `USER 10001`. | `services/*/Dockerfile` |
| OpenAPI | `[junior]` | HTTP API'lerini makine-okunur tanımlama standardı (biz 3.1 kullanıyoruz). | `contracts/product-api.yaml` |
| PAT | `[junior]` | Personal Access Token; Jenkins'in GHCR'a push edebilmesi için (`write:packages`). | `jenkins/README.md` |
| Pod | `[junior]` | Kubernetes'in en küçük çalışma birimi; bir ya da daha fazla container. | Tüm Deployment'lar |
| ProblemDetail (RFC 9457) | `[senior]` | Standart JSON hata gövdesi: `type`, `title`, `status`, `detail`, `instance`. | `GlobalExceptionHandler.java` |
| Prune | `[senior]` | Git'ten silinen kaynağın cluster'dan da silinmesi. | `argocd-app.yaml` |
| PVC | `[senior]` | PersistentVolumeClaim; pod'dan bağımsız yaşayan disk talebi. | `data-postgres-0` |
| Readiness probe | `[junior]` | "Trafik alabilir misin?" kontrolü; başarısızsa pod Service'ten çıkarılır. | `/actuator/health/readiness` |
| Readiness grubu | `[senior]` | Actuator'da readiness'e hangi sağlık bileşenlerinin dahil olduğu. İki serviste de `readinessState,db,demoBroken`. | `management.endpoint.health.group.readiness.include` |
| ReplicaSet / Revizyon | `[senior]` | Deployment'ın her sürüm için tuttuğu pod grubu; rollback bunlara döner. | `kubectl rollout history` |
| RestClient | `[junior]` | Spring'in senkron HTTP istemcisi; order-service → product-service çağrısı. | `ProductClient.java`, `ProductClientConfig.java` |
| Rollback / `rollout undo` | `[junior]` | Deployment'ı önceki revizyona döndürme. | `cd.yml`, `Jenkinsfile` |
| `rollout restart` | `[senior]` | Pod'ları tanım değişmeden yeniden başlatır. Aynı tag'li lokal imaj güncellendiğinde gerekir. | `setup-cluster.sh` |
| Rolling update | `[junior]` | Pod'ları kademeli olarak yeni sürümle değiştirme. | Deployment `strategy` |
| Secret | `[junior]` | Gizli değerler için Kubernetes nesnesi (base64, şifreli değil!). | `postgres-secret` |
| `selfHeal` | `[senior]` | Argo CD'nin drift'i otomatik geri alması. | `argocd-app.yaml` |
| Self-hosted runner | `[senior]` | GitHub Actions job'larını kendi makinende çalıştıran ajan. | `cd.yml` |
| Service | `[junior]` | Pod grubuna sabit DNS adı ve yük dağıtımı veren nesne. | `k8s/base/*/service.yaml` |
| SPEC | `[junior]` | Projenin tek doğruluk kaynağı. | `SPEC.md` |
| Startup probe | `[senior]` | Yavaş açılan uygulama hazır olana kadar diğer probe'ları bekleten kontrol. | Deployment'lar (5 sn × 60) |
| StatefulSet | `[senior]` | Kalıcı kimlik ve disk isteyen uygulamalar (ör. veritabanı) için. | `k8s/base/postgres/statefulset.yaml` |
| Sync (Argo CD) | `[junior]` | Git'teki durumu cluster'a uygulama. `Synced` / `OutOfSync`. | Argo CD arayüzü |
| Tag (imaj) | `[junior]` | İmaj sürüm etiketi. Bizde `0.1.0`, `local` ve kısa git SHA; `latest` yasak. | `kustomization.yaml` `images:` |
| Traefik | `[senior]` | Gateway API implementasyonu olarak kullandığımız reverse proxy. | `cluster/traefik-values.yaml` |
| URLRewrite | `[senior]` | HTTPRoute filtresi; isteğin yolunu servise iletmeden önce değiştirir. `/info/products` → `/api/info`. | `k8s/base/gateway/*-route.yaml` |
| `workflow_run` | `[senior]` | Bir workflow bitince başka bir workflow'u tetikleyen olay. | `cd.yml` |
| Webhook | `[junior]` | Bir olay olunca (ör. push) karşı sisteme yapılan HTTP çağrısı. | Jenkins `/github-webhook/` |
| 422 / 502 / 503 | `[senior]` | order-service'in hata kodları: olmayan ürün (422), product-service'ten beklenmedik 4xx (502), product-service'e ulaşılamıyor ya da 5xx döndü (503). | `order-service/.../web/GlobalExceptionHandler.java` |
