# Handoff: the real-time sol

The game opens on a home screen where the player picks a real period of Mars history. The
mission then plays on a picture of the outpost: a photo of Mars ground with four building
pictures on it. Each sol's light, temperature and radiation are real NASA data, starting on
that Earth date. A sol runs on a clock from its real sunrise to its real sunset. The player
directs one astronaut and flips two switches, and tries to reach each sunset with enough
energy in the battery to get through the night. The mission lasts 12 sols, about 10 to 15
minutes. The first mission after the game opens begins with a five-step lesson, given before
the clock runs.

This file says how the game plays, what is real, who owns which files and what the modules
may rely on. `PLAN.md` is the original project plan. Its daily loop (share out energy, end
the sol) has been replaced by the one described here.

## The home screen

The home screen shows the game's name, one large astronaut's helmet, and down the right-hand
side a list of the four missions with **Surprise me** at its foot. The helmet is the picture
of the mission that is picked in the list: the state of the helmet shows what the mission is
like. It is drawn as a solid object. It sways gently by itself, and the player can drag it
right round. Under it are only the picked mission's title, its month and year, one line on
what makes it hard, and a yellow button, **PICK THIS MISSION**. The list runs from the
gentlest mission to the hardest, and its first row has a green "Start here" tab.

A click on a row picks it. So do the mouse wheel (one notch, one row) and the up and down
keys. The ends of the list do not wrap. Picking starts nothing: only the button does, so the
screen works with clicks alone, for a tablet that has no hover. The helmet changes from one
mission's look to the next over 0.4 seconds. Which row is picked is the view's own display
state, like what the mouse is over. The controller hears of it only through
`start(scenario)` or `startSurprise()`. The picked row is kept when the player comes back
from a mission.

With **Surprise me** picked, the helmet has a question mark in its visor, and the words under
it are "Surprise me" and "A mystery day", with no date. Its mission starts on a random date
from 16 August 2012 (the first with temperature readings) up to the last date that leaves a
whole mission before the weather archive ends.

| In the 800 x 600 layout | Where |
|---|---|
| The game's name | Centred, baseline 42 |
| The helmet | The square x 116 to 448, y 46 to 378 |
| Title, month and year, the one line | Centred on x 282, baselines 404, 425 and 447 |
| **PICK THIS MISSION** | x 158 to 406, y 460 to 504 |
| The list | x 558 to 780. Rows 62 tall, with tops at y 102, 174, 246 and 318, and "Surprise me" at 400 |
| The credit line | Centred, baseline 584 |

| Scenario | First sol | Why this date | Its helmet |
|---|---|---|---|
| First days on Mars | 16 August 2012 | Clear skies and no solar storm in the 12 sols. The gentle one. | Clean, with a butterscotch sky and a small Sun in the visor |
| The great dust storm | 8 June 2018 | The UV reading falls to Low on 15 June, which is sol 8. | Caked in orange dust and scuffed, with brown haze and a dim Sun in the visor |
| Deep winter | 6 January 2016 | Nights near -87°C, moderate UV, and Mars near its farthest from the Sun (sunlight 0.85 of average). | Frost creeping in from the rim of the visor, and pale light |
| The stormy Sun | 10 May 2024 | Solar storms are predicted on nine sols in a row, 13 to 21 May: sols 4 to 12. | A flaring Sun and pink streaks in the visor |

The scenarios are listed in one place, `Scenarios.LIST` in `outpost.app`: a title, a start
date, the line on what makes it hard, and the story. The line and the story are the only
claims about history the game makes; add no fact to one without a NASA source. The start
dates were chosen for a 12-sol mission. If `Rules.MISSION_SOLS` changes, check them against
the archives again.

The order of that list is the order of the rows. It is the order of the health the careful
scripted player has left at the end (95, 93, 68, 53: see Balance below). `balance` prints
the order it finds, and says so if the list disagrees.

### The helmets

One class, `Helmet3D`, draws every helmet from four dials, each from 0 to 1 (`Helmet` in
`outpost.shared`). The controller works the dials out from the mission's own sols, in
`Helmets` in `outpost.app`. None is set by hand, and the drawing takes any mix of the four.
"How the view shows the state" below says how the helmet is drawn and what it costs.

| Dial | Worked out from | 0 at | 1 at | What it draws |
|---|---|---|---|---|
| `dust` | The share of sols with a dusty sky: sunlight below 50%, the level at which the "Outside" reading in the strip is headed "Dusty sky" | No dusty sol | 40% of the sols (5 of 12) | Orange dust caked on the shell and the neck ring, thickest low down; a film and grains on the glass; brown haze and a dim Sun in the visor |
| `frost` | The usual night's low: the middle one of the mission's lows | -79°C or warmer | -87°C or colder | A blue cast and sparkles of ice on the shell; frost crystals growing in from the rim of the visor; a pale blue sky and a white Sun |
| `storm` | The share of sols with a solar storm | No storm sol | 75% of the sols (9 of 12) | A large flaring Sun with rays and the pink streaks of the outpost's solar storms in the visor; pink light on the edge of the shell |
| `wear` | 0.75 x dust + 0.25 x storm + 0.15 x frost, at most 1 | | | Scuffs and scratches on the shell, fine scratches on the glass |

| Mission | dust | frost | storm | wear |
|---|---|---|---|---|
| First days on Mars | 0 | 0 | 0 | 0 |
| The great dust storm | 1 | 0 | 0 | 0.75 |
| Deep winter | 0 | 1 | 0 | 0.15 |
| The stormy Sun | 0 | 0 | 1 | 0.25 |

Frost uses the usual night and not the coldest, so that the two -98°C nights in the 2024
window (see below) do not frost over The stormy Sun's helmet: its middle low is -68.5°C.
A surprise mission's helmet is worked out in the same way from its own dates, and is shown
on its mission-start card. Most surprise helmets are clean or lightly marked: nine dates in
ten have no dusty sol, and half have one storm sol or more (one storm sol gives storm 0.11).
The numbers in `Helmets` are game estimates chosen so that the four missions look different
at a glance. If a scenario's dates change, look at its helmet again.

