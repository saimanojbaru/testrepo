# Rebuilding the photoreal heads

Requirements: Python 3 with `numpy` and `h5py`.

```bash
cd tools/gnm
curl -L -o gnm_head.npz https://huggingface.co/google/gnm-v3/resolve/main/v3_0/gnm_head.npz
git clone --depth 1 https://github.com/google/GNM.git repo   # landmarks + identity decoder weights
npm run cast:export          # from the repo root: regenerates tools/cast.json
python3 gnm_export.py gnm_head.npz ../cast.json ../../public/gnm [id ...]
```

The cast lives in **`src/data/cast.ts`** (one source of truth for cartoon looks, heads and
bodies). `tools/cast.json` is generated from it and holds each character's finished head
spec (seed, skin, hair, grooming); do not edit it by hand. `npm run check:cast` fails if
it is stale.

Each head is about 1.1 MB (int16 quantized, sparse expression morphs). The
markers (vibhuti, bindi and so on) are added at runtime from `src/data/gnmCast.ts`.
