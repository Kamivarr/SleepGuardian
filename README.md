# SleepGuardian 🌙

SleepGuardian to natywna aplikacja mobilna na system Android, zaprojektowana w celu walki z nałogowym "scrollowaniem" telefonu w łóżku przed snem. Zamiast standardowej blokady, aplikacja wykorzystuje zaawansowane czujniki urządzenia do wymuszania na użytkowniku fizycznej aktywności, która pomaga przerwać szkodliwy nawyk i zadbać o higienę snu.

## Cel projektu
Projekt został stworzony w ramach przedmiotu *Projekt Zespołowy Aplikacji Mobilnej (PAM-2026)* na Politechnice Rzeszowskiej. Głównym celem jest wdrożenie kompletnego systemu klient-serwer, który w warunkach profesjonalnego środowiska IT (CI/CD, Code Review, Dokumentacja) realnie wykorzystuje natywne możliwości fizycznego smartfona.

## Stos technologiczny
Projekt realizowany jest w rozdzielonej architekturze:

* **Frontend (Android):** Kotlin, Jetpack Compose, Retrofit, Android SDK (Sensors, Camera API, System Overlays, Foreground Services, SharedPreferences).
* **Backend (API):** Python, FastAPI, PostgreSQL (obsługa JWT i hashowanie `bcrypt`). Hosting: **Render**.
* **Infrastruktura & DevOps:** Git (zgodnie z metodologią pull-requests), GitHub Actions (CI/CD), Swagger/OpenAPI.

## Główne funkcjonalności
* **Zróżnicowane Tryby Rygoru:** Wykorzystanie akcelerometru (wymóg płaskiego odłożenia telefonu), stroboskopu LED, generatora drażniącego dźwięku na kanale Alarmu oraz systemowych nakładek (Draw Overlays).
* **Architektura Local-First (Offline):** Pełna funkcjonalność aplikacji nawet bez dostępu do sieci. Kary i sesje są buforowane lokalnie i synchronizowane w tle przy ponownym połączeniu.
* **Grywalizacja i Anty-Farming:** System "serc" i limitowanych dobowo "streaków" zapobiegający oszukiwaniu w aplikacji.
* **Integracja z chmurą:** Zapis historii snu i statystyk w zewnętrznej bazie danych PostgreSQL.

## Struktura projektu
```text
/
├── android/               # Projekt aplikacji mobilnej (Kotlin / Android Studio)
├── backend/               # Kod źródłowy serwera API (FastAPI)
├── docs/                  # Dokumentacja techniczna projektu
└── .github/               # Konfiguracja potoków CI/CD (GitHub Actions)
```
## Zespół projektowy

* Mateusz Rudziński (177151)
* Nikola Słupska (177158)
* Kamil Śliwa (177165)