The 2024 window has two single nights at -98°C (16 and 18 May) among nights near -69°C.
They are real archive readings; the archive has 32 such dips, nearly all in 2024 and 2026.
Under the rules below such a night takes about 6 more energy than its neighbours (38
against 32), nearly as much as a Deep winter night (41), and costs 21 health against 17 if
the heater is off all night. `java -jar SpaceGuy.jar balance` prints these figures for every
night of every scenario.

After **PICK THIS MISSION** is pressed, the **mission-start card** shows the mission's
helmet (the same one, standing still and turned a little to one side), its title, its Earth
dates and its story, with a green **START** button. The two buttons have different words and
colours, so it does not feel like pressing START twice. The clock does not run until START
is pressed. The home screen's button lies wholly below the place where START will appear
(`HomeScreen.pickButton` is placed from `Notices.START_BUTTON`), and no row of the list lies
there, so a child who clicks twice does not start a mission before seeing its card.

## The lesson

The first time START is pressed after the game opens, the guide teaches the game before the
clock runs. There are five steps. Each is one sentence in the guide line, with the guide's
yellow ring round the one thing the step is about. A row above the guide shows "Step n of
5", **Skip**, and **NEXT** on the steps that have nothing to try.

| Step | Ringed | Sentence | Goes on when |
|---|---|---|---|
| 1 | The heater's switch | "Try the heater switch: click it off, then on again." While it is off: "Brr! Click it back on to keep your astronaut warm." | The player has switched the heater back on |
| 2 | The energy bar | "The Sun fills the battery, and the moon mark shows what the night will need." | NEXT |
| 3 | The greenhouse: its picture, its round button and its lamp switch, in one ring | "The lamps grow food, and a click on the greenhouse picks it." | NEXT |
| 4 | The SLEEP button | "When the jobs are done, press SLEEP to skip to the night." | NEXT |
| 5 | The digger's round button | "Click a place to send your astronaut to work there: try the digger!" | The player clicks any of the four places |

- **Time stands still.** The lesson stops the clock in the way an open card does: the view
  calls `pause(true)` when the lesson starts and `pause(false)` when it ends. The rules and
  the controller know nothing of the lesson. Health is still 100 and the clock still at
  sunrise when it ends, however long the player took.
- **Only the lesson's own things can be clicked** while it runs: NEXT, Skip, the one thing
  the step asks for, Change mission and Restart. SLEEP cannot be pressed in step 4, nobody
  is sent anywhere before step 5, and the two readings in the strip open no card.
- **Sending the astronaut is the last step**, because walking takes game time: with the
  clock stopped, an astronaut who has been sent stands still. The click in step 5 sends the
  astronaut and ends the lesson together, so the clock starts and the astronaut is seen to
  walk off at once. The digger is ringed because digging the shield is also the first thing
  the guide suggests in a mission.
- **Skip** ends the lesson at any step. If the heater was switched off in step 1, Skip
  switches it back on, so the lesson never hands over a cold habitat.
- **It is given once.** Restart, Change mission and later missions go straight to the sol.
  The quiet **How to play** button above the guide gives it again at any time of day, and
  stops the clock while it does. Whether the lesson has been given is the view's own state,
  like which card is open, and lasts until the game is closed.
- The scripted players in `Balance` call `startSol` themselves and never meet the lesson.

## How a sol plays

**The clock.** A day takes 60 real seconds from the sol's real sunrise to its real sunset.
The clock stands still while any card is open or the lesson is running. At sunset the astronaut goes in, eats and
sleeps, and the night plays out at 3 Mars hours a second (about 4 seconds). The **SLEEP**
button sends the astronaut to bed early: the rest of the day and the night pass at the same
fast rate. At sunrise a card reports what the night did and why, and waits for the player.

**Energy.** The battery holds 100 and fills and drains all the time.

| Energy in is the product of | From |
|---|---|
| The Sun's height | Zero at sunrise and sunset, most at midday |
| The sky | `WeatherConditions.sunlight` |
| Distance from the Sun | `WeatherConditions.ls`, through `Rules.sunStrength` |
| Clean panels | `Outpost.panelsClean`. Dust builds every sol, fastest on dusty sols. |

| Energy out | When |
|---|---|
| Life support | Always, a little |
| Heater | While switched on. More the colder it is outside at that moment. |
| Grow lamps | While switched on |
| Digger | While the astronaut is digging |

The two switches sit beside the habitat (HEAT) and the greenhouse (LAMPS), and flip at once
from anywhere. When a mission starts the heater is on, because sunrise is the
coldest moment of the sol, and the lamps are off. With an empty battery,
everything that is switched on shares whatever the panels are making; at night that is
nothing, so everything stops.

**The astronaut.** There is one, and the health bar is theirs. Clicking a place's picture or
its round button sends them there. Every walk takes half a Mars hour (2.5 seconds), and they
work until sent elsewhere.

| Click | Job | Effect |
|---|---|---|
| Solar panels | Brush | Restores clean panels |
| Digger | Dig | Raises the soil bank round the habitat. It stays between sols. |
| Greenhouse | Pick food | Crops grow while the lamps are on, up to two days' worth. Picking moves them into the food store and puts plant clippings back in the soil as compost. |
| Habitat | Rest | Health recovers slowly, and the soil bank shields the astronaut |

**Health** falls for three reasons and the mission is lost the moment it reaches 0.

- Radiation: the sol's dose arrives evenly through the sol. The astronaut takes all of it
  outside, in the greenhouse and while walking. In the habitat the soil bank stops a share,
  up to 80% when the bank is finished.
