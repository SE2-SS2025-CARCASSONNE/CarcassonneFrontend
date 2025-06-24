![App Logo](docs/images/logo_pxart.png)

# 📘 Projekt: Carcassonne Android


## 📑 Inhaltsverzeichnis

- [1. Game Specification](#1-game-specification)
  - [1.1 Spielübersicht](#11-spielübersicht)
  - [1.2 Spiellogik](#12-spiellogik)
  - [1.3 Systemdesign](#13-systemdesign)
  - [1.4 UIUX Anforderungen](#14-uiux-anforderungen)
  - [1.5 Spielzug-Ablauf](#15-spielzug-ablauf)
  - [1.6 Spielende](#16-spielende)
- [2. App UI - Screenshots](#2-app-ui---screenshots)
- [3. Weiterführende Links](#3-weiterführende-links)

---

## 1. Game Specification

### 1.1 Spielübersicht

**Titel:** Carcassonne Android  
**Plattform:** Android (Kotlin mit Jetpack Compose)  
**Backend:** Kotlin Spring Boot  
**Datenbank:** PostgreSQL  
**Multiplayer:** Nur Mehrspieler (2 – 4 Spieler)

#### Projektziel:
Wir entwickeln eine digitale, mehrspielerfähige Version des Brettspiels Carcassonne, bei der die Spieler abwechselnd Landschaftskarten legen, Städte, Straßen und Klöster bauen und Meeples auf Felder setzen, um Punkte zu sammeln. Das Spiel endet, wenn alle Karten gelegt sind. Der Spieler mit den meisten Punkten gewinnt.

Dieses Projekt umfasst:

- Eine voll interaktive Android-Benutzeroberfläche
- Echtzeit-Mehrspielerfunktion (kein Bot)
- Synchronisierung des Spielstatus zwischen Spielern
- Sicheres Backend für Spielregeln, Punktevergabe und Datenspeicherung
- Persistente Speicherung von Spieler- und Spieldaten

---

### 1.2 Spiellogik

#### ▶️ Kartenplatzierung

**Was Spieler tun:**

- In ihrem Zug ziehen Spieler eine zufällige Karte aus dem Stapel.
- Sie können die Karte rotieren (0°, 90°, 180°, 270°).
- Die Karte muss an eine bestehende Karte angrenzen und alle Kanten müssen passend sein (z.B. Stadt an Stadt).
- Wenn die Karte nicht gelegt werden kann, wird sie ans Ende der Kartendecks gegeben, wird also am Ende des Spiels neu zum Legen angeboten und der Spieler darf eine neue Karte ziehen.

**Technische Hinweise:**

- Der Kartenstapel wird im Vorfeld definiert und mit Seed-basierter Zufallslogik generiert.
- Das Spielfeld wird als dynamisches Koordinatensystem (x, y) verwaltet.
- Karten werden in einer Map-Struktur gespeichert und können in alle Richtungen erweitert werden.

#### ▶️ Meeple-Platzierung

**Was Spieler tun:**

- Nach dem Platzieren einer Karte kann der Spieler optional einen Meeple auf ein Segment der neu platzierten Karte setzen.
- Mögliche Funktionen des Meeples: Ritter (Stadt), Wegelagerer (Straße) oder Mönch (Kloster).
- Die Platzierung eines Meeples auf einem Segment ist nur erlaubt, wenn das entsprechende Feature (Stadt, Straße) nicht bereits durch einen Meeple auf einer verbundenen Karte belegt ist.

**Technische Hinweise:**

- Jeder Spieler startet mit 7 Meeples.
- Vor Platzierung muss geprüft werden, ob das gesamte Feature schon besetzt ist (per DFS - Depth-First Search - Tiefensuche).

#### ▶️ Speicherung der Karteninstanz

- Karten-ID
- Platzierung in Map (Koordinaten)
- Rotation
- Spieler der sie gelegt hat
- Info ob Meeple gelegt wurde
TODO- Falls ja, wohin (Segment – z.B. Stadt, Straße, Kloster und falls es mehrere Möglichkeiten gibt: Nord, Ost, West, Süd – z.B. nötig bei Karte Straßenkreuzung)

#### ▶️ Punktevergabe

**Die Punktevergabe erfolgt während des Spiels bei vollständigen Features (fertige Städte, Straßen, vollständig umbaute Kloster) nach dieser Logik:**

- **Stadt (vollständig):** 2 Punkte pro Karte, +2 pro Wappen
- **Straße (vollständig):** 1 Punkt pro Karte
- **Kloster (vollständig):** 9 Punkte (inkl. 8 umliegende Karten)

**Technische Hinweise:**

- Punkteberechnung erfolgt serverseitig nach jedem Zug.
- Meeples kehren in den Vorrat der betroffenen Spieler zurück, sobald ein Feature gewertet wurde.

---

### 1.3 Systemdesign

#### ▶️ Backend (Spring Boot)

**Aufgaben des Backends:**

- Spielräume erstellen, beitreten, starten
- Spielstatus speichern und validieren
- Spielregeln und Punktevergabe durchsetzen
- Kommunikation mit Frontend via REST & WebSocket
- Nutzeridentifikation (optional mit Profilen)

**Wichtige Entitäten:**

- **User**: Spielerprofil mit ID, Username, Password und Highscore
- **Game**: Spielsession mit GameCode, Status und Gewinner

#### ▶️ Datenbank (PostgreSQL)

**Anforderungen:**

- Persistente Spielerspeicherung
- Spielverlauf und Statistiken

**Tabellen (per Spring JPA generiert):**
- `users`: Spieler mit ID, Name, Passwort (wird gehasht gepeichert) und Highscore
- `games`: Spiele mit ID, Erstellungszeitpunkt, Status, Spielcode und Gewinner

#### ▶️ Frontend (Jetpack Compose)

**Funktionen:**

- Authentifizierung, Lobby betreten, Spiel starten oder beitreten per Game-ID
- Spielerübersicht inklusive aktueller Punkte in Echtzeit
- Scroll- und zoombares Spielfeld
- Karten ziehen inklusive Cheating-Funktion zum unberechtigten Erhalt einer neuen Karte
- Karte rotieren und anschließend platzieren
- Meeple platzieren oder ohne Platzierung den Zug beenden
- Entlarven des Cheatens durch die nicht aktiven Spieler inklusive Punkteabzug für den entlarvten Spieler oder den Spieler, der zu Unrecht beschuldigt hat
- Endansicht mit Ergebnissen

**Technische Features:**

- Dynamisches Tile-Grid für Spielfeld
- WebSocket-Verbindung zur Synchronisierung
- UI mit Buttons für Rotation (direkt mit Klick auf vergrößerte, zu platzierende Karte), Meeple-Setzen bzw. Spielzug-Beenden
- Zoom- und Scroll-Gesten für Kartenansicht

---

### 1.4 UIUX Anforderungen

**Spielbildschirm:**

**Oberer Bereich:**

- Aktueller Spieler hervorgehoben
- Punktetafel mit Farbe, Namen und Punkte aller Spieler
- Kartendecks der noch zu ziehenden Karten

**Mittelteil:**

- Dynamisches Spielfeld (scroll-/zoombar)
- Platzierung von Karte und Meeple per Tippen

**Unterer Bereich:**

- Meeple inkl. Anzeige der verfügbaren Meeples pro Spieler
- für den aktiven Spieler: Gezogene Karte größer dargestellt
- Funktion 🔁 Rotieren als Vorbereitung des Platzierens per Tippen
- für die nicht aktiven Spieler: "Expose!"-Button, zum Entlarven eines cheatenden Spielers.
- "Skip-Meeple"-Button zum Beenden der Spielrunde ohne Meeple-Platzierung
- die eigene Punktezahl des Spielers

**Endansicht:**

- Finaler Punktestand
- Optionen: Neues Spiel / Zurück zur Lobby

---

### 1.5 Spielzug-Ablauf

**Ablauf eines Zugs:**

1. Server sendet eine zufällige Karte an den aktiven Spieler
2. Spieler rotiert und platziert die Karte
3. Optional: Meeple platzieren
4. Server prüft Platzierung und wertet Features
5. WebSocket-Update an alle Spieler
6. Nächster Spieler ist an der Reihe

**Frontend:**

- Eingaben für nicht-aktive Spieler blockieren und entsprechende Toasts senden
- Nur gültige Aktionen erlauben, bei fehlerhaften Klicks entsprechende Toasts senden

**Backend:**

- Eingaben validieren
- Änderungen persistent speichern

---

### 1.6 Spielende

Nach dem letzten Zug ist das Spielfeld vollständig, der Gewinner wird ermittelt und angezeigt genauso wie alle Punkte aller Spieler. Auch die Möglichkeit zum Menü zurückzukehren, wird angeboten.


**---**

## 2. APP-UI - Screenshots

Die folgenden Screenshots zeigen den finalen Stand der Benutzeroberfläche in der App:

### Landing Page
Startbildschirm der App

<img src="docs/images/250524_LandingPage.png" width="50%" />

### Authentication Screen
Login/Registrierung zur Nutzerverwaltung

<img src="docs/images/250624_AuthenticationScreen.png" width="50%" />

### Game Lobby
Auswahl: Neues Spiel erstellen oder bestehendem Spiel beitreten, Statistiken ansehen

<img src="docs/images/250624_GameLobby.png" width="50%" />

### Start Game
Übersicht der beigetretenen Spieler und Button um das Spiel zu starten

<img src="docs/images/250624_StartGame.png" width="50%" />

### Gameplay Screen
Hauptspielbildschirm mit Karten- und Meepleplatzierung sowie Spielinformationen und ggf. Toastnachrichten

<img src="docs/images/250624_GameplayScreen.png" width="50%" />

### End of Game Screen
Endpunktestand aller Spieler, hervorgehoben der Sieger sowie Möglichkeit zum Menü zurückzukehren

<img src="docs/images/250624_GameFinished.png" width="50%" />


**---**

## 3. Weiterführende Links

- 🌐 **Projekthomepage auf itch.io**:  
  👉 [https://j0klar.itch.io/pixel-carcassonne](https://j0klar.itch.io/pixel-carcassonne)
> Hier findest du die veröffentlichte Version der App, weitere Infos, Screenshots und den Link zum Game, das du auch gerne bewerten kannst!

- 🌐 **Homepage des Original-Boardgames**:  
  👉 [https://www.hans-im-glueck.de/carcassonne-familie/](https://www.hans-im-glueck.de/carcassonne-familie/)