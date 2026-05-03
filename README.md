# TunierServer

Ein modularer Minecraft-Turnier-Server als Spigot/Paper-Plugin mit Teamverwaltung, mehreren Spielmodi, gemeinsamer Punktewertung, Scoreboard, GUIs und Turnier-Lobby.

## Überblick

Das Projekt organisiert ein komplettes Minecraft-Turnier auf einem Server. Teams werden zentral verwaltet, Spielmodi können per Command gestartet werden, Punkte werden pro Modus und insgesamt gespeichert, und Spieler erhalten je nach Phase Lobby-, Countdown- oder Spielverhalten.[1][2][3]

Die Hauptklasse initialisiert `TeamManager`, `ScoreManager`, `AchievementManager`, `BackpackManager` und `GameManager`, registriert Commands und Listener und setzt das Scoreboard direkt beim Start des Plugins auf.[1]

## Features

- Team-System mit persistenten Teams und Zuordnung über den `TeamManager`.[1]
- Globales Turnier-Scoreboard mit aktuellen Spielpunkten, Gesamtpunkten und Historie pro Modus.[3]
- Mehrere Spielmodi über einen zentralen `GameManager`, darunter Achievement Battle, Jump and Run, Item Collector, PvP/Hunger Game und Spleef Windcharge.[2][4]
- Lobby-Flow mit Teleport, Start-Countdown, Freeze-System und modusspezifischen Spielinfos vor dem Start.[2][1]
- Zusatzsysteme wie Team-Backpack, Stats-Ansichten und Sichtbarkeits-Toggle für bestimmte Modi.[1][4]

## Spielmodi

| Modus | Beschreibung |
|---|---|
| Achievement Battle | Teams erfüllen vorgegebene Achievements; der `AchievementManager` ist dafür an den Spielablauf angebunden.[2][1] |
| Jump and Run | Checkpoint-basiertes Parkour-Rennen mit Zielranking, BossBar-Timer, Rejoin-Handling und eigener Sichtbarkeitslogik.[4] |
| Item Collector | Teams sammeln und craften möglichst viele unterschiedliche Items für Punkte.[2] |
| PvP / Hunger Game | Als PvP-Modus im `GameManager` vorgesehen und für Hunger-Games-artige Runden gedacht.[2] |
| Spleef Windcharge | Eigener Modus mit separater Welt und vorgelagertem Pre-Game-Ablauf.[2] |

## Architektur

Der Kern des Projekts ist der `GameManager`. Er startet Modi, bereitet Welten vor, teleportiert Spieler, verwaltet Countdowns, setzt Spieler zurück und beendet laufende Spiele sauber.[2]

Wiederkehrende Spiel-Logik wird über `AbstractGameMode` gekapselt. Diese Basisklasse übernimmt Start-/Stop-Verhalten, den Sekundentick, Pausenbehandlung, allgemeine Countdown-Ansagen und das abschließende Ranking, während konkrete Modi nur ihre spezifische Logik implementieren müssen.[3]

Das `ScoreManager`-System speichert sowohl aktuelle Punkte als auch Gesamtpunkte und eine Verlaufshistorie pro Team und Modus. `addPoints(...)` aktualisiert dabei gleichzeitig Spielwertung, Gesamtwertung, Historie und Sidebar-Scoreboard.[5]

## Vorhandene Commands

Aus der Plugin-Initialisierung sind unter anderem diese Commands registriert:[1]

- `/team`
- `/game`
- `/skip`
- `/help`
- `/score`
- `/pause`
- `/resume`
- `/stats`
- `/allowmove`
- `/gameinfo`
- `/statsgui`
- `/bp` und `/backpack`

## Projektstruktur

```text
me.HenRun189.tunierServer
├── TunierServer.java
├── game/
│   ├── GameManager.java
│   └── modes/
│       ├── AbstractGameMode.java
│       ├── JumpAndRunMode.java
│       └── ...
├── score/
│   └── ScoreManager.java
├── team/
├── commands/
├── backpack/
├── achievement/
├── listeners/
└── world/
```

Die gezeigte Struktur entspricht den in den bereitgestellten Dateien sichtbaren Hauptkomponenten und ihrer Rolle im Projekt.[1][4][2][3][5]

## Entwicklungsstand

Der Code zeigt ein bereits gut ausgebautes Turnier-Framework mit mehreren spezialisierten Spielmodi, gemeinsamen Services und turnierweiter Punkteverwaltung.[1][2][5]

Gleichzeitig ist er klar auf weitere Modi und Regeln ausgelegt, weil neue Spielmodi einfach als eigene Klasse auf Basis von `AbstractGameMode` in den `GameManager` integriert werden können.[2][3]

## Setup

1. Das Plugin in ein Spigot- oder Paper-Projekt einbinden.
2. `plugin.yml` passend zu den registrierten Commands und Permissions pflegen.
3. Benötigte Welten wie `lobby`, `voidworld2` und `windchargeworld` auf dem Server bereitstellen, da sie im Spielablauf direkt referenziert werden.[1][4][2]
4. Server starten und Teams/Modi per Command konfigurieren.

## Roadmap

- Hunger-Games-/PvP-Modus weiter ausbauen, etwa mit Border-Logik, Survival-Punkten, Kill-Tracking und Loot-Chests.
- Weitere Admin-GUIs und übersichtlichere Turnier-Statistiken ergänzen.
- Konfigurationen wie Spielzeiten, Spawnpunkte und Punktewerte in Dateien auslagern.
- Mehr Persistenz für Match-Ergebnisse und Saisons ergänzen.

## Hinweise

Das Projekt ist klar auf einen privaten oder halbprivaten Event-/Turnier-Server zugeschnitten und kombiniert Lobby-Steuerung, Teamplay und mehrere Minigame-Regelwerke in einem gemeinsamen Plugin.[1][4][2]
