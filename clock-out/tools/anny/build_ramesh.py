"""Rebuild public/anny/ramesh.glb: Anny body + sewn clothes + CMU walk/idle/phone.
Run from tools/anny with CMU files in ./cmu (see README.md)."""
import anny_export as AE
import cmu_retarget as C

spec = AE.body_spec('ramesh')
out = '../../public/anny/ramesh.glb'
AE.export(out, spec)
v, W = AE.build(spec)
C.add_animations(out, W, {
    'walk': C.clip(W, 'cmu/02.asf', 'cmu/02_01.amc', 'walk'),
    'idle': C.segment(W, 'cmu/137.asf', 'cmu/137_28.amc', 120, 2.0, 4.0, face='root'),
    'phone': C.segment(W, 'cmu/80.asf', 'cmu/80_25.amc', 60, 3.0, 5.0, face='root'),
})
print('wrote', out)
