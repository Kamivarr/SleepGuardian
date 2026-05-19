# SleepGuardian 🌙

SleepGuardian to natywna aplikacja mobilna na system Android, zaprojektowana w celu walki z nałogowym "scrollowaniem" telefonu w łóżku przed snem. Zamiast standardowej blokady, aplikacja wykorzystuje zaawansowane czujniki urządzenia do wymuszania na użytkowniku fizycznej aktywności, która pomaga przerwać szkodliwy nawyk.

## 🚀 Cel projektu
Projekt został stworzony w ramach przedmiotu *Projekt Zespołowy Aplikacji Mobilnej (PAM-2026)* na Politechnice Rzeszowskiej. Głównym celem jest wdrożenie kompletnego systemu klient-serwer, który w warunkach zbliżonych do profesjonalnego środowiska IT (CI/CD, Code Review, dokumentacja techniczna) realnie wykorzystuje natywne możliwości smartfona.

## 🛠 Stos technologiczny
Projekt realizowany jest w architekturze **monorepo**:

* **Frontend (Android):** Kotlin, Jetpack Compose, Android SDK (Sensors, Overlay, Foreground Service).
* **Backend (API):** Python, FastAPI, PostgreSQL (z obsługą JWT i hashowania haseł).
* **Narzędzia:** Git (GitFlow), GitHub Actions (CI/CD), Swagger/OpenAPI.

## 📱 Główne funkcjonalności
* **Tryby Rygoru:** Wykorzystanie akcelerometru, latarki, czujnika zbliżeniowego i dźwięku do wymuszania zmiany nawyków.
* **Synchronizacja z chmurą:** Zapisywanie sesji snu i statystyk w zewnętrznej bazie danych.
* **Architektura Offline-First:** Możliwość korzystania z aplikacji bez dostępu do sieci.
* **Grywalizacja:** System rankingów i serii dni bez złamania rygoru.

## 📁 Struktura projektu
```text
/
├── android/         # Projekt aplikacji mobilnej (Kotlin)
├── backend/         # Serwer API (FastAPI)
├── docs/            # Dokumentacja techniczna projektu
└── .github/         # Konfiguracja CI/CD (GitHub Actions)
