# Kubernetes & Helm & Kustomize Mimari Yapısı

Bu projede endüstri standardı olan **"Hibrit" Helm + Kustomize** modeli kullanılmıştır.
Kubernetes üzerinde hiçbir Container/Docker build işlemine girmeden, mevcut imajları (`spring-datadog-lab/auth-service:latest` vb.) veya sizin CI/CD üzerinden yükleyeceğiniz imajları kullanacak şekilde altyapı kurulmuştur.

## 1. Kustomize (Altyapı)
`k8s/kustomize/` altında bulunur.
Kustomize'ın `helmCharts` özelliği kullanılarak 3. parti (external) altyapı servisleri Kubernetes üzerine kurulur:
- **HashiCorp Vault** (ve Vault Secrets Operator)
- **Bitnami Kafka** (KRaft Mode)
- **Bitnami PostgreSQL**

Kustomize bu Chart'ları dinamik olarak indirip `dev` veya `prod` overlay'leri üzerinden yamalayarak (patch) tek bir YAML dosyası halinde Kubernetes'e uygular.

## 2. Helm (Mikroservisler)
`k8s/helm/spring-microservice` altında bulunur.
Kendi yazdığımız Spring Boot servisleri (auth-service, api-gateway vb.) için her defasında aynı YAML manifestlerini yazmak yerine **Generic (Genel) bir Helm Chart** oluşturulmuştur.
Bu Helm chart'ı, her mikroservis için `k8s/kustomize/overlays/dev/values/` altındaki kendi parametre dosyasını okuyarak Kubernetes'e deploy edilir.

## 3. Kurulum ve Çalıştırma

Tüm altyapıyı ve mikroservisleri Kubernetes'e göndermek için proje kök dizininde bulunan bash scriptini çalıştırabilirsiniz (Eğer Windows kullanıyorsanız WSL üzerinden veya Git Bash ile çalıştırabilirsiniz):

```bash
./deploy-k8s.sh
```

**Not:** Bu script önce Kustomize ile altyapıları (Vault, Kafka vb.) ayağa kaldırır, ardından Helm komutlarıyla `spring-microservice` isimli yerel Chart'ımızı kullanarak bütün servislerinizi deploy eder. 
Servis imajlarınız Kubernetes (veya Containerd/Rancher) ortamınızda henüz derlenmediği için Pod'lar `ErrImagePull` veya `ImagePullBackOff` durumuna düşebilir. Pod'ların başarıyla ayağa kalkması için imajlarınızı Kubernetes cluster'ınızın erişebileceği bir registry'ye atmalı veya Rancher Desktop içerisinde derlemelisiniz.
