#!/usr/bin/env bash
# Lokal kind cluster'ını sıfırdan kurar ve demo uygulamasını ayağa kaldırır:
#   kind cluster -> Gateway API CRD'leri -> Traefik -> metrics-server -> demo namespace
#   -> (isteğe bağlı) lokal imajları yükle -> kubectl apply -k -> hazır olmayı bekle -> duman testi
#
# Kullanım:
#   ./cluster/setup-cluster.sh                        # varsayılan: lokal imajlar (product-service:local, order-service:local) kind'a yüklenir
#   IMAGE_SOURCE=registry ./cluster/setup-cluster.sh  # imajlar GHCR'dan çekilir
#
# Ortam değişkenleri:
#   IMAGE_SOURCE   "local" (varsayılan) ya da "registry"
#                  "registry" yalnızca overlay'deki tag GHCR'da varsa çalışır. Başlangıç tag'i 0.1.0'ı
#                  hiçbir pipeline push etmez; CI kısa SHA push eder ve overlay'i Yol B (GitOps) günceller.
#   WAIT_TIMEOUT   pod'ların hazır olmasını bekleme süresi (varsayılan 300s)
set -euo pipefail

# ---------------------------------------------------------------------------
# Sabit sürümler (latest YOK — canlıda sürpriz olmasın)
# ---------------------------------------------------------------------------
CLUSTER_NAME="k8s-cicd"
NAMESPACE="demo"
GATEWAY_API_VERSION="v1.7.0"
TRAEFIK_CHART_VERSION="43.0.0"          # Traefik v3.8.0
METRICS_SERVER_CHART_VERSION="3.14.0"   # metrics-server v0.9.0

IMAGE_SOURCE="${IMAGE_SOURCE:-local}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-300s}"

# Script hangi klasörden çağrılırsa çağrılsın repo kökünü bul.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OVERLAY_DIR="${REPO_ROOT}/k8s/overlays/dev"

# ---------------------------------------------------------------------------
# Renkli çıktı yardımcıları
# ---------------------------------------------------------------------------
MAVI=$'\033[1;34m'
YESIL=$'\033[1;32m'
SARI=$'\033[1;33m'
KIRMIZI=$'\033[1;31m'
SIFIRLA=$'\033[0m'

ADIM=0
baslik() {
  ADIM=$((ADIM + 1))
  echo
  echo "${MAVI}==> [Adım ${ADIM}] $*${SIFIRLA}"
}
bilgi()  { echo "    $*"; }
basari() { echo "${YESIL}    ✔ $*${SIFIRLA}"; }
uyari()  { echo "${SARI}    ! $*${SIFIRLA}"; }
hata()   { echo "${KIRMIZI}    ✘ $*${SIFIRLA}" >&2; }

# ---------------------------------------------------------------------------
baslik "Gerekli araçlar kontrol ediliyor"
# ---------------------------------------------------------------------------
eksik=0
for arac in docker kind kubectl helm; do
  if command -v "${arac}" >/dev/null 2>&1; then
    basari "${arac} bulundu"
  else
    hata "${arac} bulunamadı. Lütfen kurup tekrar deneyin."
    eksik=1
  fi
done
if [[ "${eksik}" -ne 0 ]]; then
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  hata "Docker çalışmıyor. Docker Desktop'ı başlatıp tekrar deneyin."
  exit 1
fi
basari "Docker çalışıyor"

# ---------------------------------------------------------------------------
baslik "kind cluster'ı kuruluyor (${CLUSTER_NAME})"
# ---------------------------------------------------------------------------
if kind get clusters 2>/dev/null | grep -x "${CLUSTER_NAME}" >/dev/null; then
  uyari "'${CLUSTER_NAME}' zaten var, yeniden kurulmuyor. Sıfırdan kurmak için: ./cluster/reset.sh"
else
  kind create cluster --config "${SCRIPT_DIR}/kind-config.yaml"
fi
kubectl config use-context "kind-${CLUSTER_NAME}" >/dev/null
basari "kubectl bağlamı: kind-${CLUSTER_NAME}"

# ---------------------------------------------------------------------------
baslik "Gateway API CRD'leri yükleniyor (${GATEWAY_API_VERSION})"
# ---------------------------------------------------------------------------
kubectl apply --server-side -f \
  "https://github.com/kubernetes-sigs/gateway-api/releases/download/${GATEWAY_API_VERSION}/standard-install.yaml"
basari "Gateway, HTTPRoute ve GatewayClass tipleri artık cluster'da tanımlı"

# ---------------------------------------------------------------------------
baslik "Traefik kuruluyor (Helm chart ${TRAEFIK_CHART_VERSION}, Gateway API sağlayıcısı açık)"
# ---------------------------------------------------------------------------
helm repo add traefik https://traefik.github.io/charts --force-update >/dev/null
helm repo update traefik >/dev/null
helm upgrade --install traefik traefik/traefik \
  --version "${TRAEFIK_CHART_VERSION}" \
  --namespace traefik --create-namespace \
  --values "${SCRIPT_DIR}/traefik-values.yaml" \
  --wait --timeout 5m
basari "Traefik hazır; http://localhost -> Traefik"

# ---------------------------------------------------------------------------
baslik "metrics-server kuruluyor (HPA için CPU ölçümü)"
# ---------------------------------------------------------------------------
# kind'daki kubelet sertifikaları kendinden imzalı; bu yüzden --kubelet-insecure-tls (yalnızca lokal!).
helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/ --force-update >/dev/null
helm repo update metrics-server >/dev/null
helm upgrade --install metrics-server metrics-server/metrics-server \
  --version "${METRICS_SERVER_CHART_VERSION}" \
  --namespace kube-system \
  --set 'args={--kubelet-insecure-tls}' \
  --wait --timeout 5m
