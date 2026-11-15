# Junior Astronaut: Mars Outpost — Project Plan

## Goal

A browser-playable survival game for kids aged 7–12. The player runs a small Mars outpost one sol (Mars day) at a time. Real NASA data decides each sol's weather and radiation; the player decides how to respond. The outpost is shown in an angled bird's-eye view, and everything the simulation tracks is visible in that view.

## What the challenge asks for, and how this plan answers it

| Challenge requirement | How the plan meets it |
|---|---|
| Kids manage a lunar or Martian outpost | Mars outpost at Gale Crater, where the Curiosity rover measures the weather |
| Real decisions with trade-offs; no free "safe" choice | A daily power allocation between shielding, greenhouse and heating (Phase 5) |
| Two or three core systems, not five or six | Weather, radiation, food, linked by power |
| Grounded in real NASA data, with attribution | Curiosity weather feed and DONKI, bundled as an archive and credited in the game |
| Don't present static data as live | The game shows the real Earth date of each sol and labels the data as archived |
| Browser-playable, no download or account | Java Swing jar run in the browser through CheerpJ (already working) |
| Replayability | Each playthrough starts on a random date in the archive |
| Plain language | Terms such as "sol" and "radiation" are explained where they appear |

## Core design

### Setting
Mars, Gale Crater. One game day is one real sol from the archive, identified by its Earth date.

### Systems

- **Weather** comes from the data. Temperature, pressure, UV and sky opacity change how much solar power the outpost makes and how much heating it needs.
- **Radiation** comes from the data. A steady background dose, plus spikes on dates when a solar event occurred. Shielding reduces the dose the crew takes.
- **Food** is produced by a greenhouse and eaten by the crew every sol. The greenhouse needs power and warmth to grow.
- **Power** is what the player spends. Solar panels make a limited amount each sol and it cannot cover everything on a bad day.

### The daily loop

1. A new sol begins. The game loads that date's weather and any solar events.
2. The view updates to show the conditions.
3. The player splits the sol's power between shielding, greenhouse and heating.
4. The simulation applies the results: crew radiation dose, food stock, crew health.
5. The game ends when the mission length is reached, or when food or crew health runs out.

### View
An angled bird's-eye (isometric) view of a tile grid holding the habitat dome, greenhouse, solar panels and rover. Drawn in code with `Graphics2D`, as the current scene is; no image files.

## Data sources

| System | Source | Notes |
|---|---|---|
| Weather | Curiosity weather feed: `https://mars.nasa.gov/rss/api/?feed=weather&category=msl&feedtype=json` | One record per sol. Fields: `terrestrial_date`, `sol`, `season`, `min_temp`, `max_temp`, `min_gts_temp`, `max_gts_temp`, `pressure`, `atmo_opacity`, `local_uv_irradiance_index`, `sunrise`, `sunset`. About 1.7 MB. Undocumented feed, so download once and bundle. |
| Radiation events | DONKI: `https://ccmc.gsfc.nasa.gov/DONKI/api/` | Solar flares, coronal mass ejections and solar energetic particle events, by date. |
| Background radiation | Published Curiosity radiation detector average, roughly 0.6–0.7 millisieverts per sol | Used as a cited constant. |
| Food | Published NASA figures for crop growth times and crew food needs | Cited constants; there is no dataset for this. |

The data is bundled with the game, not fetched live. Live calls from the browser may be blocked under CheerpJ, the shared API key is rate-limited, and the challenge accepts an embedded archive.

### To verify before relying on it

- Whether the Curiosity feed goes back to the 2012 landing. Only the newest records have been inspected (latest seen: sol 4995, 25 August 2026).
- Whether `atmo_opacity` ever reads anything other than "Sunny", in particular during the 2018 global dust storm. If not, dust has to be inferred from another field or dropped.
- Whether DONKI's solar-wind simulation records list Mars as an impact location. If so, use only events that reached Mars; if not, use all events of a chosen strength.
- The exact figures for background radiation, crop growth and crew food needs, each with a source to cite.

