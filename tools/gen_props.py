#!/usr/bin/env python3
"""Generate procedural prop sprites + chapter background stubs.

Outputs:
  data/sprites/*.png             — drag-and-drop props (paratha, tiffin, etc.)
  data/sprites/*.png.import      — Godot import config (lossless, no mipmaps)
  chapters/chapter_NN_*/backgrounds/*.jpg — fallback warm-gradient backgrounds
"""
from __future__ import annotations
import math
import os
import random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = os.path.dirname(__file__)
SPRITES_DIR = os.path.join(HERE, "..", "data", "sprites")
HUD_DIR = os.path.join(HERE, "..", "data", "sprites", "hud")
CHAPTERS_DIR = os.path.join(HERE, "..", "chapters")
FONT_PATH = os.path.join(HERE, "..", "fonts", "LiberationSerif-Bold.ttf")

SPRITE_SIZE = 256
HUD_SIZE = 128
BG_W, BG_H = 1080, 1920

GODOT_IMPORT = """[remap]

importer="texture"
type="CompressedTexture2D"
uid="uid://{uid}"
path="res://.godot/imported/{name}.png-{hash_placeholder}.ctex"
metadata={{
"vram_texture": false
}}

[deps]

source_file="res://data/sprites/{name}.png"
dest_files=["res://.godot/imported/{name}.png-{hash_placeholder}.ctex"]

[params]

compress/mode=0
compress/high_quality=false
compress/lossy_quality=0.7
compress/hdr_compression=1
compress/normal_map=0
compress/channel_pack=0
mipmaps/generate=false
mipmaps/limit=-1
roughness/mode=0
roughness/src_normal=""
process/fix_alpha_border=true
process/premult_alpha=false
process/normal_map_invert_y=false
process/hdr_as_srgb=false
process/hdr_clamp_exposure=false
process/size_limit=0
detect_3d/compress_to=1
"""


def bleed_edges(img: Image.Image, passes: int = 2) -> Image.Image:
	"""Bleed visible RGB outward into transparent pixels to prevent halos."""
	img = img.convert("RGBA")
	px = img.load()
	w, h = img.size
	for _ in range(passes):
		for y in range(h):
			for x in range(w):
				if px[x, y][3] == 0:
					# Sample 4 neighbours; copy the first opaque one's RGB.
					for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
						nx, ny = x + dx, y + dy
						if 0 <= nx < w and 0 <= ny < h and px[nx, ny][3] > 0:
							r, g, b, _ = px[nx, ny]
							px[x, y] = (r, g, b, 0)
							break
	return img


def new_sprite(transparent: bool = True) -> Image.Image:
	mode = "RGBA"
	bg = (0, 0, 0, 0) if transparent else (255, 255, 255, 255)
	return Image.new(mode, (SPRITE_SIZE, SPRITE_SIZE), bg)


def save_sprite(img: Image.Image, name: str) -> None:
	img = bleed_edges(img)
	path = os.path.join(SPRITES_DIR, f"{name}.png")
	img.save(path, "PNG", optimize=True, compress_level=9)
	# Generate import sidecar with a deterministic UID.
	uid = abs(hash(name)) % (2 ** 60)
	hash_placeholder = "%016x" % (abs(hash(name + "_h")) % (2 ** 64))
	with open(path + ".import", "w") as f:
		f.write(GODOT_IMPORT.format(uid=uid, name=name, hash_placeholder=hash_placeholder))
	print(f"sprite: {path}")


# ----------------------------- prop generators -----------------------------

