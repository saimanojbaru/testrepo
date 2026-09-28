# Cast: what was chosen vs. what was defaulted

Every visible or audible property of the nine characters, and where its value came from.
**You** = stated by the game's author in a brief or review. **Default** = picked by Claude to
fill a gap. Defaults are placeholders until someone chooses them on purpose.

## A structural problem first: three cast definitions

| File | What it defines | Used for |
|---|---|---|
| `src/data/npcs.ts` (`NPC_LOOKS`) | Cartoon look: skin, hair style, height, accessories, voice | Low-poly NPCs (and the >16 m fallback) |
| `tools/gnm/cast.json` + `src/data/gnmCast.ts` | Realistic head: face seed, skin, hair, grooming, markers, rest expression, gait | Realistic heads |
| `tools/anny/bodies.json` | Realistic body: sex, age, build, height, outfit | Realistic bodies |

They have already drifted:

| | Cartoon | Realistic head | Realistic body |
|---|---|---|---|
| Rinku hair | swoop | spiky | — |
| Priya hair | bun | braid | — |
| Ramesh hair | short | side part | — |
| Priya outfit | kurti + clipboard | — | kurta + gold dupatta |
| Skin | 9 tones, L* 37–75 | 9 tones, L* 23–66 | same as head |

**Recommendation:** one cast file (`src/data/cast.ts`) that all three read from, before the
other eight bodies are built.

## Per field

| Field | Source | Notes |
|---|---|---|
| Names, roles | You (Indian IT brief) | |
| Home region / city | You (regions); cities default | You chose the six regions; the specific cities (Jorhat, Thrissur, Warangal…) are defaults |
| "One marker per character", subtle | You | |
| Which marker (vibhuti, chandrakor…) | Default | Rule is yours, picks are mine |
| **Ages** | **You (latest review)** | Rinku 22, Priya ~30, Deepak ~33, Rohit ~28, Kavita ~35, Srinivas ~45, Lakshmi ~50, Ramesh ~50, Sanjay ~45. Previously defaulted (Kavita 50, Priya 35, Deepak 38): now corrected |
| **Sex / gender** | Default for **Rinku**; implied for others | Never stated for Rinku. Built male; your brief says "she". **Needs your decision** |
| Skin tone | Rule is yours ("spread, don't shift"); values default | See TUNING.md |
| Face identity (GNM seed) | Default | Kavita and Rinku recast once at your request |
| Hair style | Default | And inconsistent across files (above) |
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
