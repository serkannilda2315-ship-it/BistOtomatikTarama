# BIST Otomatik Tarama — ilk Android proje sürümü

Bu ZIP, Android Studio ile derlenebilen kaynak projedir; hazır APK değildir. APK'yı otomatik üretmek için GitHub Actions iş akışı eklendi.

## Önemli: Veri sunucusu gerekir
Uygulama bütün BIST sembollerinin OHLCV geçmişini almak için bir API bekler. Örnek uyumlu servis:
https://github.com/Armert-Labs/bist-data-service

Bu servis ücretsiz/gecikmeli kaynaklara dayalıdır; gerçek zamanlı veri garantisi vermez. Servisi kendi bilgisayarında/VPS'inde Docker ile çalıştırıp internete güvenli HTTPS üzerinden açman gerekir. Telefonda `localhost` kullanmak doğru değildir; localhost telefonun kendisidir. Sunucu adresini uygulama içindeki API adresine yaz.

Servis örnek uçları:
- `GET /symbols`
- `GET /history/{symbol}?period=3mo&interval=1d`
- `GET /history/{symbol}?period=5d&interval=5m`
- Gerekirse `X-API-Key` başlığı

## APK üretme (bilgisayarda)
1. Bu ZIP'i GitHub'da yeni bir depoya yükle.
2. `Actions` sekmesini aç; `Android APK` iş akışının tamamlanmasını bekle.
3. Tamamlanan çalışmanın `Artifacts` bölümünden `BistOtomatikTarama-debug-apk` dosyasını indir.
4. ZIP'i çıkar, `app-debug.apk` dosyasını Android telefona gönder ve kur. Android bilinmeyen kaynak izni isteyebilir.

Alternatif: Android Studio'da klasörü açıp `Build > Build APK(s)` seç.

## Sinyal koşulları
- RSI(14), aynı mumda 50 yukarı kesişimi.
- Kompozit = hisse kapanışı / XU100 kapanışı; kompozit RSI(14) 50 yukarı kesişimi.
- Kompozit oranının EMA14 veya EMA34'ü yukarı kesmesi.
- WaveTrend 10/21/4: WT1, WT2'yi yukarı keser.
- Hacim, 20 mumluk ortalamaya göre en az 3 kat.
- Tüm koşullar aynı son mumda olmalı.

## Sınırlamalar
- Bu ilk sürüm, veri sağlayıcının sembol ve history formatına bağlıdır; sağlayıcının `/symbols` yanıtı `symbols`, `items` veya `data` dizilerinden birini içermelidir.
- Sinyal hesapları Pine Script ile birebir doğrulanmış değildir; özellikle devam eden mum, veri zamanlarının hizalanması ve EMA/RSI başlangıç hesaplaması farklılık oluşturabilir. Canlı kullanım öncesi TradingView ile karşılaştırıp test et.
- Uygulama açık ve ekrandayken 5 dakikalık otomatik yenileme vardır. Uygulama kapalıyken sürekli tarama/uyarı henüz ekli değildir.
- Birkaç yüz hisseye ayrı history isteği atmak veri sağlayıcının hız sınırına takılabilir. Bu yüzden servis erişimi, istek sınırları ve tam hisse kapsamı doğrulanmalıdır.
- Bu yazılım yatırım tavsiyesi değildir.
