# Síťová hra Kostky (Klient–Server)

[![Server C](https://img.shields.io/badge/Server-C99%20%2F%20POSIX-blue?style=flat&logo=c)](https://en.wikipedia.org/wiki/C_(programming_language))
[![Client Java](https://img.shields.io/badge/Client-Java%2021%20%2F%20JavaFX-orange?style=flat&logo=openjdk)](https://openjfx.io/)
[![Build Tool](https://img.shields.io/badge/Build-CMake%20%26%20Maven-red?style=flat)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

Semestrální práce z předmětu **KIV/UPS (Úvod do počítačových sítí) **FAV ZČU v Plzni**.

Práce obsahuje kompletní síťovou multiplayer hru **Farkle (Kostky / 10 000)** s architekturou **Server (C)** a **klient (JavaFX)** komunikujícími přes UDP.

---

##  Pravidla hry (Farkle / Kostky)

- Hráči hází sadou 6 kostek a snaží se dosáhnout cílového skóre (např. 10 000 bodů).
- Bodované kombinace: jednotlivé jedničky (100 b), pětky (50 b), trojice, čtveřice, pětice a šestice stejných čísel, postupky a tři dvojice.
- Hráč musí po hodu odložit alespoň jednu bodovanou kostku a může buď:
  1. **Bankovat body** a předat tah dalšímu hráči.
  2. **Riskovat a házet znovu** se zbývajícími kostkami pro vyšší zisk.
- **Farkle:** Pokud při hodu nepadne žádná bodovaná kombinace, hráč přichází o všechny body nasbírané v daném kole a tah přechází na soupeře.

---

## Architektura systému

### 1. Server (`UPS-semestralka-server`)
- **Jazyk & Technologie:** C (C99), POSIX Berkley Sockets API, CMake.
- **Síťový model:** Zpracování klientských spojení přes neblokující sockety / I-O multiplexing (`select`/`poll`) a vlákna.
- **Správa stavu:**
  - `PlayerManager`: Autentizace, správa připojených klientů, heartbeat/ping-pong pro detekci výpadků.
  - `RoomManager`: Správa lobby, zakládání herních místností, připojování hráčů, start hry.
  - `GameLogic`: Striktní validace tahů, výpočet skóre, kontrola podmínek výhry a řízení tahů výhradně na serveru (ochrana proti cheatování).
- **Protokol:** Vlastní textový protokol definující zprávy pro přihlášení, seznam místností, hody kostkami, výběr kombinací a chybové stavy.

### 2. Herní Klient (`UPS-semestralka`)
- **Jazyk & Technologie:** Java 21, JavaFX, FXML, ControlsFX, Maven.
- **Architektura:** MVC (Model-View-Controller).
- **Síťové vlákno:** Asynchronní komunikace se serverem na pozadí, zabraňující zamrzání grafického rozhraní.
- **Obrazovky:**
  - Přihlašovací dialog (volba přezdívky, IP a portu serveru).
  - Lobby (přehled volných a probíhajících herních místností, zakládání her).
  - Herní stůl (interaktivní výběr kostek, vizualizace hodů, průběžná tabulka skóre, herní historie).

---

## 📂 Struktura repozitáře

```text
├── UPS-semestralka-server/      # Serverová část v C
│   ├── CMakeLists.txt           # Build konfigurace
│   ├── main.c                   # Vstupní bod, inicializace socketu
│   ├── commands.c / commands.h  # Zpracování příkazů protokolu
│   ├── config.c / config.h      # Konfigurace portů a limitů
│   ├── game_logic.c / .h        # Pravidla Farkle, bodování
│   ├── player.c / player.h      # Datové struktury hráčů
│   ├── protocol.c / protocol.h  # Serializace a deserializace paketů
│   └── room.c / room.h          # Správa místností
└── UPS-semestralka/             # Klient v JavaFX
    ├── pom.xml                  # Maven konfigurace a závislosti
    └── src/main/java/cz/zcu/kiv/ups/semestralka/
        ├── FarkleRules.java     # Lokální validace pro UI
        ├── GameModel.java       # Stav hry na klientu
        ├── Komunikace.java      # Socketové síťové vlákno
        ├── HraciMistnostControl.java # Ovládání herní scény
        ├── LobbyController.java # Ovládání lobby a seznamu her
        └── LoginController.java # Přihlašovací obrazovka
```

---

## Kompilace a spuštění

### Spuštění serveru (Linux / WSL / macOS):
```bash
cd UPS-semestralka-server
mkdir build && cd build
cmake ..
make
./UPS_semestralka_server
```

### Spuštění klienta (libovolný OS s Java 21+):
```bash
cd UPS-semestralka
./mvnw clean javafx:run
```

---

## 👤 Autor

- **Marek Jirušek**
