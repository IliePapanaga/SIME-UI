# Agenda

A clean, minimalist native Android app and installable **Vercel Web / PWA** client for Moldova's school portal ([agenda.sime.md](https://agenda.sime.md)).  
Designed with an OLED black **Liquid Glass** interface focused on fast daily readability for iPhone, Android, iPad/tablets, and PC.

---

## Versiune Web & iPhone (Vercel / PWA)

Oricine poate accesa sau instala **Agenda** direct din browser (pe **iPhone**, **Android**, **Tabletă** sau **PC**) fără cabluri și fără App Store / Play Store:

1. Intră pe [vercel.com](https://vercel.com) $\rightarrow$ **Add New Project** $\rightarrow$ selectează repository-ul **`IliePapanaga/SIME-UI`** $\rightarrow$ apasă **Deploy** (zero configurare necesară; folosește un domeniu gratuit `*.vercel.app`).
2. **Instalare pe iPhone / iPad**: Deschide linkul `.vercel.app` în Safari $\rightarrow$ apasă butonul **Share** $\rightarrow$ **Add to Home Screen** (*Adaugă pe ecranul principal*).
3. **Instalare pe Android / PC**: Deschide linkul în Chrome / Edge și apasă **Instalează aplicația** (sau din **Dosar & Setări**).

---

## Instalare aplicație nativă (Android APK) / Native Android Install

1. Deschide pagina [**Releases**](../../releases/latest) a acestui repository (sau linkul direct [`Agenda.apk`](../../releases/latest/download/Agenda.apk)).
2. Descarcă fișierul **`Agenda.apk`** direct pe telefon.
3. Deschide fișierul descărcat și confirmă instalarea din surse necunoscute (*Install unknown apps*), dacă Android solicită acest lucru.
4. Autentifică-te cu contul tău obișnuit de **Elev** sau **Părinte** de pe `agenda.sime.md`.

---

## Funcționalități principale / Key Features

- **Interfață Negru OLED & Liquid Glass**: contrast curat, cifre tabulare (`tnum`) și zero zgomot vizual (cu opțiuni pentru **Negru OLED**, **Întunecat** și **Luminos** în Dosar).
- **Orar pe săptămâni cu sincronizare automată**: navigare săptămânală (`<` / `>`) cu preluare automată din calendarul SIME și fereastră de detalii pentru subiectul lecției, teme și link online.
- **Reușita & simulator de medie live**: toate notele cu date exacte (atingerea oricărei note deschide direct lecția corespunzătoare în **Orar**), medie live pe disciplină, medie generală și simulator opțional (**Simulare medie**).
- **Absențe sortate pe zile**: filtrare (Toate / Nemotivate / Motivate), sortare și sumar pe zile sau pe discipline.
- **Mesaje & notificări**: mesaje de la profesori legate direct de lecțiile din **Orar**, plus verificări periodice în fundal via `WorkManager`.
- **Schimbare rapidă a elevului**: suport pentru conturile de părinți cu mai mulți copii.

---

## Confidențialitate și securitate / Privacy

- Aplicația comunică **direct** cu serverul oficial `https://agenda.sime.md` — nu există niciun server intermediar.
- Datele de autentificare și starea sesiunii sunt salvate **exclusiv local** pe dispozitiv, criptate hardware prin `EncryptedSharedPreferences` (Android Keystore).

---

## Build din cod sursă / Build from Source

Cerințe: **JDK 17** și **Android SDK (API 34)**.

```bash
# Rulează testele unitare
./gradlew testDebugUnitTest

# Construiește APK-ul de producție (cu R8 minification activat)
./gradlew assembleRelease
```

Fișierul rezultat este generat în `app/build/outputs/apk/release/app-release.apk`.

---

## Licență / License

Acest proiect este distribuit sub licența [MIT](LICENSE).