- Cold: while the heater is off or has no energy, faster the colder it is outside.
- Hunger: the astronaut eats one day of food at bedtime, and loses health if there is none.

While health is falling, the health bar says why: "cold" or "radiation", whichever is taking
more at that moment. The rules work this out (`Readings.harm`), with the same sums as the
step function. With no soil bank the everyday radiation alone takes about 3 health a sol, so
on a quiet sol the bar says "radiation" until the astronaut is resting in a warm habitat
behind a bank that is nearly finished (88% or more), where rest wins the health back. The
word is hidden while the clock is stopped, as "filling" and "draining" are.

The mission is won by finishing the night of sol 12 with health above 0.

All the numbers are in `Rules.java`. They are game rules, not NASA figures, except where
the table below says otherwise.

## The garden soil (pH)

The greenhouse soil starts as Earth soil, pH 6.3, healthy. Earth soil is the thing that
runs out: every day of food the crops grow uses some up, and Mars soil takes its place, so
the share of Mars soil (`Outpost.marsSoil`, 0 to 1) rises and the pH rises with it, in a
straight line from 6.3 (all Earth soil) to 9.25 (all Mars soil). The pH can never fall
below 6.3, so the game has no "too acid" state. Picking food puts the clippings back as
compost, which wins a little of the Earth-like soil back.

| Soil pH | Zone | Plants grow at |
|---|---|---|
| 7.0 or less | Healthy | full speed |
| Above 7.0, up to 8.5 | Tired | 75% speed |
| Above 8.5 | Stressed | 50% speed |

The only place the player sees it is the greenhouse's round button, which reads "pH 6.8"
above the days of food in store. The zone is not shown. The decision it creates: the
lamps cost energy and now also soil. Growing food you do not need wears the soil out for
nothing; leaving ripe crops on the plants stops them growing and returns no compost.

| Status | Value |
|---|---|
| **Real**, NASA OSDR OSD-581 (Berni et al. 2023) | pH 9.25 for Mars soil simulant MMS-1 at the start of the study; plants on it showed yellow, bent leaves and root air gaps; 8.55 after 14 days, still stressed. |
| **Borrowed**, not NASA, not ryegrass | A healthy pH of about 6.3 for amended MMS-1, from a Mars-simulant study on soybean. The Earth soil the game starts with is not measured: the OSD-581 study's own potting soil was about pH 5. Check the citation before putting one on screen. |
| **Game rules** | The straight line between the two pHs (real soil mixes do not behave so), the 7.0 and 8.5 zone edges, the growth speeds, `SOIL_PER_FOOD_DAY` (0.10), `COMPOST_PER_FOOD_DAY` (0.03), and the idea that Earth soil is used up by growing food. |

The study tested only soil near pH 5 and Mars simulant near pH 9. Everything in between is
untested, and that is a good thing to tell a child: nobody knows yet.

## Build and run

Needs a JDK, version 17 or newer, on the PATH. Nothing else.

```
java Build.java                      # compiles src/ for Java 17 and writes SpaceGuy.jar
java -jar SpaceGuy.jar               # desktop
java -jar SpaceGuy.jar balance       # the balance simulation: no window, prints results
java -jar SpaceGuy.jar balance sols  # the same, with a line for every sol
java -jar SpaceGuy.jar paint-times   # desktop, with the time each repaint takes in a corner
```

`Build.java` also copies every non-Java file under `src/` (the data archives and the
pictures) into the jar, next to the classes that read them.

`SpaceGuy.jar` is committed because GitHub Pages serves it to `index.html`. Rebuild and
commit it whenever `src/` changes. The same sources and the same JDK give a byte-identical
jar; different JDK versions give slightly different bytes.

### The pictures

The play screen is made of five picture files in `src/outpost/view/`. Each is kept at twice
the size it is drawn at in the 800 x 600 layout, which is enough for a large window or a
screen that Java scales up, and no larger.

| File | Drawn at | In the jar | The original |
|---|---|---|---|
| `background.jpg` | The whole layout, 800 x 600 | 1600 x 983, 264 KB (a photo is not made taller than it was) | 1825 x 983, 3.7 MB |
| `base.png` (the habitat) | 175 x 150 | 350 x 300, 69 KB | 3000 x 2100, 2.3 MB |
| `greenh.png` | 105 x 85 | 210 x 170, 36 KB | 3000 x 2455, 4.2 MB |
| `panel.png` | 80 x 50 | 160 x 100, 7 KB | 2000 x 2000, 1.1 MB |
| `drill.png` | 60 x 50 | 120 x 100, 10 KB | 1781 x 3000, 1.1 MB |

```
java tools/ShrinkPictures.java <folder of the full-size originals>
java Build.java
```

`tools/ShrinkPictures.java` makes the files again. Run it after adding a picture or changing
the size one is drawn at, and change its list to match `Site.java`: it repeats the four drawn
sizes, because it cannot read the table. The full-size originals are not in the working
files. They are in git history, in commit `6224f38`, under the same names (the photo as
`background.png`). Shipped full size they made the jar 12.6 MB, held over 100 MB of memory
and were scaled again at every frame; the jar is now about 0.6 MB.

Three of the building pictures are drawn in a different shape from the artist's originals,
because that is the size they were first placed at: the drill about twice as wide, the solar
panel 60% wider and the base about 22% taller. The photo is squeezed from 1825 x 983 to 4:3.
The files have the drawn shape.

To try the browser version locally, serve the repository root with a server that supports
HTTP Range requests (CheerpJ requires them) and open `index.html`. `npx http-server` should
work. Python's `http.server` does not. In the browser the game runs in an 800 x 600 window
inside the page. To see paint times there, pass the word to the jar in a copy of
`index.html`: `cheerpjRunJar(path, "paint-times")`.

## Balance

