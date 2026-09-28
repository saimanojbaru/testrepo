# Photoreal heads: provenance

These `.glb` heads are generated from **GNM Head v3.0** by Google
(https://github.com/google/GNM, weights at https://huggingface.co/google/gnm-v3),
licensed under the Apache License 2.0 (copy in `LICENSE-GNM.txt`). They are
derived works: synthetic identities sampled from GNM's identity model, with
expressions solved from its expression basis, baked by `tools/gnm/gnm_export.py`.

No person was scanned or photographed. The identity decoder was given a
sex label and equal (25%) weight on each of its four ethnicity inputs, so no
face is pushed toward any single dataset category. Regional identity comes from
skin tone, grooming and one cultural marker per character, not from facial
structure.
