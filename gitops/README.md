# GitOps (Yol B) — Argo CD

Yol A'da (`cd.yml` / Jenkins Deploy) CI, `kubectl set image` ile cluster'a **iter** (push).
Yol B'de CI cluster'a hiç dokunmaz: yeni imaj tag'ini Git'e (`k8s/overlays/dev/kustomization.yaml`)
yazar, cluster içindeki Argo CD bu değişikliği görüp **çeker** (pull). Git = tek doğruluk kaynağı.

```
commit → CI: test + imaj push → CI: kustomization.yaml'da tag güncelle + commit
                                              ↓
                          Argo CD: Git'i izler → farkı görür → cluster'ı sync eder
```

## 1. Argo CD'yi kur

```bash
kubectl create namespace argocd
kubectl apply -n argocd --server-side --force-conflicts \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

> `--server-side`: Argo CD'nin bazı CRD'leri, klasik `kubectl apply`'ın annotation boyut sınırını
> aşıyor; server-side apply bu sorunu yaşamaz. Sürümü sabitlemek isterseniz `stable` yerine
> bir sürüm etiketi (ör. `v3.x.y`) yazın.

Pod'ların hazır olmasını bekle:

```bash
kubectl -n argocd rollout status deployment/argocd-server --timeout=180s
```

## 2. Arayüze eriş (port-forward)

```bash
kubectl -n argocd port-forward svc/argocd-server 8443:443
```

Tarayıcıda `https://localhost:8443` (self-signed sertifika uyarısını geç).
8080 portunu kullanmıyoruz çünkü Jenkins orada.

## 3. Admin şifresi

Kullanıcı adı `admin`. İlk şifre cluster'da bir Secret içinde üretilir:

```bash
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d; echo
```

Şifreyi hiçbir dosyaya yazma. Giriş yaptıktan sonra **User Info → Update Password** ile değiştirip
`kubectl -n argocd delete secret argocd-initial-admin-secret` ile ilk şifreyi silebilirsin.

## 4. Uygulamayı tanımla

```bash
kubectl apply -f gitops/argocd-app.yaml
```

Arayüzde `k8s-cicd-dev` uygulaması belirir ve `k8s/overlays/dev`'i `demo` namespace'ine sync eder.

Ön koşullar:
- **Repo public olmalı** (private ise Argo CD → Settings → Repositories'den credential eklenir).
- **GHCR paketleri public olmalı:** ilk push'ta private oluşurlar ve kind imajı çekemez
  (`ImagePullBackOff`). GitHub → profil → Packages → `product-service` / `order-service` →
  Package settings → Change visibility → Public.

## 5. CI'ı GitOps moduna al

GitHub → repo → **Settings → Secrets and variables → Actions → Variables → New repository variable**

- Name: `DEPLOY_MODE`
- Value: `gitops`

Bundan sonra `main`'e push'ta:
- `ci.yml` içindeki `gitops-update` job'u çalışır, `kustomize edit set image` ile tag'i günceller ve commit'ler.
- `cd.yml` kendini atlar (aynı değişkene bakar).

Jenkins kullanıyorsan orada da `DEPLOY_MODE=gitops` global ortam değişkenini tanımla (bkz. `jenkins/README.md`).

**CI neden sonsuz döngüye girmez?** `gitops-update`'in commit'i yalnızca `k8s/` altını değiştirir;
`ci.yml`'in `paths` filtresinde `k8s/` yok. Ayrıca `GITHUB_TOKEN` ile yapılan push'lar GitHub
kuralı gereği yeni workflow tetiklemez.

## 6. Yol A ile Yol B'yi aynı anda açma

`argocd-app.yaml`'da `selfHeal: true` var. Argo CD açıkken `kubectl set image` (cd.yml veya Jenkins)
ile verilen imaj, Git'tekinden farklı olduğu için birkaç saniye içinde **geri alınır**.
Canlıda bunu bilerek göstermek isterseniz:

```bash
kubectl -n demo set image deployment/product-service product-service=ghcr.io/muhammedali55/product-service:<başka-tag>
kubectl -n demo get deploy product-service -o jsonpath='{.spec.template.spec.containers[0].image}'; echo
# Birkaç saniye sonra tekrar bakın: Git'teki tag'e dönmüş olacak.
```

Yol A'ya geri dönmek için: `DEPLOY_MODE` değişkenini silin ve Argo CD uygulamasını kaldırın
(`kubectl -n argocd delete application k8s-cicd-dev` — varsayılan olarak cluster'daki kaynakları silmez).