`Balance.java` plays whole missions through `GameController` with scripted players. They
press the same `PlayerActions` a person does, and time moves only through
`GameController.advance`, so the simulation measures the game itself. Run it after changing
any number in `Rules.java`. Results with the present numbers:

| Scripted player | First days on Mars | The great dust storm | Deep winter | The stormy Sun | 300 random start dates |
|---|---|---|---|---|---|
| Sensible play | Wins, health 95 | Wins, 93 | Wins, 68 | Wins, 53 | Wins 300. Health: average 84, lowest 40. |
| Sensible, but looks only every 6 seconds | Wins, 95 | Wins, 92 | Wins, 66 | Wins, 36 | Wins 300. Average 82, lowest 35. |
| Does nothing | Lost on sol 10 | Lost on sol 9 | Lost on sol 8 | Lost on sol 8 | Wins 0 |
| Both switches on, astronaut rests | Lost on sol 8 | Lost on sol 6 | Lost on sol 5 | Lost on sol 6 | Wins 0 |
| Sensible jobs, but never switches off | Wins, 77 | Lost on sol 10 | Lost on sol 6 | Lost on sol 9 | Wins 120 |

The columns are in the order of the rows of the home screen's list, which is the order of the
first row: the more health sensible play has left, the gentler the mission. `balance` prints
that order under "Sensible play" and says whether `Scenarios.LIST` agrees with it.

No start date in the sample loses under sensible play. The dates it finishes with least
health are 19 May 2025 (40), 16 May 2025 (41), 22 August 2021 (43), 18 August 2021 (44) and
21 July 2018 (45), all mostly hunger: slower growth in tired soil means the lamps have to
run longer. (Before the soil rules the lowest was 53.) 10 May 2024 still finishes with 53, from radiation.
The sample is 300 dates drawn with a fixed seed from the same range as "Surprise me".

What the numbers were tuned for: sensible play wins every scenario with health to spare
but not full health; doing nothing loses; leaving both switches on with the astronaut
resting loses; and in the dust storm a player who never switches the lamps off cannot get
through the nights.

The heater is on when a mission starts. While it started switched off, doing nothing lost
on sol 3 or 4, to the cold. Now doing nothing loses on sols 8 to 10: to hunger once the four
days of food are eaten, to radiation (the larger share in The stormy Sun), and in the dust
storm and Deep winter to cold as well, on nights when weak sunlight and dust on the
unbrushed panels have left the battery empty. The other four rows did not change, because
those players switch the heater on before any time passes, so no number in `Rules` was
changed for this.

The scripted "sensible" player decides four times a second and never forgets anything. A
child will do worse, which is why it finishes most missions well above 50 health.

## Refreshing the data

```
python tools/fetch_nasa_data.py [weather] [events]
java Build.java
```

Needs Python 3.9 or newer and no packages. Raw downloads are cached in `build/data-cache/`;
delete that folder to force a fresh download. The solar storm step makes about 175 requests
and takes a few minutes the first time.

| Archive | Source | Contents |
|---|---|---|
| `src/outpost/weather/curiosity_weather.csv` | Curiosity REMS weather feed, `mars.nasa.gov/rss/api/?feed=weather&category=msl&feedtype=json` | 4,745 sols, 7 August 2012 to 25 August 2026: solar longitude, air and ground temperature, pressure, UV index, sunrise, sunset |
| `src/outpost/radiation/donki_mars_cme.csv` | NASA CCMC DONKI, WSA-ENLIL model runs, `ccmc.gsfc.nasa.gov/DONKI/WS/get/WSAEnlilSimulations` | 514 days on which a solar storm (CME) was predicted to reach Mars, with its launch speed |

Each file starts with comment lines naming its source and units.

### What is real and what is a game estimate

| In the game | Status |
|---|---|
| Lowest and highest temperature of each sol, air pressure, UV level, sunrise and sunset times, solar longitude, Curiosity sol number | Measured, from the weather archive |
| Which sols have a solar storm, and the storm's launch date and speed | Real NASA model predictions of arrival at Mars. Not measurements made at Mars. |
| Everyday radiation dose, 0.64 mSv per day | Measured by Curiosity's radiation detector (Hassler et al., Science 2014) |
| How strong sunlight is at Mars's distance from the Sun | **Real physics**, `Rules.sunStrength`: ((1 + 0.0934 cos(Ls - 251°)) / (1 - 0.0934²))², from the measured solar longitude. 1.217 at the nearest point and 0.836 at the farthest, which match (227.956 / 206.650)² and (227.956 / 249.261)² from the distances in the NASA Mars Fact Sheet. `balance` prints this check. |
| When the Sun rises and sets, and so how long a day lasts | Measured |
| The Sun's height between sunrise and sunset | **Model**, `Rules.sunHeight`: half a sine wave between the two measured times |
| The temperature outside at each moment | **Model**, `Rules.outsideC`: the sol's measured low at sunrise, a smooth climb to its measured high 70% of the way through the daylight, and a smooth fall back to the low by the next sunrise. Only the two ends are measured. |
| Which night a low belongs to | **Model**. The rover's low for a sol is used both for that sol's dawn and for the night after that sol's day. |
| Sunlight percentage (the sky) | **Game estimate** from the UV level: very high 100%, high 85%, moderate 65%, low 35%. See `ArchiveWeatherSource.sunlightFor`. |
| Radiation dose on a storm sol | **Game estimate** from the storm's speed: 2x to 6x the everyday dose, half the extra for a glancing blow. See `ArchiveRadiationSource.stormDoseMsv`. |
| The dose arriving evenly through the sol | **Model**. The archive has one figure per sol. |
| How long a night lasts | The measured sunset to the same sol's sunrise time a sol later. The next sol's own sunrise is at most a minute different. |
| Energy the panels make, and dust building up on them | **Game rule**. Dust settles faster the lower the sunlight percentage. |
| Energy the heater, lamps, digger and life support draw | **Game rule**. The heater's draw rises with each degree the outside is below 20°C. |
| Battery size, walking time, digging, brushing, crop growth, food eaten | **Game rule** |
| The soil bank stopping up to 80% of radiation, and only in the habitat | **Game rule** |
| Health lost to radiation, cold and hunger, and recovered by rest | **Game rule** |
| The helmet on the home screen and on the mission-start card | **Game estimate** from the mission's real data: its dusty sols, its usual night's low and its solar storm sols. See `Helmets`. The picture itself is a drawing, made by the game's own code. |
| The ground photo, the four building pictures and the astronaut | **Pictures, not data.** Nothing is to scale: the astronaut is drawn as tall as a building. Where the photo and the building pictures came from is not recorded: see "Not done". |

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
| `src/outpost/app/` | Shared | `Main` (window, wiring and the clock's timer), `GameController`, `Rules` (the game rules and the step function), `Scenarios` (the list of missions, in the order of the cards), `Helmets` (each mission's helmet, from its data) and `Balance` (the balance simulation) |
| `src/outpost/view/` | Shared | `OutpostView` (the panel: the mouse and the keys, the play screen's buttons and switches, the cards, the guide and the lesson), `Site` (the one table of places on the play screen), `Pictures` (the ground photo and the building pictures, their one cached image and the test for a click on a picture), `Light` (the tint of the hour and the dust, and the streaks of a solar storm), `Astronaut` (the figure), `StatusStrip` (the strip along the top, with the two real readings), `Notices` (the mission-start card, the night's report and the ending box), `HomeScreen` (the helmet, the list, the button and the timer that moves the helmet), `Helmet3D` (the helmet and its cached pictures), `Draw` (the colours, fonts and helpers they share), and the five picture files |
| `src/outpost/weather/` | Weather | `ArchiveWeatherSource` and its archive |
| `src/outpost/radiation/` | Radiation | `ArchiveRadiationSource` and its archive |
| `Build.java`, `index.html`, `SpaceGuy.jar`, `tools/` | Shared | Build script, browser page, built jar, the data download script and the picture shrinking program |