def gen_paratha() -> None:
	img = new_sprite()
	d = ImageDraw.Draw(img)
	cx, cy = SPRITE_SIZE / 2, SPRITE_SIZE / 2
	# Outer disc
	d.ellipse([18, 18, SPRITE_SIZE - 18, SPRITE_SIZE - 18], fill=(212, 144, 65, 255))
	# Inner ring (lighter)
	d.ellipse([36, 36, SPRITE_SIZE - 36, SPRITE_SIZE - 36], fill=(232, 175, 92, 255))
	# Char marks
	rng = random.Random(1)
	for _ in range(28):
		ang = rng.random() * 2 * math.pi
		r = rng.uniform(20, 90)
		x = cx + math.cos(ang) * r
		y = cy + math.sin(ang) * r
		rad = rng.uniform(2, 6)
		d.ellipse([x - rad, y - rad, x + rad, y + rad], fill=(95, 55, 22, 200))
	save_sprite(img, "paratha")


def gen_tiffin(closed: bool) -> None:
	img = new_sprite()
	d = ImageDraw.Draw(img)
	# Body cylinder
	d.rounded_rectangle([28, 70, SPRITE_SIZE - 28, SPRITE_SIZE - 30],
	                    radius=20, fill=(178, 192, 205, 255), outline=(95, 110, 125, 255), width=4)
	# Top ellipse (rim)
	d.ellipse([28, 50, SPRITE_SIZE - 28, 110], fill=(208, 218, 228, 255), outline=(95, 110, 125, 255), width=4)
	if closed:
		# Lid dome
		d.rounded_rectangle([20, 28, SPRITE_SIZE - 20, 70], radius=14,
		                    fill=(190, 202, 215, 255), outline=(95, 110, 125, 255), width=4)
		d.ellipse([SPRITE_SIZE / 2 - 18, 12, SPRITE_SIZE / 2 + 18, 32],
		          fill=(160, 174, 190, 255), outline=(80, 90, 105, 255), width=2)
		# Steam wisps
		for i, dx in enumerate([-30, 0, 30]):
			y0 = -10 + (i * 4)
			d.arc([SPRITE_SIZE / 2 + dx - 14, y0, SPRITE_SIZE / 2 + dx + 14, y0 + 24],
			      start=200, end=340, fill=(255, 240, 220, 140), width=3)
	else:
		# Open: dark interior
		d.ellipse([42, 60, SPRITE_SIZE - 42, 100], fill=(50, 58, 70, 255))
		# Floating lid offset above-left
		d.rounded_rectangle([8, 8, SPRITE_SIZE - 60, 38], radius=12,
		                    fill=(190, 202, 215, 230), outline=(95, 110, 125, 230), width=3)
	save_sprite(img, "tiffin_closed" if closed else "tiffin_open")


def gen_match(lit: bool) -> None:
	img = new_sprite()
	d = ImageDraw.Draw(img)
	# Wooden stem
	d.rectangle([SPRITE_SIZE / 2 - 6, 80, SPRITE_SIZE / 2 + 6, SPRITE_SIZE - 30],
	            fill=(160, 110, 70, 255), outline=(90, 60, 35, 255), width=2)
	# Head
	d.ellipse([SPRITE_SIZE / 2 - 18, 50, SPRITE_SIZE / 2 + 18, 90],
	          fill=(180, 50, 38, 255) if not lit else (60, 35, 20, 255),
	          outline=(110, 30, 22, 255), width=2)
	if lit:
		# Flame
		flame_pts = [
			(SPRITE_SIZE / 2, 10),
			(SPRITE_SIZE / 2 + 16, 40),
			(SPRITE_SIZE / 2 + 10, 65),
			(SPRITE_SIZE / 2 - 10, 65),
			(SPRITE_SIZE / 2 - 16, 40),
		]
		d.polygon(flame_pts, fill=(255, 200, 80, 255))
		d.polygon([(SPRITE_SIZE / 2, 25),
		           (SPRITE_SIZE / 2 + 8, 50),
		           (SPRITE_SIZE / 2 - 8, 50)],
		          fill=(255, 245, 200, 255))
	save_sprite(img, "match_lit" if lit else "match_unlit")


