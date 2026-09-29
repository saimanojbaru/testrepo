# Cast: what was chosen vs. what was defaulted

Every visible or audible property of the nine characters, and where its value came from.
**You** = stated by the game's author in a brief or review. **Default** = picked by Claude to
fill a gap. Defaults are placeholders until someone chooses them on purpose.

## One cast file

Every character is defined once, in **`src/data/cast.ts`**. Everything else reads from it:

| Reader | What it takes |
|---|---|
| `src/data/npcs.ts` | Names, roles, cartoon look (skin lifted +10 L*, hair mapped through `HAIR`, markers as accessories) |
| `src/data/gnmCast.ts` | Realistic head markers, glasses, gait, rest expression, body |
| `tools/cast.json` (generated: `npm run cast:export`) | Finished head and body specs for the Python exporters |

`npm run check:cast` fails if `tools/cast.json` is stale. `npm run cast:defaults` prints, per
character, which fields you chose (the `chosen` list in `cast.ts`) and which are still defaults.
**To choose a field, edit its value in `cast.ts` and add its name to that character's `chosen`.**

Consolidation reproduced every committed realistic head and Ramesh's body byte for byte. The drift
it resolved (the realistic values won, since those are the assets being built):

| | Before (cartoon / head / body) | Now |
|---|---|---|
| Rinku hair | swoop / spiky / — | `messy` |
| Priya hair | bun / braid / — | `braid` |
| Ramesh hair | short / side part / — | `receding` |
| Priya outfit | kurti + clipboard / — / kurta + gold dupatta | kurta + gold dupatta |
| Skin | 9 tones L* 37–75 / L* 23–66 / same as head | one tone each; cartoon derives +10 L* |
| Sanjay glasses | black / silver | `silver` |
| Priya jhumka | no / yes | yes |

## Settled: skin range (do not re-litigate)

The author's review cited L* 20–75 for Indian skin; that figure had no source and was the
author's error, recorded here at their request. Published colorimetry of Indian skin runs
roughly **L* 35–65**, and 25–30 is already very dark. The cast's **L* 23** dark end is fine.
The rule stands: spread the range, don't shift it, and check lighting before touching tones.

## Per field

| Field | Source | Notes |
|---|---|---|
| Names, roles | You (Indian IT brief) | |
| Home region / city | You (regions); cities default | You chose the six regions; the specific cities (Jorhat, Thrissur, Warangal…) are defaults |
| "One marker per character", subtle | You | |
| Which marker (vibhuti, chandrakor…) | Default | Rule is yours, picks are mine |
| **Ages** | **You (latest review)** | Rinku 22, Priya ~30, Deepak ~33, Rohit ~28, Kavita ~35, Srinivas ~45, Lakshmi ~50, Ramesh ~50, Sanjay ~45. Previously defaulted (Kavita 50, Priya 35, Deepak 38): now corrected |
| **Sex / gender** | Default for **Rinku**; implied for others | Never stated for Rinku. Built male as a placeholder (`UNDECIDED` comment in `cast.ts`); your brief says "she". **Needs your decision, in `cast.ts` only** |
| Skin tone | Rule is yours ("spread, don't shift"); values default | See TUNING.md |
| Face identity (GNM seed) | Default | Kavita and Rinku recast once at your request |
| Hair style | Default | Now one value per character (above) |
| Height, build, weight | Default | Ramesh's paunch, Rohit's height: my picks |
| Voice pitch | Default | Rinku 290, Kavita 320, Ramesh 150… |
| Walk speed | Default | Also sets walk tempo in game |
| Gait (tempo, lean, arms) | Default | Posture-as-personality guesses |
| Clothing colours | Default | |
| Lakshmi: salwar kameez + apron (not saree) | You | |
| Gen Z wardrobe (Rinku, Deepak, Priya, Rohit) | You | Not built yet (held with the bodies) |
| Personality habits | Lakshmi (tumbler, squint): you. The other eight: drafts by Claude, not approved |
| Rest expressions (Kavita smirk, Rinku eager) | Default | |
| Accessories (kara, gamosa, bangles, mangalsutra…) | Default | Mangalsutra = married, for Kavita and Lakshmi: a biographical fact nobody chose |
| Dialogue voice, barks | You reviewed (Americanism pass) | |
