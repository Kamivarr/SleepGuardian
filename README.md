# SleepGuardian 🌙

SleepGuardian to natywna aplikacja mobilna na system Android, zaprojektowana w celu walki z nałogowym "scrollowaniem" telefonu w łóżku przed snem. Zamiast standardowej blokady, aplikacja wykorzystuje zaawansowane czujniki urządzenia do wymuszania na użytkowniku fizycznej aktywności, która pomaga przerwać szkodliwy nawyk.

## 🚀 Cel projektu
Projekt został stworzony w ramach przedmiotu *Projekt Zespołowy Aplikacji Mobilnej (PAM-2026)* na Politechnice Rzeszowskiej. Głównym celem jest wdrożenie kompletnego systemu klient-serwer, który w warunkach zbliżonych do profesjonalnego środowiska IT (CI/CD, Code Review, dokumentacja techniczna) realnie wykorzystuje natywne możliwości fizycznego smartfona.

## 🛠 Stos technologiczny
Projekt realizowany jest w architekturze **monorepo**:

* **Frontend (Android):** Kotlin, Jetpack Compose, Android SDK (Sensors, Camera API, Overlay, Foreground Service).
* **Backend (API):** Python, FastAPI, PostgreSQL (z obsługą JWT i autoryzacją za pomocą `bcrypt`).
* **Infrastruktura & DevOps:** Docker, Docker Compose.
* **Narzędzia:** Git (GitFlow), GitHub Actions (CI/CD), Swagger/OpenAPI.

## 📱 Główne funkcjonalności
* **Tryby Rygoru:** Wykorzystanie akcelerometru, latarki, czujnika zbliżeniowego i nakładek systemowych do wymuszania zmiany nawyków.
* **Synchronizacja z chmurą:** Zapisywanie sesji snu i statystyk w zewnętrznej relacyjnej bazie danych.
* **Architektura Offline-First:** Możliwość korzystania z aplikacji bez dostępu do sieci (lokalna synchronizacja).
* **Grywalizacja:** System rankingów i serii dni bez złamania rygoru.

## 📁 Struktura projektu
```text
/
├── android/               # Projekt aplikacji mobilnej (Kotlin / Android Studio)
├── backend/               # Serwer API (FastAPI) i plik Dockerfile
├── docs/                  # Dokumentacja techniczna projektu
├── docker-compose.yml     # Konfiguracja środowiska kontenerowego (Baza + API)
└── .github/               # Konfiguracja potoków CI/CD (GitHub Actions)
```
## ⚙️ Wymagania systemowe

### Frontend
* Android Studio *(wersja Hedgehog lub nowsza)*
* JDK 17+

### Backend
* Docker
* Docker Compose *(lokalna instalacja Pythona nie jest wymagana)*

---

## 🚀 Uruchomienie projektu (dla programistów)

Projekt wykorzystuje konteneryzację w celu uproszczenia konfiguracji środowiska.

### 1. Uruchomienie backendu i bazy danych

W głównym katalogu projektu uruchom:

```bash
docker compose up --build -d
```
* Serwer API będzie dostępny pod adresem: `http://localhost:8000`
* Interaktywna dokumentacja (Swagger UI) znajduje się pod adresem: `http://localhost:8000/docs`

> **Wskazówka:** Aby zatrzymać i wyczyścić środowisko (wraz z bazą danych), użyj polecenia: `docker compose down -v`

### 2. Uruchomienie aplikacji mobilnej (Android)

* Otwórz folder `/android` w środowisku Android Studio.
* Poczekaj na zakończenie synchronizacji narzędzia Gradle.

> **Uwaga dotycząca testów sprzętowych:** Jeśli testujesz aplikację na fizycznym smartfonie podłączonym przez kabel USB/Wi-Fi (wymagane m.in. dla rygoru Latarni Morskiej lub Akcelerometru), przejdź do pliku `ApiService.kt` i zamień adres `10.0.2.2` na lokalny adres IP komputera w sieci Wi-Fi (np. `192.168.1.15`).

## 👥 Zespół projektowy

* Mateusz Rudziński (177151)
* Nikola Słupska (177158)
* Kamil Śliwa (177165)