## Code structure

Kept separate so each part can change without rewriting the others:

- **Data**: loads the bundled archive and returns the conditions for a given date.
- **Simulation**: holds the outpost state (sol, power, food, shielding, crew dose, crew health) and advances it by one sol given the conditions and the player's choices.
- **View**: draws the outpost from the current state. It reads the state and never changes it.
- **Controls**: the allocation inputs and the "next sol" button.

The Java side has no JSON library. A one-off script converts the downloaded JSON to CSV, and the game reads the CSV from inside the jar.

## Phases

### Phase 0 — Setup
- Split `SpaceGuy.java` into the four parts above.
- Add a build script that compiles and produces the jar, replacing the hand-built one.
- Remove the unused "Hello World" button and fix the stale header comment.

**Done when:** the build script produces a jar that runs in the browser as it does today.

### Phase 1 — Bird's-eye view
- Draw an isometric tile grid of Mars ground.
- Place the dome, greenhouse, solar panels and rover on it.
- Define the outpost state object and have the view draw only from it.
- Keep a temporary toggle to switch between two hard-coded states, to prove the view responds to state.

**Done when:** changing a value in the state object visibly changes the scene.

### Phase 2 — Weather
- Download the Curiosity feed and convert it to CSV. Resolve the feed questions under "To verify".
- Load the CSV and look up a sol's conditions by date.
- Add a "next sol" button that advances the date.
- Show weather in the view: sky colour and haze from opacity, a temperature readout, day length from sunrise and sunset.
- Show the Earth date and sol number.

**Done when:** stepping through sols shows real, changing weather for each date.

### Phase 3 — Radiation
- Download DONKI events for the same date range and convert to CSV. Resolve the Mars-impact question.
- Mark each sol with its solar events, if any.
- Compute the sol's dose: background plus any event spike.
- Show it in the view: a warning and visual effect on event sols, and a crew dose meter.

**Done when:** sols with a real solar event look different and raise the dose meter.

### Phase 4 — Food and power
- Add food stock, daily crew consumption and greenhouse growth.
- Add solar power generation, reduced by dusty or short sols.
- Show both in the view: greenhouse crop state, a food meter, panel output.

**Done when:** food rises and falls with weather across a run of sols with no player input.

### Phase 5 — Decisions
- Add the daily power split between shielding, greenhouse and heating.
- Make each choice cost something: power to shielding lowers dose but slows crops; power to the greenhouse does the reverse; too little heating on a cold sol harms both.
- Add the end conditions and a short end-of-mission summary of what happened and why.
- Tune so that no single allocation wins on every sol.

**Done when:** a playthrough can be won or lost, and the outcome depends on the player's choices.

### Phase 6 — Kid-friendly layer and attribution
- Rewrite all text for a 7-year-old reader; explain each science term where it appears.
- Add a "this really happened" note on notable sols, giving the real date.
- Add an in-game credits screen naming each dataset and stating that the data is archived, not live.
- Start each playthrough on a random date.

**Done when:** a child can play a full mission without outside explanation, and every data source is credited in the game.

### Phase 7 — Submission
- Write a short explanation of which NASA datasets are used and why.
- Publish on GitHub Pages and test in a clean browser.
- Update the README with how to play and how to build.

**Done when:** the public link works with no install and the dataset explanation is in the repo.

## Open decisions

- **Power and the daily decision.** The original idea was weather, radiation and food as a simulation. Power and the daily allocation are proposed additions, because the challenge requires player trade-offs. Confirm before Phase 4.
- **Mission length.** A fixed number of sols (for example 30) keeps a playthrough short enough for a child and for judging.
- **Date range to bundle.** The full Curiosity record, or a shorter window that includes active periods such as the 2018 dust storm and the May 2024 solar storms.