def gen_lamp(lit: bool) -> None:
	img = new_sprite()
	d = ImageDraw.Draw(img)
	# Base
	d.rounded_rectangle([60, SPRITE_SIZE - 70, SPRITE_SIZE - 60, SPRITE_SIZE - 30],
	                    radius=8, fill=(120, 90, 50, 255), outline=(60, 40, 20, 255), width=3)
	# Glass chimney
	chimney_fill = (255, 220, 150, 200) if lit else (180, 180, 180, 140)
	d.polygon([(80, SPRITE_SIZE - 70), (SPRITE_SIZE - 80, SPRITE_SIZE - 70),
	           (SPRITE_SIZE - 95, 60), (95, 60)],
	          fill=chimney_fill, outline=(110, 90, 60, 255))
	if lit:
		# Glow halo
		halo = Image.new("RGBA", (SPRITE_SIZE, SPRITE_SIZE), (0, 0, 0, 0))
		hd = ImageDraw.Draw(halo)
		hd.ellipse([20, SPRITE_SIZE / 2 - 90, SPRITE_SIZE - 20, SPRITE_SIZE / 2 + 90],
		           fill=(255, 200, 100, 110))
		halo = halo.filter(ImageFilter.GaussianBlur(radius=18))
		img = Image.alpha_composite(halo, img)
		d = ImageDraw.Draw(img)
		# Wick flame
		d.polygon([(SPRITE_SIZE / 2, 100), (SPRITE_SIZE / 2 + 14, 150),
		           (SPRITE_SIZE / 2 - 14, 150)],
		          fill=(255, 230, 130, 255))
	save_sprite(img, "lamp_lit" if lit else "lamp_unlit")


def gen_bill(name: str, accent: tuple) -> None:
	img = new_sprite()
	d = ImageDraw.Draw(img)
	# Paper
	d.rounded_rectangle([18, 28, SPRITE_SIZE - 18, SPRITE_SIZE - 28],
	                    radius=10, fill=(248, 240, 222, 255), outline=(160, 145, 115, 255), width=2)
	# Header stripe
	d.rectangle([18, 28, SPRITE_SIZE - 18, 70], fill=accent + (255,))
	# Title
	try:
		font_hdr = ImageFont.truetype(FONT_PATH, 28)
		font_line = ImageFont.truetype(FONT_PATH, 22)
		d.text((36, 36), name, fill=(250, 245, 230), font=font_hdr)
	except IOError:
		d.text((36, 36), name, fill=(250, 245, 230))
		font_line = None
	# Scribble lines
	for i in range(5):
		y = 90 + i * 22
		d.rectangle([36, y, SPRITE_SIZE - 50, y + 6], fill=(180, 165, 130, 220))
	# Amount
	if font_line is not None:
		d.text((36, SPRITE_SIZE - 70), "₹ XXXX", fill=(80, 55, 25), font=font_line)
	save_sprite(img, f"bill_{name.lower().replace(' ', '_')}")