basari "metrics-server hazır"

# ---------------------------------------------------------------------------
baslik "'${NAMESPACE}' namespace'i oluşturuluyor"
# ---------------------------------------------------------------------------
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
basari "namespace/${NAMESPACE} hazır"

# ---------------------------------------------------------------------------
if [[ "${IMAGE_SOURCE}" == "local" ]]; then
  baslik "Lokal imajlar kind'a yükleniyor (IMAGE_SOURCE=local)"
  # Overlay'in ürettiği gerçek imaj adlarını oku (ör. ghcr.io/muhammedali55/product-service:0.1.0).
  manifestler="$(kubectl kustomize "${OVERLAY_DIR}")"
  for servis in product-service order-service; do
    hedef_imaj="$(echo "${manifestler}" | awk -v s="/${servis}:" '$1 == "image:" && index($2, s) { print $2 }' | sort -u)"
    lokal_imaj="${servis}:local"
    if ! docker image inspect "${lokal_imaj}" >/dev/null 2>&1; then
      hata "${lokal_imaj} bulunamadı. Önce: docker build -t ${lokal_imaj} services/${servis}"
      exit 1
    fi
    # Pod'lar overlay'deki adı arar; lokal imajı o adla etiketleyip kind node'una kopyalıyoruz.
    docker tag "${lokal_imaj}" "${hedef_imaj}"
    kind load docker-image "${hedef_imaj}" --name "${CLUSTER_NAME}"
    basari "${lokal_imaj} -> ${hedef_imaj} yüklendi"
  done
  # PostgreSQL imajı lokalde varsa onu da yükle (yoksa cluster internetten çekmeye çalışır).
  # Yükleme başarısız olursa script durmaz: Docker Desktop'ta çok platformlu resmi imajlar
  # "kind load" ile bazen aktarılamıyor ("content digest ... not found"); cluster imajı kendisi çeker.
  if docker image inspect postgres:17 >/dev/null 2>&1; then
    if kind load docker-image postgres:17 --name "${CLUSTER_NAME}"; then
      basari "postgres:17 yüklendi"
    else
      uyari "postgres:17 kind'a yüklenemedi; cluster internetten çekmeye çalışacak"
    fi
  else
    uyari "postgres:17 lokalde yok; cluster internetten çekmeye çalışacak"
  fi
fi

# ---------------------------------------------------------------------------
baslik "Uygulama manifest'leri uygulanıyor (kubectl apply -k k8s/overlays/dev)"
# ---------------------------------------------------------------------------
kubectl apply -k "${OVERLAY_DIR}"

# Lokal imajda tag (ör. 0.1.0) değişmeden içerik değişebilir. Deployment tanımı aynı kaldığı için
# Kubernetes yeni imajı fark etmez; pod'ları yeniden başlatarak yeni yüklenen imajı kullanmalarını sağlarız.
if [[ "${IMAGE_SOURCE}" == "local" ]]; then
  kubectl -n "${NAMESPACE}" rollout restart deployment/product-service deployment/order-service
  basari "Servis pod'ları yeni lokal imajla yeniden başlatılıyor"
fi

# ---------------------------------------------------------------------------
baslik "Pod'ların hazır olması bekleniyor (en fazla ${WAIT_TIMEOUT})"
# ---------------------------------------------------------------------------
# Önce veritabanı: servisler açılışta Flyway ile ona bağlanır.
# (StatefulSet pod'u apply'dan hemen sonra henüz oluşmamış olabilir; kısa süre bekle.)
for _ in $(seq 1 30); do
  kubectl get pod postgres-0 -n "${NAMESPACE}" >/dev/null 2>&1 && break
  sleep 2
done
kubectl wait pod/postgres-0 -n "${NAMESPACE}" --for=condition=Ready --timeout="${WAIT_TIMEOUT}"
basari "PostgreSQL hazır"

if ! kubectl wait deployment --all -n "${NAMESPACE}" --for=condition=Available --timeout="${WAIT_TIMEOUT}"; then
  hata "Servisler zamanında hazır olmadı. Son durum:"
  kubectl get pods -n "${NAMESPACE}" -o wide
  bilgi "İpucu: kubectl describe pod -n ${NAMESPACE} <pod>  /  kubectl logs -n ${NAMESPACE} <pod>"
  exit 1
fi
basari "product-service ve order-service hazır"

# ---------------------------------------------------------------------------
baslik "Duman testi: curl http://localhost/api/products"
# ---------------------------------------------------------------------------
# Traefik'in yeni HTTPRoute'ları görmesi birkaç saniye sürebilir; birkaç kez dene.
duman_ok=0
for deneme in $(seq 1 15); do
  if cevap="$(curl -fsS --max-time 5 http://localhost/api/products)"; then
    duman_ok=1
    break
  fi
  bilgi "deneme ${deneme}/15 başarısız, 2 sn sonra tekrar..."
  sleep 2
done
if [[ "${duman_ok}" -eq 1 ]]; then
  basari "Cevap geldi:"
  echo "${cevap}"
else
  hata "http://localhost/api/products cevap vermedi"
  kubectl get gateway,httproute -n "${NAMESPACE}"
  exit 1
fi

# ---------------------------------------------------------------------------
baslik "Özet"
# ---------------------------------------------------------------------------
kubectl get pods,svc,hpa,gateway,httproute -n "${NAMESPACE}"
echo
echo "${YESIL}Cluster hazır!${SIFIRLA}"
bilgi "Ürünler   : curl http://localhost/api/products"
bilgi "Sipariş   : curl -X POST http://localhost/api/orders -H 'Content-Type: application/json' -d '{\"productId\":1,\"quantity\":1}'"
bilgi "İmaj kaynağı: ${IMAGE_SOURCE}"
