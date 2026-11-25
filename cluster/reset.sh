#!/usr/bin/env bash
# Cluster'ı tamamen silip sıfırdan kurar (setup-cluster.sh'i çağırır) ve toplam süreyi yazar.
# IMAGE_SOURCE gibi ortam değişkenleri setup-cluster.sh'e aynen aktarılır:
#   IMAGE_SOURCE=local ./cluster/reset.sh
set -euo pipefail

CLUSTER_NAME="k8s-cicd"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MAVI=$'\033[1;34m'
YESIL=$'\033[1;32m'
SIFIRLA=$'\033[0m'

baslangic=$(date +%s)

echo
echo "${MAVI}==> [Sıfırlama] '${CLUSTER_NAME}' cluster'ı siliniyor${SIFIRLA}"
# Cluster yoksa kind hata vermez; yine de güvenli tarafta kalalım.
kind delete cluster --name "${CLUSTER_NAME}" || true

echo
echo "${MAVI}==> [Sıfırlama] Kurulum başlıyor (setup-cluster.sh)${SIFIRLA}"
"${SCRIPT_DIR}/setup-cluster.sh"

bitis=$(date +%s)
sure=$((bitis - baslangic))
echo
echo "${YESIL}==> Toplam süre: $((sure / 60)) dk $((sure % 60)) sn${SIFIRLA}"