A feature module imports from `outpost.shared` and from nowhere else in `outpost`. Modules
never import each other or `outpost.app`. Only `Main` names module classes, in the block
marked "Module wiring", so replacing a module means editing files in its own folder plus
at most its own lines in that block. `Main` also reads the end of the weather archive from
`ArchiveWeatherSource.lastDate()` and gives it to the controller, which needs it to choose
a date for "Surprise me". `Balance` is handed the same sources by `Main`.

## Contracts (`outpost.shared`)

| Type | Meaning |
|---|---|
| `Sol(number, earthDate)` | The current game day. `number` starts at 1. `earthDate` is the key into the data archives. |
| `WeatherConditions(minTempC, maxTempC, sunlight, missionSol, pressurePa, uv, sunrise, sunset, measuredOn, ls)` | One sol's weather. `sunlight` is 0.0 to 1.0, the share of clear-sky solar power the sky lets through. `measuredOn` is the Earth date of the rover reading used. `ls` is the solar longitude in degrees. A three-argument constructor leaves the rest unknown. |
| `UvIndex` | `UNKNOWN`, `LOW`, `MODERATE`, `HIGH`, `VERY_HIGH` |
| `RadiationConditions(doseMsv, solarEvent, detail)` | One sol's unshielded dose in millisieverts, whether a solar event caused a spike, and one sentence about the event for display (or null). A two-argument constructor leaves `detail` null. |
| `Place` | Where the astronaut can be sent: `HABITAT`, `GREENHOUSE`, `PANELS`, `DIGGER`. Each has one job. |
| `Soil` | Not state: helpers that turn `Outpost.marsSoil` into a pH (`ph`), a zone (`zone`: `HEALTHY`, `TIRED`, `STRESSED`) and a plant growth speed (`growth`). The constants and their sources are in its Javadoc. |
| `Outpost(clock, energy, panelsClean, shield, cropsReady, foodDays, health, heaterOn, lampsOn, place, walkLeft, tally, marsSoil)` | Everything that changes as time passes. `clock` is Mars local time in hours and runs past 24 during the night. `place` is where the astronaut is or is walking to; `walkLeft` is the Mars hours of walking left; `marsSoil` is the share of the greenhouse soil that is Mars soil (0 to 1). |
| `Tally(radiation, cold, hunger, rest, energyUsed, foodEaten, mealsShort, hoursHeaterOff, hoursNoPower)` | Running totals since the mission began. `since(earlier)` gives what happened between two of them. |
| `Readings(sun, dayShare, nightShare, outsideC, sunStrength, energyIn, energyOut, nightNeed, heaterWorking, lampsWorking, diggerWorking, shieldBlocks, harm)` | What the instruments read at this moment, worked out by the rules so that the view does no sums. `nightNeed` is the energy the coming night will take with the switches as they are. `harm` is why health is falling now. |
| `Harm` | `NONE` (health is steady or coming back), `RADIATION`, `COLD`: whichever is taking the most health at this moment. Hunger is not here, because it strikes once, at bedtime. |
| `GameState(phase, missionSols, mission, sol, weather, radiation, outpost, now, night, energyCapacity, paused, scenarios)` | Immutable snapshot of everything a panel may display. `now` is the `Readings`. `night` is the `Tally` of the last night, or null before the first. `scenarios` runs from the gentlest mission to the hardest. On the home screen there is no mission: everything but `phase`, `missionSols`, `energyCapacity` and `scenarios` is null, so check `phase` first. |
| `Scenario(title, start, end, hard, story, helmet)` | One mission: its name, the Earth dates of its first and last sols, about six words on what makes it hard, what really happened then, and its helmet. |
| `Helmet(dust, frost, storm, wear)` | The state of the helmet that stands for a mission: four dials from 0.0 to 1.0, worked out by the controller from the mission's weather and radiation. |
| `Phase` | `BRIEFING` (the home screen), `READY` (the mission-start card), `DAY` (the clock is running), `NIGHT` (the astronaut is asleep and the clock runs fast), `DAWN` (the night's report), `WON`, `LOST`. |
| `PlayerActions` | On the home screen: `start(scenario)`, `startSurprise()`. On a card: `startSol()`. By day: `send(place)`, `switchHeater(on)`, `switchLamps(on)`, `sleep()`. Any time in a mission: `pause(paused)`, `restart()`, `home()`. The switches and `send` still work while the clock is paused, which the lesson relies on. Implemented by `GameController`. |
| `Glyphs` | Small icons and their colours, drawn in code. |
| `WeatherSource.weatherFor(Sol)` | Implemented by the weather module. |
| `RadiationSource.radiationFor(Sol)` | Implemented by the radiation module. |
| `CsvResource.read(owner, name)` | Reads a bundled archive from the jar: comment lines and data rows. |

Both sources must return the same answer every time they are asked about the same sol.

## Rules

1. **The controller owns all state.** `GameController` holds the only mutable game state.
   It creates a new `GameState` on every tick of the clock and on every `PlayerActions`
   call that changes something, and sends it to every listener.
2. **Time moves in one place.** `Rules.step(outpost, weather, radiation, hours)` is a pure
   function that advances the outpost by a span of Mars time. The live clock, the night
   and the balance simulation all go through it. It knows nothing about real time, timers
   or phases. `GameController.advance(seconds)` turns real seconds into Mars hours and
   handles sunset and sunrise; `Main` owns the Swing timer that calls it 20 times a second.
   A test calls `advance` or `step` directly and never waits for a clock. (The home screen
   has a timer of its own to turn its helmet. It moves no game time.)
3. **Panels display state and emit actions.** A panel has a `show(GameState)` method that
   stores the snapshot and calls `repaint()`. To act, it calls the `PlayerActions` it was
   given in its constructor. A panel never names the controller class.
4. **Painting changes nothing.** `paintComponent` reads the stored snapshot and draws. It
   must not advance, compute or store simulation state, because Swing repaints at
   unpredictable times. The view does keep its own display state: which card is open, which
   row of the home screen's list is picked and how far its helmet is turned, what the mouse
   is over, where the astronaut's figure is drawn, and its cached pictures.
5. Everything runs on the Swing event thread.

Current controller behaviour: the controller starts on the home screen (`Phase.BRIEFING`).
When it is made it builds the list of scenarios, gentlest first, and works out each one's
helmet from the two sources. `start` and `startSurprise` work only on the home screen. They
load sol 1 on the chosen Earth date with 50 energy, full health, 4 days of food, clean
panels, no soil bank, the heater on and the lamps off, and show the mission-start card
(`READY`). The surprise date is chosen in the controller at that moment, never while
painting, and its helmet is worked out then. `startSol` starts the clock (`DAY`). At sunset, or on
`sleep`, the astronaut goes to the habitat and eats, and the phase is `NIGHT` until the
clock reaches the sol's sunrise time plus 24 hours. Then the night's `Tally` is worked out,
the sol number and the Earth date go up by one, the new sol's weather and radiation are
loaded and the phase is `DAWN` until `startSol`. After the night of the last sol the phase
is `WON`. Whenever health reaches 0 the phase is `LOST`. `advance` does nothing while
`paused` is set or the phase is not `DAY` or `NIGHT`, so nothing is repainted then. The
controller has no lesson of its own: the view's lesson is a pause, like an open card.
`restart` returns to the mission-start card of the same mission, on the same dates, and
lifts any pause. `home` drops the mission and shows the home screen again.

## How the view shows the state

The play screen is a picture with controls on it. The picture is a photo of the ground, four
building pictures, the astronaut drawn in code, and the light of the hour laid over all of
that. The controls are drawn over the light, so they stay bright at night. The screen is
kept quiet by giving every fact one home:

- **The strip along the top** holds what the player is looking after: the sol and the time
  of day, health and energy. Between health and energy are the day's two real readings,
  small: the temperature outside now, and today's radiation.
- **A round button** beside the digger, the solar panels and the greenhouse holds the one or
  two numbers that matter there. **A switch** beside the habitat (HEAT) and the greenhouse
  (LAMPS) flips the heater and the lamps.
- **A card**, opened by a click on one of the two readings in the strip, holds everything
  else about the weather or the radiation. The clock stops while a card is open.

Before adding something to the screen, look for the place it already belongs.
`UI-SIMPLIFY.md` says how to keep the screen quiet.

### The table of places

Every place on the play screen is one row of `Site`, an enum in `outpost.view`, in the
800 x 600 layout. The pictures, the round buttons, the switches, the clicks, the astronaut's
walk and label and the guide's ring all read it. No place has a number anywhere else, except
that `tools/ShrinkPictures.java` repeats the four drawn sizes.

| Site | Picture, drawn at (left, top, width x height) | Where the astronaut's feet are | Round button, 76 x 76 (left, top) | Switch, 56 x 16 (left, top) |
|---|---|---|---|---|
| `HABITAT` | `base.png` (350, 375, 175 x 150) | (388, 502) | none | HEAT (319, 314) |
| `GREENHOUSE` | `greenh.png` (625, 375, 105 x 85) | (677, 504) | (639, 302) | LAMPS (594, 384) |
| `PANELS` | `panel.png` (80, 470, 80 x 50) | (184, 508) | (82, 390) | none |
| `DIGGER` | `drill.png` (327, 180, 60 x 50) | (424, 236) | (309, 100) | none |

**Where a click counts** is worked out from the same rows, so it cannot drift from the art.
A click counts on a round button, on a switch (with the 2-unit dark frame round it), and on
a picture wherever the picture is not see-through, or within 4 units of such a point
(`Pictures.solid`). Open ground does nothing, even inside a picture's rectangle. A click on
a building's picture or its round button sends the astronaut there; a click on the habitat's
picture sends them home to rest.

The photo covers the layout exactly, so a building stands on the same ground whatever the
shape of the window. Where the panel is wider or taller than the layout, the margins are
filled with the same photo, blurred, mirrored outward from each edge and a little darker.

### What shows what

| State | In the view |
|---|---|
| `sol`, `missionSols` | Top strip |
| `now.dayShare`, `now.nightShare` | The small arc in the top strip. The Sun crosses it by day and the Moon by night. |
| `now.sun`, `weather.sunlight` | One see-through tint over the ground, the buildings and the astronaut (`Light`): navy for the hour, tan for dust in the air. It is as dark at sunrise and sunset as at night, and clear at midday under a clear sky. The "Outside" reading is headed "Dusty sky" below 50% sunlight. The percentage is on the weather card. |
| `now.outsideC` | The "Outside" reading in the strip |
| `minTempC`, `maxTempC`, `pressurePa`, `sunrise`, `sunset`, `uv`, `missionSol`, `measuredOn`, `now.sunStrength` | Weather card. It says so when the rover has no reading for the date. |
| `doseMsv`, `solarEvent`, `detail` | The "Radiation" reading in the strip, pink on a storm sol; pink streaks over the picture; a banner under the strip with the storm's details; and the radiation card |
| `outpost.health` | Bar in the top strip |
| `outpost.foodDays`, `outpost.marsSoil` | The greenhouse's round button: "pH 6.8" and "Food 3.0d" |
| `now.harm` | "cold" or "radiation" beside the health bar's name, with a down arrow, while health is falling and the clock runs |
| `outpost.energy`, `now.energyIn`, `now.energyOut` | Energy bar in the top strip, with "filling", "draining", "full" or "empty" beside its name while the clock runs |
| `now.nightNeed` | The moon mark on the energy bar. It turns red when the battery cannot hold that much, or when the afternoon has come and it does not hold that much yet. |
| `outpost.heaterOn`, `now.heaterWorking` | The HEAT switch: green ON, grey OFF, dark red ON when there is no energy. A red box under it says so when it is off and freezing outside. |
| `outpost.lampsOn`, `now.lampsWorking` | The LAMPS switch, the same three ways |
| `outpost.cropsReady` | No number. The guide says when food is ready, and the astronaut's label says "Picking food" or "Nothing to pick yet". |
| `outpost.panelsClean` | The solar panels' round button, "57% clean", with a warning edge below 70% |
| `outpost.shield`, `now.shieldBlocks` | The digger's round button, "Shield 23%". The share it stops is on the radiation card and in the night's report. The screen calls the soil bank "the shield" throughout. |
| `outpost.place`, `outpost.walkLeft`, `now.diggerWorking` | The astronaut, and a small label over their head saying what they are doing. By day the astronaut is always drawn: resting, they stand at the habitat's door. At night the figure is not drawn, and "Sleeping" stays at the door with a small helmet in the label. |
| `night` | The night's report, while the phase is `DAWN`: one line for each thing that changed health, then food eaten and energy used. Every number is whole. The health lines are rounded together (`Notices.healthLines`) so that they add up to the headline, and the headline is the change in the health number the strip showed from bedtime to dawn. A cause that comes to 0 gets no line. |
| `outpost.tally` | The ending box: health lost to radiation, cold and hunger over the mission, and which took the most. Whole numbers. |
| `mission` | The mission-start card, while the phase is `READY`, with the mission's helmet |
| `paused` | Not drawn on its own. The open card says "clock stopped", the lesson shows its step, and the bars' trend words are hidden. |
| `phase` | `BRIEFING`: the home screen, drawn by `HomeScreen` in place of the outpost. `READY`, `DAWN`, `WON`, `LOST`: a box in the middle of the screen, drawn by `Notices`, with the buttons and switches hidden. `NIGHT`: the picture is dark and only Change mission and Restart can be clicked. |
| `scenarios` | The rows of the home screen's list, in the list's order. The first is marked "Start here". The picked row's helmet is the large one. |

The scene that was drawn in code showed some of this in the picture itself: lit windows, a
glowing greenhouse, crop rows, panels turning tan, the soil bank, a weather station and a
radiation detector. The pictures do not change, so the buttons, the switches and the two
readings carry those facts now.

**Clicks.** A click on a switch flips it and sends nobody. A click on a reading in the strip
opens its card under the strip and calls `pause(true)`. A second click on the reading closes
it. So does a click anywhere else, which calls `pause(false)` and then does whatever that
click would do anyway. A click on the card itself does nothing. At night and during the
lesson the readings open no card.

**The mouse and the keys.** `OutpostView` owns the panel. While the phase is `BRIEFING` it
passes every press, drag, release, move, wheel turn and key to its `HomeScreen`, with the
mouse's place given in the 800 x 600 layout, and asks it to paint. `HomeScreen` says where a
press would do something, so that `OutpostView` can show the hand.

**The guide**, bottom left, is one line. It names the one thing to do next, in yellow, and
the thing it means is ringed in the same yellow: a round button, a switch, the habitat's
picture, the energy bar or the SLEEP button (`OutpostView.hint`). While the mouse is over
something that can be clicked, the guide says in white what the click will do. The lesson
speaks through the same line and the same ring; while it runs, its row of buttons sits just
above the guide, where the quiet "How to play" button otherwise is. The "About the data"
button opens the credits for the weather and the solar storms. The home screen carries one
line of credit at its foot.

**The astronaut's figure.** The rules say only where the astronaut is going and how many
hours of walking are left. `OutpostView.follow` moves the drawn figure in a straight line
from wherever it stands to the place's standing spot in `Site`, so that it arrives as the
walking runs out. A straight line can cross the habitat's picture, and the figure is then
drawn over it. This is display state, and it is updated in `show`, never while painting.

### How the helmet is drawn

`Helmet3D` is the game's own code and uses no 3D library. The shell (a ball a little taller
than it is wide) and the neck ring are shapes turned on a lathe about the one axis the
helmet turns on. Turning such a shape never changes its outline on the screen, nor the light
on it, so nearly all the work is done once and kept:

