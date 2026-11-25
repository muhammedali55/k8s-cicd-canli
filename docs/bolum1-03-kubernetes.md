# Bölüm 1.03 — Kubernetes: kind, Kustomize, probe'lar ve Gateway API

> Atıf yapılan dosyalar: `cluster/kind-config.yaml`, `cluster/traefik-values.yaml`, `cluster/setup-cluster.sh`,
> `cluster/reset.sh`, `k8s/base/**`, `k8s/overlays/dev/kustomization.yaml`

## 1. Neden?

Container tek başına "çalışıyor", ama onu kim yeniden başlatacak, kaç kopya olacak, trafiği kim dağıtacak, bozuk sürüm
gelince ne olacak? Kubernetes bu soruların cevabını **istenen durum** (desired state) olarak YAML'da yazmamızı ve
sistemin bu durumu sürekli korumasını sağlıyor. Bu bölümde laptop'ta tek komutla sıfırdan cluster kurup iki servisi ve
PostgreSQL'i ayağa kaldıracağız.

## 2. Kavramlar

- **kind** `[junior]`: Kubernetes node'unu bir Docker container'ı olarak çalıştırır. `kind-config.yaml`: tek node, adı `k8s-cicd`,
  node imajı Kubernetes v1.37.0 (digest ile sabitlenmiş), host:80 → node:80 port eşlemesi.
- **Pod / Deployment / ReplicaSet** `[junior]`: Deployment "2 kopya product-service istiyorum" der, ReplicaSet bunu sağlar.
  Her sürüm değişikliği yeni bir ReplicaSet üretir; rollback için bu revizyonlar saklanır.
- **Service** `[junior]`: Sürekli değişen pod IP'lerinin önüne sabit bir DNS adı koyar: `product-service:8080`, `order-service:8080`.
  PostgreSQL için **headless** Service (`clusterIP: None`) kullanılıyor; bu, StatefulSet pod'una sabit bir ad verir.
- **StatefulSet + PVC** `[senior]`: PostgreSQL tek replika çalışıyor; verisi `volumeClaimTemplates` ile oluşan `data-postgres-0` PVC'sinde.
  İlk açılışta `postgres-init` ConfigMap'indeki SQL `products` ve `orders` veritabanlarını açıyor; tabloları her servis Flyway ile kuruyor.
- **ConfigMap / Secret** `[junior]`: Gizli olmayan ayarlar ConfigMap'te (`SPRING_DATASOURCE_URL`, `DEMO_BROKEN`, order'da
  `PRODUCT_SERVICE_URL`). Kullanıcı adı ve şifre `postgres-secret` Secret'ında. Secret `secretGenerator` ile üretiliyor ve
  değerler demo amaçlı (`postgres/postgres`); dosyadaki yorum da bunu açıkça söylüyor. `APP_VERSION` ConfigMap'te **yok**, imajın içinde.
- **Probe'lar** `[senior]`:
  `startupProbe` (Spring yavaş açılır; 5 sn × 60 = en fazla 5 dk), `livenessProbe` → `/actuator/health/liveness`
  (takılırsa yeniden başlat), `readinessProbe` → `/actuator/health/readiness` (hazır değilse Service trafik göndermez).
- **Readiness grubu** `[architect]`: İki serviste de `readinessState,db,demoBroken`. Veritabanı yoksa ya da `DEMO_BROKEN=true`
  ise pod trafik almıyor. Bu bileşenler liveness'ta **yok**: DB kesintisi pod'ları yeniden başlatma döngüsüne sokmuyor.
- **Rolling update: `maxUnavailable: 0`, `maxSurge: 1`** `[senior]`: Yeni pod hazır olmadan eskisi öldürülmüyor.
  Bölüm 2.02'deki bozuk sürüm senaryosunun temeli bu.
