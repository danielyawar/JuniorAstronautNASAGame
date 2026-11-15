# Handoff: outpost view and real data

The game opens on an angled bird's-eye view of the outpost, drawn from real NASA data for
each sol. Outcomes (food, crew health, winning and losing) are not modelled yet. This file
says who owns which files and what the modules may rely on. The game design is in `PLAN.md`.

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
| `src/outpost/app/` | Shared | `Main` (window, wiring, Next Sol and Restart) and `GameController` |
| `src/outpost/view/` | Shared | `OutpostView` (the bird's-eye view), `Terrain`, and the drawing helpers `Mesh` and `Projection` |
| `src/outpost/weather/` | Weather | `ArchiveWeatherSource` and its archive |
| `src/outpost/radiation/` | Radiation | `ArchiveRadiationSource` and its archive |
| `src/outpost/foodpower/` | Food and power | `FoodPowerPanel` (the power controls) |
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
| `PowerAllocation(shielding, greenhouse, heating)` | The player's power split in percent. Each share is 0 or more; the total is 100 or less. The constructor rejects anything else. |
| `GameState(sol, weather, radiation, allocation)` | Immutable snapshot of everything a panel may display. |
| `WeatherSource.weatherFor(Sol)` | Implemented by the weather module. |
| `RadiationSource.radiationFor(Sol)` | Implemented by the radiation module. |
| `CsvResource.read(owner, name)` | Reads a bundled archive from the jar: comment lines and data rows. |

Both sources must return the same answer every time they are asked about the same sol.

## Rules

1. **The controller owns turn progression.** `GameController` holds the only mutable game
   state. It creates a new `GameState` on `nextSol()`, `restart()` and `setAllocation()`
   and sends it to every listener.
2. **Panels display state and emit actions.** A panel has a `show(GameState)` method that
   stores the snapshot and calls `repaint()`. To act, it calls a callback it was given in
   its constructor (`FoodPowerPanel` receives a `Consumer<PowerAllocation>`). A panel
   never holds the controller.
3. **Painting changes nothing.** `paintComponent` reads the stored snapshot and draws. It
   must not advance, compute or store simulation state, because Swing repaints at
   unpredictable times.
4. Everything runs on the Swing event thread.

Current controller behaviour: Next Sol adds one to the sol number and one day to the Earth
date, and keeps the allocation. Restart returns to the first sol and the starting
allocation.

## How the view shows the state

| State | In the view |
|---|---|
| `sunlight` | Solar panel colour, brightness of the whole scene, dust haze, "Clear sky" or "Dusty sky" |
| `minTempC`, `maxTempC`, `pressurePa` | Weather station label |
| `sunrise`, `sunset`, `uv` | Top-right box |
| `measuredOn`, `missionSol` | Top-left box. Says so when the rover has no reading for the date. |
| `doseMsv`, `solarEvent`, `detail` | Radiation detector label and light, pink streaks, banner with the storm's details |
| `allocation.shielding` | Height of the soil bank around the habitat |
| `allocation.heating` | Glow of the habitat windows |
| `allocation.greenhouse` | Colour and height of the crop rows |

The view is laid out at 800 x 600 and scales to fit its panel. It has no animation: it
repaints only when the state changes.

## Still temporary

| What | Where | Replace with |
|---|---|---|
| Fixed start, sol 1 on 1 June 2018 | `Main.START` | A random start date from the archive |
| Plus and minus buttons in steps of 10 | `FoodPowerPanel` | Whatever allocation control the final design calls for |

## Not done

- **Outcomes.** Food stock, crew dose, crew health and end conditions are not modelled,
  so `GameState` has no fields for them. Adding them is a change to `outpost.shared` and
  to `GameController.nextSol()`. Until then the power split changes how the outpost looks
  and nothing else.
- **Power budget.** Allocations are percentages. How much power a sol produces is not
  defined yet.
- **Sol-to-date mapping.** One sol is treated as one Earth day. A real sol is 24 h 39 min,
  so about one Earth date in 37 has no rover row; the game then shows the previous row and
  says so. Dates after the archive ends show its last row, also labelled.
- **Text for young readers, credits screen.** The view credits each source in two lines at
  the bottom; the wording has not been written for a 7-year-old.
- **Tests.** There are none.