def gen_tray(label: str, warm: bool) -> None:
	img = new_sprite()
	d = ImageDraw.Draw(img)
	wood = (155, 110, 60, 255) if warm else (110, 90, 70, 255)
	d.rounded_rectangle([12, 60, SPRITE_SIZE - 12, SPRITE_SIZE - 30],
	                    radius=16, fill=wood, outline=(60, 40, 20, 255), width=4)
	d.rectangle([28, 80, SPRITE_SIZE - 28, SPRITE_SIZE - 60],
	            fill=(60, 45, 30, 255), outline=(40, 28, 20, 255), width=2)
	try:
		font = ImageFont.truetype(FONT_PATH, 30)
		bbox = d.textbbox((0, 0), label, font=font)
		tw = bbox[2] - bbox[0]
		d.text(((SPRITE_SIZE - tw) // 2, 18), label,
		       fill=(252, 235, 205, 255), font=font)
	except IOError:
		d.text((40, 18), label, fill=(252, 235, 205, 255))
	save_sprite(img, f"tray_{label.lower().replace(' ', '_')}")


def save_hud_icon(img: Image.Image, name: str) -> None:
	img = bleed_edges(img)
	path = os.path.join(HUD_DIR, f"{name}.png")
	img.save(path, "PNG", optimize=True, compress_level=9)
	uid = abs(hash("hud_" + name)) % (2 ** 60)
	hash_placeholder = "%016x" % (abs(hash(name + "_hud_h")) % (2 ** 64))
	import_text = GODOT_IMPORT.format(uid=uid, name=name, hash_placeholder=hash_placeholder)
	import_text = import_text.replace(
		'source_file="res://data/sprites/{name}.png"'.format(name=name),
		'source_file="res://data/sprites/hud/{name}.png"'.format(name=name),
	)
	with open(path + ".import", "w") as f:
		f.write(import_text)
	print(f"hud: {path}")


def _hud_canvas() -> Image.Image:
	return Image.new("RGBA", (HUD_SIZE, HUD_SIZE), (0, 0, 0, 0))


IVORY = (250, 245, 230, 255)
IVORY_DIM = (220, 210, 188, 255)


def gen_hud_family() -> None:
	img = _hud_canvas()
	d = ImageDraw.Draw(img)
	# Adult silhouette (left), taller
	d.ellipse([20, 18, 52, 50], fill=IVORY)                       # head
	d.rounded_rectangle([16, 50, 56, 108], radius=14, fill=IVORY) # body
	# Child silhouette (right), shorter + bigger head proportion
	d.ellipse([72, 32, 100, 60], fill=IVORY)                      # head
	d.rounded_rectangle([72, 60, 104, 108], radius=12, fill=IVORY)# body
	# Holding hands — small bar connecting
	d.rectangle([54, 78, 74, 84], fill=IVORY_DIM)
	save_hud_icon(img, "icon_family")


def gen_hud_career() -> None:
	img = _hud_canvas()
	d = ImageDraw.Draw(img)
	# Open book (lower half)
	d.polygon([(16, 92), (60, 80), (60, 110), (16, 116)], fill=IVORY)   # left page
	d.polygon([(60, 80), (108, 92), (108, 116), (60, 110)], fill=IVORY) # right page
	d.line([(60, 80), (60, 110)], fill=(110, 90, 60, 255), width=2)
	# Upward arrow above book
	d.polygon([(62, 18), (78, 40), (68, 40), (68, 64), (56, 64), (56, 40), (46, 40)], fill=IVORY)
	save_hud_icon(img, "icon_career")


def gen_hud_finance() -> None:
	img = _hud_canvas()
	d = ImageDraw.Draw(img)
	# Coin stack — 3 ellipses
	for i, y in enumerate([88, 70, 52]):
		shade = IVORY if i % 2 == 0 else IVORY_DIM
		d.ellipse([26, y, 102, y + 22], fill=shade, outline=(120, 95, 50, 220), width=2)
	# Rupee glyph on top coin — simplified ₹ as two horizontal bars + a curved stroke
	cx = 64
	d.rectangle([cx - 14, 58, cx + 14, 62], fill=(120, 80, 30, 255))
	d.rectangle([cx - 14, 65, cx + 14, 69], fill=(120, 80, 30, 255))
	d.line([(cx - 10, 58), (cx + 8, 76)], fill=(120, 80, 30, 255), width=3)
	save_hud_icon(img, "icon_finance")


def gen_exam_paper() -> None:
	img = new_sprite()
	d = ImageDraw.Draw(img)
	d.rounded_rectangle([20, 16, SPRITE_SIZE - 20, SPRITE_SIZE - 16],
	                    radius=8, fill=(252, 247, 232, 255), outline=(150, 130, 95, 255), width=2)
	# Header
	try:
		font = ImageFont.truetype(FONT_PATH, 26)
		d.text((36, 28), "Exam Paper", fill=(95, 70, 35), font=font)
	except IOError:
		pass
	# Ruled lines
	for i in range(8):
		y = 80 + i * 22
		d.rectangle([36, y, SPRITE_SIZE - 36, y + 4], fill=(190, 170, 130, 200))
	save_sprite(img, "exam_paper")


# --------------------------- chapter bg stubs ---------------------------

CHAPTER_BG_STUBS = [
	("chapter_02_engineering_the_dream", "college_hostel.jpg",
	 (245, 220, 175), (180, 130, 80), "Engineering the Dream"),
	("chapter_03_dragons_den", "cubicle_dawn.jpg",
	 (180, 168, 155), (90, 80, 75), "Dragon's Den"),
	("chapter_04_middle_class_trap", "balcony_night.jpg",
	 (60, 55, 70), (35, 32, 42), "The Middle-Class Trap"),
	("chapter_05_the_abyss", "rain_window_bg.jpg",
	 (50, 65, 80), (25, 35, 50), "The Abyss"),
	("chapter_06_taming_the_dragon", "morning_after.jpg",
	 (240, 200, 150), (200, 150, 90), "Morning After"),
]


def gen_chapter_bg(chapter: str, filename: str, top_rgb, bot_rgb, title: str) -> None:
	out_dir = os.path.join(CHAPTERS_DIR, chapter, "backgrounds")
	os.makedirs(out_dir, exist_ok=True)
	img = Image.new("RGB", (BG_W, BG_H), top_rgb)
	px = img.load()
	for y in range(BG_H):
		t = y / BG_H
		r = int(top_rgb[0] * (1 - t) + bot_rgb[0] * t)
		g = int(top_rgb[1] * (1 - t) + bot_rgb[1] * t)
		b = int(top_rgb[2] * (1 - t) + bot_rgb[2] * t)
		for x in range(BG_W):
			px[x, y] = (r, g, b)
	# Vignette
	vignette = Image.new("L", (BG_W, BG_H), 0)
	vd = ImageDraw.Draw(vignette)
	cx, cy = BG_W // 2, BG_H // 2
	vd.ellipse([cx - BG_W, cy - BG_H, cx + BG_W, cy + BG_H], fill=180)
	vignette = vignette.filter(ImageFilter.GaussianBlur(radius=120))
	black = Image.new("RGB", (BG_W, BG_H), (0, 0, 0))
	img = Image.composite(img, black, vignette)
	# Chapter title in lower third
	d = ImageDraw.Draw(img)
	try:
		font = ImageFont.truetype(FONT_PATH, 96)
		bbox = d.textbbox((0, 0), title, font=font)
		tw = bbox[2] - bbox[0]
		d.text(((BG_W - tw) // 2, int(BG_H * 0.68)), title,
		       fill=(245, 215, 165), font=font)
	except IOError:
		pass
	path = os.path.join(out_dir, filename)
	img.save(path, "JPEG", quality=82, optimize=True)
	print(f"bg: {path}")


def main() -> None:
	os.makedirs(SPRITES_DIR, exist_ok=True)
	os.makedirs(HUD_DIR, exist_ok=True)
	gen_hud_family()
	gen_hud_career()
	gen_hud_finance()
	gen_paratha()
	gen_tiffin(closed=False)
	gen_tiffin(closed=True)
	gen_match(lit=False)
	gen_match(lit=True)
	gen_lamp(lit=False)
	gen_lamp(lit=True)
	gen_bill("Electricity", (60, 110, 175))
	gen_bill("School Fee", (75, 145, 95))
	gen_bill("Medical", (170, 75, 75))
	gen_bill("Grocery", (200, 130, 60))
	gen_tray("Pay Now", warm=True)
	gen_tray("Defer", warm=False)
	gen_exam_paper()

	for ch, fn, top, bot, title in CHAPTER_BG_STUBS:
		gen_chapter_bg(ch, fn, top, bot, title)


if __name__ == "__main__":
	main()
