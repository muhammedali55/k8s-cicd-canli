# Bölüm 2.04 — Argo CD ve GitOps: Yol A'dan Yol B'ye

> Atıf yapılan dosyalar: `gitops/argocd-app.yaml`, `gitops/README.md`, `.github/workflows/ci.yml` (`gitops-update` job'u),
> `.github/workflows/cd.yml`, `k8s/overlays/dev/kustomization.yaml`

> Bu bölümdeki Argo CD çıktıları bu sürümde **çalıştırılmadı**; dosyalar okunarak yazıldı ve "tahmini" diye işaretlendi.

## 1. Neden?

Yol A'nın sonunda (Bölüm 2.02) şunu gördük: cluster'da yeni SHA çalışıyor ama Git'te hâlâ `0.1.0` yazıyor. Biri
`kubectl apply -k` çalıştırsa ya da cluster'ı yeniden kursa eski sürüm geri gelir. Ayrıca CI'ın cluster'a erişim anahtarını
(kubeconfig / self-hosted runner) taşıması bir güvenlik yüzeyi. GitOps ikisini birden çözüyor: **Git, cluster'ın olması
gereken hâlidir**; cluster içindeki bir ajan (Argo CD) Git'i izler ve farkı kendisi kapatır.

## 2. Kavramlar

- **GitOps** `[senior]`: İstenen durum Git'te, bildirimsel olarak (YAML) duruyor. Değişiklik = commit, geri alma = `git revert`,
  denetim kaydı = `git log`.
- **Push vs pull** `[junior]`: Yol A'da CI cluster'a **iter** (`kubectl set image`). Yol B'de cluster Git'ten **çeker**.
- **Argo CD `Application`** `[junior]`: `gitops/argocd-app.yaml`. NEREDEN (`repoURL`, `targetRevision: main`,
  `path: k8s/overlays/dev`), NEREYE (`https://kubernetes.default.svc`, namespace `demo`), NASIL (`syncPolicy`).
- **Sync / OutOfSync / Healthy** `[junior]`: Sync durumu "cluster Git'le aynı mı?" sorusunun cevabı. Health ise "kaynaklar sağlıklı mı?"
  (ör. Deployment'ın pod'ları hazır mı?).
- **`automated` + `prune: true`** `[senior]`: Git'te yeni commit → kendiliğinden sync. Git'ten silinen kaynak cluster'dan da silinir.
- **`selfHeal: true`** `[senior]`: Cluster'da elle yapılan değişiklik (drift) Git'teki hâline geri çekilir.
- **`kustomize edit set image`** `[senior]`: `ci.yml`'deki `gitops-update` job'u overlay'deki `images:` bloğunu günceller ve
  `main`'e commit'ler. Eşleştirme sabit `BASE_IMAGE_PREFIX` (`ghcr.io/muhammedali55`) ile yapılıyor; yeni önek `newName`'e,
  SHA `newTag`'e yazılıyor. Bu sayede fork'ta da doğru girdi güncelleniyor.
- **Sürüm imajın içinde** `[senior]`: `APP_VERSION` build sırasında imaja gömülüyor. Hangi yolla deploy edilirse edilsin
  `curl http://localhost/info/products` çalışan imajın SHA'sını gösteriyor; Argo CD'nin ConfigMap'le uğraşmasına gerek yok.
- **Sonsuz döngü önlemi** `[architect]`: (1) `ci.yml`'in `push.paths` listesinde `k8s/` yok ve bot commit'i yalnızca `k8s/`
  altını değiştiriyor. (2) `GITHUB_TOKEN` ile yapılan push yeni bir workflow tetiklemiyor.
- **`DEPLOY_MODE` anahtarı** `[senior]`: Repo variable `DEPLOY_MODE=gitops` olunca `gitops-update` çalışıyor, `cd.yml` kendini atlıyor.
  Jenkins'te aynı adlı global değişken Deploy stage'ini atlatıyor.
- **HPA ile uyum** `[architect]`: order-service Deployment'ında `replicas` alanı yok. Git o alan hakkında bir şey söylemediği için
  Argo CD, HPA'nın belirlediği replika sayısını drift olarak görmüyor. Alan olsaydı ikisi aynı alanı sürekli birbirinden geri alırdı.

### Yol A ile Yol B farkı

| | Yol A — push (`cd.yml` / Jenkins Deploy) | Yol B — GitOps (Argo CD) |
| --- | --- | --- |
| Deploy'u kim yapar | CI/CD aracı, `kubectl set image` | Cluster içindeki Argo CD |
| Cluster kimlik bilgisi | CI tarafında (self-hosted runner / Jenkins `kubeconfig` credential'ı) | Yalnızca cluster içinde; CI'ın cluster'a erişimi yok |
| Git'teki tag | Değişmez (`0.1.0` kalır) → Git ile cluster ayrışır | Her deploy bir commit (`deploy(dev): imaj tag'i <sha> (...)`) |
| `/info/products` sürümü | Doğru (sürüm imajda) | Doğru (sürüm imajda) |
| Elle `kubectl` değişikliği | Kalıcı olur (drift birikir) | `selfHeal` geri alır |
| Bozuk sürümde | `rollout status` + otomatik `rollout undo`, job kırmızı | Otomatik geri alma **yok**; eski pod'lar readiness sayesinde yaşar, uygulama `Progressing`/`Degraded` görünür; çözüm `git revert` |
| Geri alma | `kubectl rollout undo` | `git revert <commit>` + push |
| Gecikme | CD bitince hemen | Argo CD'nin yoklama aralığı (varsayılan yaklaşık 3 dk) ya da elle Refresh |
| Ek bileşen | Self-hosted runner | Argo CD (cluster içinde) |
| Açma anahtarı | `DEPLOY_MODE` yok / farklı | `DEPLOY_MODE=gitops` |
| Jenkins desteği | Var (Deploy stage'i) | Tag'i Git'e yazan stage yok; Yol B'de bu işi Actions yapıyor |

> İkisini aynı anda açma: Argo CD açıkken Yol A'nın `set image`'ı birkaç saniye içinde selfHeal ile geri alınır.

## 3. Canlı akış

**Ön koşullar:** Cluster ayakta; repo ve GHCR paketleri **public** (Argo CD repo'yu, kind de imajları kimlik bilgisi olmadan çekecek).

1. **Argo CD'yi kur.**

   ```bash
   kubectl create namespace argocd
   kubectl apply -n argocd --server-side --force-conflicts \
     -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
   kubectl -n argocd rollout status deployment/argocd-server --timeout=180s
   ```

   Beklenen (tahmini): çok sayıda CRD, Deployment ve Service oluşturulur; sonunda `successfully rolled out`.
   Not: `stable` sabit bir sürüm değil; canlıdan önce bir sürüm etiketi seçmek daha güvenli (`gitops/README.md`).

2. **Arayüz ve şifre.**

   ```bash
   kubectl -n argocd get secret argocd-initial-admin-secret \
     -o jsonpath="{.data.password}" | base64 -d; echo
   kubectl -n argocd port-forward svc/argocd-server 8443:443
   ```

   `https://localhost:8443` → sertifika uyarısını geç → `admin` + yukarıdaki şifre. (8080 Jenkins'e ayrıldı.)

3. **Uygulamayı tanımla** (yeni terminalde).

   ```bash
   kubectl apply -f gitops/argocd-app.yaml
   kubectl -n argocd get applications.argoproj.io
   ```

   Beklenen (tahmini): `k8s-cicd-dev   Synced   Healthy`. İlk sync birkaç saniye sürebilir; arayüzde kaynak ağacı belirir
   (Deployment'lar, StatefulSet, HPA, Gateway, HTTPRoute'lar...).
   Dikkat: Git'teki tag `0.1.0`. Cluster'ı `setup-cluster.sh` ile kurduysan bu tag'li imajlar kind'a lokal olarak yüklenmiş durumda,
   sorun yok. Yol A ile SHA'ya geçmiştiysen Argo CD cluster'ı Git'teki `0.1.0`'a geri çeker.

4. **CI'ı GitOps moduna al.** GitHub → repo → Settings → Secrets and variables → Actions → **Variables** →
   New repository variable: `DEPLOY_MODE` = `gitops`. Self-hosted runner açık kalsa bile `cd.yml` artık atlanır.

5. **İzleme döngüsünü aç** (ayrı terminal):

   ```bash
   while true; do curl -s http://localhost/info/products; echo; sleep 2; done
   ```

6. **Bir değişiklik push et** (product-service'te zararsız bir yorum). Beklenen CI akışı (tahmini):
   `Değişen servisleri bul` → `product-service` (test + build + push) → **`GitOps — kustomization.yaml güncelle`**.
   Bu job'un logunda `→ ghcr.io/muhammedali55/product-service => ghcr.io/muhammedali55/product-service:<sha>` ve güncel
   `kustomization.yaml` içeriği görünür. CD workflow'unda `deploy` job'u **skipped** olur.

7. **Bot commit'ini gör.**

   ```bash
   git pull
   git log -1 --stat -- k8s/overlays/dev/kustomization.yaml
   ```

   Beklenen (tahmini): yazar `github-actions[bot]`, mesaj `deploy(dev): imaj tag'i <sha> (product-service)` ve yalnızca
   `k8s/overlays/dev/kustomization.yaml` değişmiş. CI bu commit için **tekrar tetiklenmedi** (Actions sekmesinde yeni CI yok).

8. **Argo CD'nin çekmesini izle.** Varsayılan yoklama birkaç dakika sürer; beklemek istemezsen arayüzde **Refresh**'e bas ya da:

   ```bash
   kubectl -n argocd annotate applications.argoproj.io k8s-cicd-dev argocd.argoproj.io/refresh=normal --overwrite
   kubectl -n demo rollout status deploy/product-service
   ```

   Beklenen (tahmini): arayüzde kısa bir `OutOfSync` → `Syncing` → `Synced`; Deployment yeni bir ReplicaSet ile rollout olur.
   İzleme döngüsünde sürüm yeni SHA'ya döner: `{"service":"product-service","version":"<sha>"}`.

## 4. Bozalım ve düzeltelim: "elle müdahale kalıcı olmaz"

1. Cluster'ı Git'ten saptır:

   ```bash
   kubectl -n demo scale deploy/product-service --replicas=0
   kubectl -n demo get deploy product-service -w
   ```

   Beklenen (tahmini): `READY 0/0` görünür, birkaç saniye içinde Argo CD Git'teki `replicas: 2`'yi geri uygular ve `2/2` olur.
   Arayüzde uygulama kısa süre `OutOfSync` görünür, sonra kendiliğinden `Synced` olur. (`-w`'yi Ctrl+C ile durdur.)

2. Karşılaştırma için aynısını order-service'te dene:

   ```bash
   kubectl -n demo scale deploy/order-service --replicas=4
   kubectl -n demo get deploy,hpa -l app=order-service
   ```

   Beklenen (tahmini): Argo CD buna **karışmaz**, çünkü Git'te order-service için `replicas` yazmıyor. Sayıyı HPA yönetiyor;
   yük yoksa HPA bir süre sonra sayıyı yeniden kendi hesabına (en az 2) indirir. Ders: Git'te yazmayan bir alanın sahibi Git değil.

3. Aynı şeyi imajla dene (`gitops/README.md`, 6. bölüm):

   ```bash
   kubectl -n demo set image deployment/product-service product-service=ghcr.io/muhammedali55/product-service:0.1.0
   sleep 10
   kubectl -n demo get deploy product-service -o jsonpath='{.spec.template.spec.containers[0].image}'; echo
   ```

   Beklenen (tahmini): imaj adı Git'teki SHA'ya geri dönmüş.

4. **Doğru yol:** değişiklik Git'ten yapılır. Bozuk bir sürüm Yol B'de nasıl geri alınır?

   ```bash
   git log --oneline -3 -- k8s/overlays/dev/kustomization.yaml
   git revert --no-edit <bozuk-deploy-commit'i>
   git push
   ```

   Bot commit'i geri alınır ve `kustomization.yaml` bir önceki tag'e döner. `k8s/` CI'ı tetiklemediği için Argo CD doğrudan
   sync eder. (Bölüm 2.02'deki `rollout undo` burada işe yaramaz: selfHeal onu da Git'teki hâle geri çeker.)

## 5. Sık sorulan sorular

**Argo CD bozuk sürümü kendisi geri alır mı?**
Hayır. Git'te ne yazıyorsa onu uygular. Readiness probe ve `maxUnavailable: 0` sayesinde eski pod'lar trafiği taşımaya
devam eder, ama geri almayı `git revert` ile bir insan (ya da ayrı bir otomasyon) yapar. Otomatik analizli geri alma
için Argo Rollouts gibi araçlar var; kapsam dışı.

**`gitops-update` job'unun push'u reddedilirse?**
`main`'de branch protection varsa bot doğrudan push edemez (`ci.yml`'deki yorum). O durumda job bir PR açacak şekilde
değiştirilir ya da bot'a istisna tanınır.

**Fork'ta çalışan katılımcı için tuzak var mı?**
İki şey var. (1) `gitops/argocd-app.yaml`'daki `repoURL`'i kendi fork'una çevir; yoksa Argo CD eğitmenin repo'sunu izler.
(2) `k8s/` altındaki `ghcr.io/muhammedali55/...` adlarını **değiştirme**: `gitops-update` job'u eşleştirmeyi sabit
`BASE_IMAGE_PREFIX: ghcr.io/muhammedali55` ile yapıyor ve senin önekini (`ghcr.io/<senin-adın>`) `newName`'e yazıyor.
Adları değiştirirsen `ci.yml`'deki `BASE_IMAGE_PREFIX`'i de aynı değere çekmen gerekir; yoksa `kustomize edit set image`
eşleşmeyen yeni bir girdi ekler ve Deployment'lar eski imajda kalır.

**Yol A'ya geri dönmek istersem?**
`DEPLOY_MODE` değişkenini sil ve `kubectl -n argocd delete application k8s-cicd-dev` çalıştır (varsayılan olarak cluster'daki
kaynakları silmez). Self-hosted runner açıksa bir sonraki CI'dan sonra `cd.yml` yine deploy eder.

**Secret'lar Git'teyse GitOps güvenli mi?**
Bizdeki `postgres-secret` bir demo kısayolu. Gerçek GitOps'ta Git'e ya şifreli hâli (Sealed Secrets) ya da yalnızca bir referans
(External Secrets Operator) girer; düz şifre asla.
