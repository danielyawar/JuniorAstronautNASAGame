# Handoff: outpost view and real data

The game opens on an angled bird's-eye view of the outpost, drawn from real NASA data for
each sol. The player collects the energy the solar panels make, spends it on heat, shielding
and food, and tries to keep the crew healthy for 30 sols. This file says who owns which
files and what the modules may rely on. The game design is in `PLAN.md`.

## How a sol plays

1. Click **COLLECT** on the solar panels. The sol's energy (1 to 10, from the sunlight) goes
   into the battery, shown top right. The battery holds 20 and carries over between sols.
2. Click the **habitat** or the **greenhouse**. A card opens with plus and minus buttons:
   heat and shield on the habitat, growing food on the greenhouse. Each press moves one energy.
3. The box at the bottom left says what to do next, and what tonight will do to the crew's
   health and why. The totals are beside the health and food bars in the top strip.
4. Click **END SOL**. The crew eats, health changes, spent energy is used up, and the next
   sol begins. Solar energy that was not collected is lost.

Clicking the **weather station** or the **radiation detector** opens a card with the day's
readings. Clicking anywhere else closes a card.

The mission is won by ending sol 30 with crew health above 0, and lost when health reaches 0.
All the numbers are in `Rules.java` and are game rules, not NASA figures.

## Build and run

Needs a JDK, version 17 or newer, on the PATH. Nothing else.

```
java Build.java          # compiles src/ for Java 17 and writes SpaceGuy.jar
java -jar SpaceGuy.jar   # desktop
```

`Build.java` also copies every non-Java file under `src/` (the data archives) into the jar,
next to the classes that read them.

`SpaceGuy.jar` is committed because GitHub Pages serves it to `index.html`. Rebuild and
commit it whenever `src/` changes. The same sources and the same JDK give a byte-identical
jar; different JDK versions give slightly different bytes.

To try the browser version locally, serve the repository root with a server that supports
HTTP Range requests (CheerpJ requires them) and open `index.html`. `npx http-server` should
work. Python's `http.server` does not.

## Refreshing the data

```
python tools/fetch_nasa_data.py [weather] [events] [terrain]
java Build.java
```

Needs Python 3.9 or newer and no packages. Raw downloads are cached in `build/data-cache/`;
delete that folder to force a fresh download. The solar storm step makes about 175 requests
and takes a few minutes the first time.

| Archive | Source | Contents |
|---|---|---|
| `src/outpost/weather/curiosity_weather.csv` | Curiosity REMS weather feed, `mars.nasa.gov/rss/api/?feed=weather&category=msl&feedtype=json` | 4,745 sols, 7 August 2012 to 25 August 2026: air and ground temperature, pressure, UV index, sunrise, sunset |
| `src/outpost/radiation/donki_mars_cme.csv` | NASA CCMC DONKI, WSA-ENLIL model runs, `ccmc.gsfc.nasa.gov/DONKI/WS/get/WSAEnlilSimulations` | 514 days on which a solar storm (CME) was predicted to reach Mars, with its launch speed |
| `src/outpost/view/gale_terrain.csv` | Mars Global Surveyor MOLA gridded topography, 128 pixels per degree, tile `megt00n090hb.img` | 21 x 21 ground heights, 926 m apart, centred on Bradbury Landing (4.5895 S, 137.4417 E) |

Each file starts with comment lines naming its source and units.

### What is real and what is a game estimate

| Shown in the game | Status |
|---|---|
| Temperatures, air pressure, UV level, sunrise, sunset, Curiosity sol number | Measured, from the weather archive |
| Which sols have a solar storm, and the storm's launch date and speed | Real NASA model predictions of arrival at Mars. Not measurements made at Mars. |
| Everyday radiation dose, 0.64 mSv per day | Measured by Curiosity's radiation detector (Hassler et al., Science 2014) |
| Shape of the ground and the compass direction | Measured (MOLA). Heights are drawn 2x taller than true scale. |
| Sunlight percentage | **Game estimate** from the UV level: very high 100%, high 85%, moderate 65%, low 35%. See `ArchiveWeatherSource.sunlightFor`. |
| Radiation dose on a storm sol | **Game estimate** from the storm's speed: 2x to 6x the everyday dose, half the extra for a glancing blow. See `ArchiveRadiationSource.stormDoseMsv`. |
| Size of the buildings, the rocks | Not real. A ground tile is about 0.9 km across and the buildings are drawn far larger than life. Rocks are decoration. |