- **Resources** `[junior]`: Her container'da `requests` (100m / 256Mi) ve `limits` (250m / 512Mi) var. kind tek node olduğu için düşük tutuldu.
- **HPA ve `replicas` alanının yokluğu** `[senior]`: order-service için CPU hedefi %70, 2–5 replika. Deployment'ta `replicas`
  alanı bilerek **yok**: olsaydı her `kubectl apply` ya da Argo CD sync'i HPA'nın kararını ezip sayıyı 2'ye çekerdi. CPU ölçümü için
  metrics-server gerekiyor; `setup-cluster.sh` onu kind'a özel `--kubelet-insecure-tls` ile kuruyor (yalnızca lokalde!).
- **Kustomize base/overlay** `[senior]`: `k8s/base` ortamdan bağımsız; `k8s/overlays/dev` namespace'i (`demo`) ve
  imaj tag'lerini (`images:` bloğu) belirliyor. Pipeline'lar ve Argo CD **yalnızca** bu bloğu değiştiriyor.
- **Gateway API** `[senior]`: `GatewayClass` (altyapı sağlayıcısı: Traefik) → `Gateway` (giriş kapısı `demo-gateway`,
  listener portu 8000) → `HTTPRoute` (yol kuralları). Rol ayrımı: Gateway'i platform ekibi, kendi HTTPRoute'unu uygulama ekibi yönetir.
- **HTTPRoute `URLRewrite`** `[senior]`: `/info/products` ve `/info/orders` istekleri (`Exact` eşleşme) servise giderken yolu
  `/api/info` olarak yeniden yazılıyor (`ReplaceFullPath`). Böylece sürüm bilgisi port-forward gerekmeden dışarıdan görünüyor.
- **Neden ingress-nginx değil?** `[architect]`: Kubernetes topluluğu Kasım 2025'te ingress-nginx projesinin emekliye
  ayrılacağını duyurdu ve en iyi çaba (best-effort) bakımı **Mart 2026'da sona erdi**. Bu tarihten sonra yeni sürüm,
  hata düzeltmesi ve **güvenlik yaması** çıkmıyor (2025'teki kritik "IngressNightmare" açıkları bu riskin ne kadar gerçek olduğunu
  gösterdi). Mevcut kurulumlar çalışmaya devam ediyor, ama yamasız bir kenar bileşenini (edge) internete açmak kabul edilebilir
  değil. `Ingress` API'si Kubernetes'ten silinmedi, yalnızca donduruldu; yeni özellikler Gateway API'ye geliyor. Bu yüzden
  SPEC "ingress-nginx KULLANILMAZ" diyor ve implementasyon olarak Traefik (Helm) seçildi. Gateway API standart olduğu için
  yarın Traefik yerine başka bir implementasyona geçmek, büyük ölçüde `gatewayClassName`'i değiştirmek demek.

### Trafiğin yolu

```
curl http://localhost/api/products
  → host:80 → (kind extraPortMappings) → node:80
  → Traefik pod (hostPort 80 → container 8000, "web" entrypoint)
  → Gateway demo-gateway (listener 8000)
  → HTTPRoute product-route (PathPrefix /api/products)
  → Service product-service:8080 → hazır (Ready) pod'lardan biri:8080

curl http://localhost/info/products
  → ... → HTTPRoute product-route (Exact /info/products, URLRewrite → /api/info)
  → Service product-service:8080 → GET /api/info
```

## 3. Canlı akış