| Kept | What it holds | When it is made |
|---|---|---|
| A `Solid`, for each size of picture (two are kept) | For every pixel: how much of it the helmet covers, the light there, and which point of the surface shows there when the helmet faces the front | For a new size only |
| A `Skin`, for each look (six are kept) | The helmet's surface unwrapped into a flat picture, 1025 x 507, with whatever sits on the helmet (dust, scuffs, frost, the stripe, the visor's frame); and a small picture of what the glass mirrors (sky, ground and Sun) | Once for each look: the picked one with the first state, the other four one at each of the next ticks |
| For a skin at a size | The mirror picture and the shine, laid out on the screen's pixels | The first frame of that skin at that size |
| The frame | The last picture of the helmet | Worked out again only if the turn, the mix or the size changed |
| Stills (four are kept) | The helmet on the mission-start card | The first paint of a card |

A frame is one pass over the pixels: look the skin up at the surface point, moved round by
the turn; multiply by the light; and where the skin is clear glass, show the mirror picture.
The mirror picture stays still on the screen while the visor slides across it, which is how
a mirrored ball really looks. The two ear pieces are not lathe shapes and are worked out in
the same pass for each frame. While the look is changing, two skins are looked up and mixed.
When a sol starts, `Helmet3D.rest()` lets go of every skin but the picked one.