Answers to the questions `PLAN.md` listed under "To verify":

- The feed goes back to sol 1 (7 August 2012). Sols 1 to 9 have no temperature readings.
- `atmo_opacity` is "Sunny" on all but three sols, including the 2018 global dust storm, so
  it is not used. The UV index does drop to "Low" in that storm, so the game uses it as its
  dust signal.
- DONKI's WSA-ENLIL records do list Mars as an impact location; only those are used.
  DONKI's solar energetic particle records come from spacecraft near Earth, so they are
  not used.

## File ownership

| Path | Owner | Contents |
|---|---|---|
| `src/outpost/shared/` | Shared. Change only by agreement of all module owners. | The contracts below |
| `src/outpost/app/` | Shared | `Main` (window and wiring), `GameController` and `Rules` (the game rules) |
| `src/outpost/view/` | Shared | `OutpostView` (the bird's-eye view and every control), `Terrain`, and the drawing helpers `Mesh` and `Projection` |
| `src/outpost/weather/` | Weather | `ArchiveWeatherSource` and its archive |
| `src/outpost/radiation/` | Radiation | `ArchiveRadiationSource` and its archive |
| `Build.java`, `index.html`, `SpaceGuy.jar`, `tools/` | Shared | Build script, browser page, built jar, data download script |

A feature module imports from `outpost.shared` and from nowhere else in `outpost`. Modules
never import each other or `outpost.app`. Only `Main` names module classes, in the block
marked "Module wiring", so replacing a module means editing files in its own folder plus
at most its own lines in that block.

## Contracts (`outpost.shared`)

| Type | Meaning |
|---|---|
| `Sol(number, earthDate)` | The current game day. `number` starts at 1. `earthDate` is the key into the data archives. |
| `WeatherConditions(minTempC, maxTempC, sunlight, missionSol, pressurePa, uv, sunrise, sunset, measuredOn)` | One sol's weather. `sunlight` is 0.0 to 1.0, the share of clear-sky solar power. `measuredOn` is the Earth date of the rover reading used. A three-argument constructor leaves the rest unknown. |
| `UvIndex` | `UNKNOWN`, `LOW`, `MODERATE`, `HIGH`, `VERY_HIGH` |
| `RadiationConditions(doseMsv, solarEvent, detail)` | One sol's unshielded dose in millisieverts, whether a solar event caused a spike, and one sentence about the event for display (or null). A two-argument constructor leaves `detail` null. |
| `PowerAllocation(shielding, greenhouse, heating)` | Whole units of energy spent on each use this sol. Each is 0 or more. |
| `PowerUse` | `SHIELD`, `GREENHOUSE`, `HEAT` |
| `GameState(sol, weather, radiation, allocation, limit, phase, missionSols, energy, energyCapacity, solarOutput, collected, heatNeed, crewHealth, foodDays, forecast)` | Immutable snapshot of everything a panel may display. `limit` is the most energy each use can take this sol. |
| `SolReport` | What a sol does to food and crew health under a given spending. `GameState.forecast` is the report for ending the sol now. |
| `Phase` | `PLAYING`, `WON`, `LOST`. `BRIEFING` is not used yet. |
| `PlayerActions` | `collect()`, `spend(use, +1 or -1)`, `nextSol()`, `restart()`. Implemented by `GameController`. |
| `Glyphs` | Small icons and their colours, drawn in code. |
| `WeatherSource.weatherFor(Sol)` | Implemented by the weather module. |
| `RadiationSource.radiationFor(Sol)` | Implemented by the radiation module. |
| `CsvResource.read(owner, name)` | Reads a bundled archive from the jar: comment lines and data rows. |

Both sources must return the same answer every time they are asked about the same sol.

## Rules

1. **The controller owns turn progression.** `GameController` holds the only mutable game
   state. It creates a new `GameState` on every `PlayerActions` call that changes
   something and sends it to every listener.
2. **Panels display state and emit actions.** A panel has a `show(GameState)` method that
   stores the snapshot and calls `repaint()`. To act, it calls the `PlayerActions` it was
   given in its constructor. A panel never names the controller class.
3. **Painting changes nothing.** `paintComponent` reads the stored snapshot and draws. It
   must not advance, compute or store simulation state, because Swing repaints at
   unpredictable times. The view does keep its own display state: which building's card is
   open and what the mouse is over.
4. Everything runs on the Swing event thread.

Current controller behaviour: ending a sol applies `GameState.forecast`, then adds one to
the sol number and one day to the Earth date, clears the spending and keeps the battery.
Restart returns to the first sol with an empty battery, full health and 5 days of food.

## How the view shows the state

The screen is kept quiet by giving every fact one home, in one of three places:

- **The strip along the top** holds what the player is looking after: the sol, crew health,
  food and energy.
- **A one-line tag over each building** holds the one or two numbers that matter for today's
  choices.
- **A building's card**, opened by clicking it, holds everything else about it.

Before adding something to the screen, look for the place it already belongs. Only add to
the strip or to a tag if the player needs it to choose how to spend energy.

| State | In the view |
|---|---|
| `sunlight` | Solar panel colour, brightness of the whole scene, dust haze. The weather tag is headed "Dusty sky" below 50%. The percentage is on the weather station's card. |
| `minTempC` | Weather tag ("-71°C tonight"), and the heat line of the habitat's card |
| `maxTempC`, `pressurePa`, `sunrise`, `sunset`, `uv` | Weather station's card |
| `measuredOn`, `missionSol` | Weather station's card. The weather tag adds "no reading today" when the rover has no reading for the date. |
| `doseMsv`, `solarEvent`, `detail` | Radiation tag and the detector's light and card, pink streaks, banner under the top strip with the storm's details |
| `allocation.shielding` | Height of the soil bank around the habitat; habitat tag |
| `allocation.heating` | Glow of the habitat windows, brightest when the night's need is met; habitat tag, red while the night's need is not met |
| `allocation.greenhouse` | Colour and height of the crop rows; greenhouse tag |
| `energy`, `solarOutput`, `collected` | Energy bar in the top strip. The outlined part is today's energy waiting to be collected. COLLECT button over the solar panels, replaced by a tag once collected. |
| `crewHealth`, `foodDays`, `forecast` | Bars in the top strip, each with tonight's change: the end of the bar that tonight takes away is faded, and what tonight adds is an outline. The reasons for the change in health, bottom left. What grows and what is eaten, on the greenhouse's card. |
| `phase` | Mission complete or mission over box with PLAY AGAIN |

The guide at the bottom left names one thing to do next, and the tag of the building it
names is ringed in the same yellow. The data sources are credited in one line at the bottom;
the "About the data" button opens the full credits and the notes on scale.

The view is laid out at 800 x 600 and scales to fit its panel. It has no animation: it
repaints only when the state changes or the mouse moves onto or off something clickable.

## Still temporary

| What | Where | Replace with |
|---|---|---|
| Fixed start, sol 1 on 1 June 2018 | `Main.START` | A random start date from the archive |
| `Mission.java` | `outpost.shared` | Unused. Left over from an earlier draft of the rules; delete it or fold its extra fields (health lost by cause, storms faced) into `GameState`. |

## Not done

- **Balance.** The numbers in `Rules.java` have been checked only by a scripted
  play-through from the fixed start date: sensible spending wins with about 73 health and
  spending nothing loses on sol 7. Other start dates and real players are untested.
- **End-of-mission summary.** The ending box gives the result and the last night's losses,
  not what happened over the whole mission.
- **Sol-to-date mapping.** One sol is treated as one Earth day. A real sol is 24 h 39 min,
  so about one Earth date in 37 has no rover row; the game then shows the previous row and
  says so. Dates after the archive ends show its last row, also labelled.
- **Text for young readers, credits screen.** The view credits each source in one line at
  the bottom and in the "About the data" card; the wording has not been written for a
  7-year-old.
- **Tests.** There are none.