1. **İmajları build et** (Bölüm 1.02'de yaptıysan atla). Sürümü ekranda görmek için bir build-arg ver:

   ```bash
   docker build --build-arg APP_VERSION=demo1 -t product-service:local services/product-service
   docker build --build-arg APP_VERSION=demo1 -t order-service:local   services/order-service
   ```

2. **Overlay'in ürettiği son YAML'a bak** (cluster'a dokunmadan):

   ```bash
   kubectl kustomize k8s/overlays/dev | grep -E "image:|namespace: demo|replicas:" | sort | uniq -c
   ```

   Beklenen (tahmini sayılar): `image: ghcr.io/muhammedali55/product-service:0.1.0`, `image: ghcr.io/muhammedali55/order-service:0.1.0`,
   `image: postgres:17`, kaynakların aldığı `namespace: demo` satırları ve yalnızca **iki** `replicas:` satırı
   (product-service `2`, postgres `1`). order-service için `replicas` yok. `APP_VERSION` hiç geçmiyor.

3. **Sıfırdan cluster.** `IMAGE_SOURCE` varsayılan olarak `local`; lokal imajlar kind'a yükleniyor.

   ```bash
   ./cluster/reset.sh
   ```

   Beklenen renkli başlıklar:
   `[Adım 1] Gerekli araçlar` → `[Adım 2] kind cluster` → `[Adım 3] Gateway API CRD'leri (v1.7.0)` →
   `[Adım 4] Traefik (chart 43.0.0)` → `[Adım 5] metrics-server` → `[Adım 6] namespace` →
   `[Adım 7] Lokal imajlar kind'a yükleniyor` (`product-service:local -> ghcr.io/muhammedali55/product-service:0.1.0 yüklendi`) →
   `[Adım 8] kubectl apply -k` (ve hemen ardından `rollout restart`) → `[Adım 9] Pod'ların hazır olması` →
   `[Adım 10] Duman testi` (ürün JSON'u) → `[Adım 11] Özet` ve en sonda `Toplam süre: X dk Y sn`.
   Bu kurulum (`setup-cluster.sh`) gerçek ortamda çalıştırıldı ve exit 0 ile bitti.

   Anlatılacak iki ayrıntı:
   - **`rollout restart` neden var?** Lokal imajın tag'i (`0.1.0`) değişmeden içeriği değişebilir. Deployment tanımı aynı kaldığı
     için Kubernetes yeni imajı fark etmez; script pod'ları yeniden başlatarak yeni yüklenen imajı kullanmalarını sağlıyor.
   - **`postgres:17` uyarısı** görürsen sorun yok: `kind load` bazen çok platformlu imajı aktaramıyor, script uyarı verip devam ediyor
     ve cluster imajı internetten çekiyor.
   - `IMAGE_SOURCE=registry ./cluster/reset.sh` imajları GHCR'dan çeker, ama yalnızca overlay'deki tag GHCR'da varsa çalışır.
     `0.1.0` tag'ini hiçbir pipeline push etmiyor; bu yüzden ilk kurulumda varsayılanı (`local`) kullan.

4. **Ne kuruldu?**

   ```bash
   kubectl get pods,svc,hpa -n demo
   kubectl get gateway,httproute -n demo
   ```

   Beklenen (tahmini biçim): `postgres-0`, 2 `product-service-…` ve 2 ile 5 arası `order-service-…`, hepsi `1/1 Running`.
   Service'ler: `postgres` (ClusterIP `None`), `product-service`, `order-service`. HPA `order-service`: `cpu: x%/70%`, `MINPODS 2`, `MAXPODS 5`.
   Gateway `demo-gateway`: `CLASS traefik`, `PROGRAMMED True`. HTTPRoute'lar: `product-route`, `order-route`.

5. **HPA'yı izle: Spring'in açılış CPU'su.**

   ```bash
   kubectl get hpa order-service -n demo -w
   ```

   Gerçek gözlem: ilk açılışta HPA order-service'i **5 replikaya** çıkardı, sonra yavaş yavaş düşürdü. Sebep: CPU isteği
   `100m`, limiti `250m`. Spring Boot açılırken limite kadar CPU yakıyor, bu da isteğin %250'si demek. HPA bunu "yük var" sanıyor.
   Ölçek küçültme varsayılan olarak 5 dakikalık bir dengeleme penceresiyle yavaş ilerliyor. Ders: HPA "uygulama ne yapıyor"
   bilmez, yalnızca metriğe bakar. (Ctrl+C ile çık.)

6. **Uçtan uca istekler.** Aşağıdaki çıktılar cluster'da alınan **gerçek** çıktılar (sürüm build-arg'a göre değişir).

   ```bash
   curl -s http://localhost/api/products
   ```
   → `[{"id":1,"name":"Klavye","price":750.00,"stock":10},{"id":2,"name":"Mouse","price":350.00,"stock":25},{"id":3,"name":"Monitör","price":5200.00,"stock":5}]`

   ```bash
   curl -s http://localhost/info/products
   curl -s http://localhost/info/orders
   ```
   → `{"service":"product-service","version":"<sürüm>"}` ve `{"service":"order-service","version":"<sürüm>"}`
   (`<sürüm>` = build-arg ile verdiğin değer; vermediysen `local`).

   ```bash
   curl -s -i -X POST http://localhost/api/orders \
     -H 'Content-Type: application/json' -d '{"productId":1,"quantity":2}'
   ```
   → `201`, gövde `{"id":1,"productId":1,"quantity":2,"totalPrice":1500.00,"status":"CREATED","createdAt":"..."}`.
   `Location: /api/orders/1` başlığı da gelir (kod ve `OrderControllerTest` bunu garanti ediyor).

   ```bash
   curl -s -i -X POST http://localhost/api/orders \
     -H 'Content-Type: application/json' -d '{"productId":3,"quantity":99}'
   ```
   → `409`, gövde:
   `{"detail":"Yetersiz stok: ürün 3 için istenen 99 adet karşılanamadı","instance":"/api/orders","status":409,"title":"Conflict","order":{"id":2,...,"totalPrice":514800.00,"status":"REJECTED",...}}`.
   Vurgula: reddedilen sipariş de kaydedildi ve `order` alanında döndü. Mesajda mevcut stok yok; bu bilgi product-service'te kalıyor.

   ```bash
   curl -s -i -X POST http://localhost/api/orders \
     -H 'Content-Type: application/json' -d '{"productId":999,"quantity":1}'
   ```
   → `422`, `{"detail":"Ürün bulunamadı: 999","instance":"/api/orders","status":422,"title":"Unprocessable Content"}`.
   Neden 404 değil? `POST /api/orders` adresi var; sorun gövdedeki `productId`'de (`GlobalExceptionHandler`'daki yorum).

   ```bash
   curl -s http://localhost/api/orders
   ```
   → (tahmini) iki sipariş: biri `CREATED`, biri `REJECTED`.

7. **Loglar.**

   ```bash
   kubectl logs -n demo deploy/order-service --tail=5
   ```

   Beklenen (tahmini): `POST /api/orders 201 …ms` gibi tek satırlık istek logları ve 5 saniyede bir
   `GET /actuator/health/readiness 200 …ms` (kubelet'in probe istekleri; probe'ların gerçekten çağrıldığının kanıtı).

8. **Readiness'i doğrudan sor.** Actuator uçları Gateway'den yayınlanmıyor; port-forward ile bakıyoruz:

   ```bash
   kubectl -n demo port-forward deploy/order-service 18082:8080 &
   sleep 2 && curl -s http://localhost:18082/actuator/health/readiness; echo; kill %1
   ```

   Beklenen (gerçek): `{"status":"UP"}`. order-service'te `show-components` açık olmadığı için bileşenler listelenmiyor;
   product-service'te aynı uç `db`, `demoBroken`, `readinessState` bileşenlerini de gösterir.

9. **Probe'ları gözle.**

   ```bash
   kubectl describe pod -n demo -l app=product-service | grep -E "Liveness|Readiness|Startup"
   ```

   Beklenen (tahmini): üç probe da `http-get http://:http/actuator/health/...` adresleriyle.

## 4. Bozalım ve düzeltelim: product-service gidince order-service ne der?

1. Ayrı bir terminalde trafiği izle:

   ```bash
   while true; do curl -s -o /dev/null -w "%{http_code} " -X POST http://localhost/api/orders \
     -H 'Content-Type: application/json' -d '{"productId":2,"quantity":1}'; sleep 1; done
   ```

   Beklenen: `201 201 201 ...`

2. product-service'i sıfıra indir:

   ```bash
   kubectl -n demo scale deploy/product-service --replicas=0
   ```

   Beklenen (tahmini): döngü birkaç saniye içinde `503 503 ...` döner. Gövde:
   `{"title":"Service Unavailable","status":503,"detail":"product-service'e şu anda ulaşılamıyor, lütfen biraz sonra tekrar deneyin",...}`.
   Anlat: Service'in arkasında hazır pod yok → bağlantı kurulamıyor → `ProductClient` bunu `ResourceAccessException` olarak
   görüyor → 503. 2 saniyelik bağlantı/okuma timeout'u sayesinde order-service'in thread'leri takılı kalmıyor.
   order-service'in readiness'i product-service'e bağlı değil; order-service trafikte kalıp anlamlı bir hata dönüyor.

3. Düzelt:

   ```bash
   kubectl -n demo scale deploy/product-service --replicas=2
   kubectl -n demo rollout status deploy/product-service
   ```

   Beklenen: pod'lar hazır olunca döngü tekrar `201`'e döner. Döngüyü Ctrl+C ile durdur.

**Alternatif bozma (Gateway):** `k8s/base/gateway/gateway.yaml`'daki listener portu `8000`, Traefik'in `web` entrypoint
portuyla (`traefik-values.yaml`) aynı olmak zorunda. Farklı olsaydı Gateway `PROGRAMMED` olmaz, `curl http://localhost/...`
cevap vermezdi. Kontrol komutu: `kubectl describe gateway demo-gateway -n demo` (Conditions bölümü).

## 5. Sık sorulan sorular

**Neden tek node? Gerçek cluster'da da böyle mi?**
Hayır. kind tek node ile laptop'ta hafif kalıyor. Gerçek ortamda birden çok node ve `podAntiAffinity` /
`topologySpreadConstraints` ile kopyalar farklı node'lara dağıtılır. Traefik'in `hostPort` kullanması da tek node'a özel bir kısayol.

**Liveness ile readiness arasındaki fark tam olarak ne?**
Liveness "takıldın mı, yeniden başlatayım mı?" diye sorar, readiness "trafik alabilir misin?" diye. `db` ve `demoBroken`
yalnızca readiness grubunda olduğu için DB kesintisi ya da bozuk sürüm pod'u yeniden başlatmıyor, sadece trafikten çıkarıyor.
DB kontrolü liveness'a konsaydı, bir veritabanı kesintisi bütün pod'ları sonsuz yeniden başlatma döngüsüne sokardı.

**order-service'te `replicas` yoksa Deployment ilk oluşturulduğunda kaç pod açılıyor?**
Alan verilmezse Kubernetes varsayılan olarak 1 kullanıyor; HPA hemen devreye girip sayıyı en az 2'ye çıkarıyor. Sonrasında
sayıyı yalnızca HPA değiştiriyor; `kubectl apply -k` ve Argo CD bu alana dokunmuyor.

**Secret'ı Git'e koymuşuz, bu güvenli mi?**
Hayır, bu bilerek yapılmış bir demo kısayolu; `k8s/base/kustomization.yaml`'daki yorum da bunu söylüyor. Gerçek ortamda
Sealed Secrets, External Secrets Operator ya da bulutun secret yöneticisi kullanılır.

**Ingress API'si kaldırıldı mı? Eski Ingress YAML'larım çalışmaz mı?**
Hayır, `Ingress` API'si Kubernetes'te duruyor. Emekliye ayrılan, onun en yaygın implementasyonu olan **ingress-nginx
controller**'ı. Ingress kaynaklarını başka bir controller da (ör. Traefik) okuyabilir; ama yeni projelerde yön Gateway API.
Biz Traefik'te klasik Ingress sağlayıcısını bilerek kapattık (`traefik-values.yaml`: `kubernetesIngress.enabled: false`).
