# Rebuilding the photoreal heads

Requirements: Python 3 with `numpy` and `h5py`.

```bash
cd tools/gnm
curl -L -o gnm_head.npz https://huggingface.co/google/gnm-v3/resolve/main/v3_0/gnm_head.npz
git clone --depth 1 https://github.com/google/GNM.git repo   # landmarks + identity decoder weights
python3 gnm_export.py gnm_head.npz cast.json ../../public/gnm
```

`cast.json` has one entry per character:
- `seed`, `female`, `latent`: which synthetic identity to decode.
- `overrides`: nudges on identity components (`head_000`: build and age, `head_001`: face length vs. fullness).
- `skin`, `lip`, `hairColor`: hex colours.
- `hair`: short, crop, side, spiky, bun or braid.
- `beard`: full or moustache.
- `moustache`, `stubble`, `kajal`, `browThick`, `forehead`: grooming.

Each head is about 1.1 MB (int16 quantized, sparse expression morphs). The
markers (vibhuti, bindi and so on) are added at runtime from `src/data/gnmCast.ts`.
