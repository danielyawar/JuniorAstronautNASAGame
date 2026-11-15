# How to simplify the screen (a guide, not a change)

Nothing in the UI was changed except one line on the greenhouse tag (`Soil pH ...`, in
`chips()` in `OutpostView`). This is what to do yourself.

## What a child sees at once today

By day, all at once: the strip along the top (sol, clock arc, health, food, energy, each with
words like "filling"/"draining"/"cold"), six tags over the buildings (weather, radiation,
habitat with heater switch, greenhouse with lamp switch and now soil, solar panels, digger),
a "Doing" line, a guide line, a storm banner, the SLEEP button and "How to play". Cards open
from the weather station and the radiation detector, and each sunrise opens a night report.
That is about 20 things to read before the first decision.

## The rule to hold to

A 7 to 12 year old can keep about three things in mind. Pick three, show only those, and
let everything else appear only when it needs the player.

Suggested three: **Energy** (will the night get through?), **Food**, **Health**.

## Steps, in the order I would do them

1. **Cut the strip to three meters.** `StatusStrip` draws sol/time, the sun track, health,
   food and energy. Keep health, food and energy. Drop the sun/moon arc (`paintSunTrack`) and
   the trend words (`trend(...)`), which exist only to explain. Move "Sol 3 of 12" to the
   top-right corner in a larger font.
2. **Show a tag only when it matters.** `chips()` in `OutpostView` returns all six tags
   every frame, and `paintComponent` draws every one. Filter the list: weather and radiation
   tags only when `dusty` or `storm` is true; solar panels only when `o.panelsClean() < DUSTY_PANELS`;
   digger only while `o.shield() < 1`. Habitat and greenhouse stay, since they hold the switches.
3. **One line per tag.** Each tag is a title plus a list of `Stat`s. Keep one `Stat` per
   tag. For the greenhouse, choose between "1.5 ready" and the soil line; show the soil
   line only once the zone is `TIRED` or `STRESSED`.
4. **Replace numbers with colour and shape.** Energy and food already have bars. Do the same
   for soil: a small bar that goes green to yellow to red from `Soil.zone(...)`, instead of
   "Soil pH 7.4: Tired". Keep the number on the card for older players.
5. **One instruction at a time.** `hint()` already chooses a single sentence; keep it as the
   only text on the bottom. Remove the "Doing" line (`paintDoing`) or merge it into the hint.
   Add a soil hint at the end of `hint()`, for example: "The soil is tired. Pick the food so
   the clippings go back in."
6. **Fewer words per sentence.** Aim for under 8 words and one verb. "The panels are dusty. Click
   them to brush." becomes "Brush the dusty panels."
7. **Make the two important buttons big.** `SLEEP_BUTTON` is 140 x 38. Make it about 200 x 56 and
   make the lamp and heater switches larger. Children miss small targets.
8. **Cards: keep two.** The weather and detector cards are for older players. Keep them, but
   remove the "Change mission" and "How to play" quiet buttons from the play screen and put them
   behind a single menu icon.
9. **The night report (`Notices.paintNight`)**: show three lines, "Energy left", "Food left",
   "Health", and one sentence on why. Put the detail behind a "More" button.
10. **The lesson**: five steps is too many for a first run. Cut to three: the heater, the
    greenhouse, SLEEP. In `OutpostView` the steps are the `Step` enum (`NONE, HEATER, BATTERY, FOOD, SLEEP, SEND`) around line 928.

## How to check each change

After every step, run `java Build.java`, open the game, and play a minute with someone who
has not seen it. Count how many things they ask about. If it is more than two, cut more.
`java -jar SpaceGuy.jar paint-times` shows if drawing got slower.

Rule 4 in `HANDOFF.md` still applies: painting reads the stored state and changes nothing.
