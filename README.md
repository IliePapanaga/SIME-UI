# Agenda

A clean, minimalist native Android client for Moldova's school portal ([agenda.sime.md](https://agenda.sime.md)).  
Designed with an OLED black **Liquid Glass** interface focused on fast daily readability for students and parents.

---

## Instalare rapidă (Android) / Quick Install

1. Deschide pagina [**Releases**](../../releases/latest) a acestui repository.
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