**The timer.** `HomeScreen` has a Swing timer of its own, every 40 ms. It runs only while
the phase is `BRIEFING`, the panel is showing and something is moving: the sway (27 degrees
each way over 9 seconds), a drag, the swing back after a drag, a change of look, or skins
still being made. `show` stops it on any other phase, and a hierarchy listener stops it when
the window is hidden or closed. Everything that moves is changed in `HomeScreen.tick` or by
the mouse and the keys, never while painting.

### What painting costs

Painting is kept cheap, because the play screen repaints at every tick of the clock, the
home screen repaints its helmet 25 times a second, and CheerpJ is several times slower than
desktop Java.

- **The play screen.** `Pictures` reads the five picture files once, when the view is made,
  and puts the photo and the four buildings together into one image at the panel's size in
  the screen's own pixels. It is made again only when the panel changes size. An ordinary
  frame draws that one image, the astronaut, one see-through fill for the hour and the dust,
  44 short lines on a storm sol, and then the controls.
- **The home screen.** Everything but the helmet keeps still, so it is kept as a picture,
  made again on a pick, when the mouse goes onto or off a row, and for a new size. A tick
  repaints only the helmet's rectangle.

| Measured at 800 x 600 | Desktop Java | Headless Chrome through CheerpJ |
|---|---|---|
| Play screen, a repaint with the clock running | 0.4 to 0.9 ms | 8.5 to 9 ms, at 16 to 17 repaints a second |
| Home screen, a frame of the turning helmet | 0.5 ms | 2.8 to 3 ms, at 21 to 22 repaints a second |
| Home screen, a frame during a change of look | 0.8 ms | not measured on its own |
| The first paint of the home screen | About 150 ms with the first state | About 2 seconds, most of it CheerpJ's first use of Java2D |
| Making the view (reading the pictures) | About 140 ms | Not measured on its own; the window appears 9 to 11 seconds after the page opens |
| Heap in use: view made; home screen with every helmet ready; a sol in play | 25 MB; 36 MB; 28 MB | Not comparable |

