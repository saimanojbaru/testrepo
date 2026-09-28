# Realistic bodies (Anny + CMU motion capture)

- **Anny** body model (NAVER LABS, Apache-2.0) built on **MakeHuman** assets (CC0).
  Bodies are synthetic: sex, age, weight, muscle and height sliders. No scans.
- **CMU Graphics Lab Motion Capture Database** (mocap.cs.cmu.edu): "The motion
  capture data may be copied, modified, or redistributed without permission."

Setup (Python 3.11, CPU is fine):

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install --index-url https://download.pytorch.org/whl/cpu torch
pip install anny numpy scipy
mkdir -p cmu && for f in 02/02.asf 02/02_01.amc 137/137.asf 137/137_28.amc 80/80.asf 80/80_25.amc; do
  curl -sf -o cmu/$(basename $f) http://mocap.cs.cmu.edu/subjects/$f; done
python build_ramesh.py
```

How it works:
- `anny_export.py` builds a body from `bodies.json`. Anny's `age` runs newborn -1/3,
  baby 0, child 1/3, **young adult (~25) 2/3, old (~90) 1**; `gender` is 0 male, 1 female.
  It cuts Anny's head (the GNM head sits on the neck), sews clothes as offset shells of
  the body surface (so they share its skin weights), and writes a rigged `.glb`.
- `cmu_retarget.py` maps CMU ASF/AMC onto the Anny skeleton: limbs by bone direction,
  spine/neck/head by world rotation. Walks are cut to one clean gait cycle, made in
  place, with the average head tilt removed. Standing clips have their root yaw locked
  so the game's AI owns facing.
- The game plays `walk` / `idle` / `phone`, picked from the NPC's measured speed and
  phone state (`src/ai/RealBody.ts`).