Before the pictures were shrunk, the play screen took 23.5 ms a repaint in the browser, the
view took 600 ms to make and held over 100 MB. Run with `paint-times` to see the figures on
your own machine. Refreshing the kept pictures is the one thing painting writes; it is
drawing work and holds no game state.

If the home screen is too slow somewhere, these are the numbers to change:
`HomeScreen.TURNS_BY_ITSELF` (false: the helmet turns only when dragged, and the timer runs
only during a drag, the swing back and a change of look), `HomeScreen.TICK_MILLIS` (40), and
`Helmet3D.MOST_PIXELS` (512: a helmet shown larger than this is a stretched picture). Three
things proved very slow in the browser and are kept out of code that runs for every frame:
Graphics2D shapes, `Math.pow` and `Math.hypot`, and `Math.round` on doubles.

The view is laid out at 800 x 600 and scales to fit its panel. The play screen repaints when
the state changes or the mouse moves onto or off something clickable, and the state does not
change while the clock is stopped, so an open card, a waiting box or the lesson costs
nothing.

## Not done

- **Picture credits.** Nothing in the repository says where the ground photo and the four
  building pictures came from, or under what licence, so the game credits nobody for them.
  Find out before the game is published, and add the credit to the "About the data" card.
- **Play-testing with children.** The balance is tuned against scripted players only, and
  no child has yet read the lesson's five sentences, told the four helmets apart, or played
  the first minute, which is at sunrise on a dark picture (`Light.NIGHT_ALPHA` sets how dark).
- **Touch.** The home screen and the play screen need only clicks, and a drag on the helmet
  is ordinary mouse events, but neither has been tried on a real tablet or in any browser
  other than headless Chrome.
- **End-of-mission summary.** The ending box gives the result and the health lost to each
  cause over the mission, not a sol-by-sol story.
- **Sol-to-date mapping.** One sol is treated as one Earth day. A real sol is 24 h 39 min,
  so about one Earth date in 37 has no rover row; the game then shows the previous row and
  says so. Dates after the archive ends show its last row, also labelled.
- **Unit tests.** There are none. `Balance` is the only automated check, and it is run by
  hand.
