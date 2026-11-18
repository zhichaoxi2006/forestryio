"""Generate the 16x16 Forestry Filter texture and the 128x128 mod logo.

Run from anywhere with:
    python tools/make_assets.py

Requires Pillow (the DSH bundled Python already has it).
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]


def hexagon(draw: ImageDraw.ImageDraw, cx: int, cy: int, radius: int, fill, outline) -> None:
    """Draws a pixel-friendly flat-top honeycomb cell."""
    points = [
        (cx + radius, cy),
        (cx + radius // 2, cy - radius),
        (cx - radius // 2, cy - radius),
        (cx - radius, cy),
        (cx - radius // 2, cy + radius),
        (cx + radius // 2, cy + radius),
    ]
    draw.polygon(points, fill=fill, outline=outline)


def make_filter_texture() -> Image.Image:
    """A small grey filter housing with a golden honeycomb on its screen."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    outline = (38, 32, 26, 255)
    body = (196, 201, 206, 255)
    highlight = (233, 238, 242, 255)
    screen = (62, 55, 48, 255)
    screen_shadow = (44, 40, 36, 255)
    gold = (244, 182, 44, 255)
    gold_dark = (150, 98, 32, 255)
    gold_light = (255, 214, 120, 255)

    # Housing.
    draw.rounded_rectangle((1, 1, 14, 14), radius=2, fill=outline)
    draw.rounded_rectangle((2, 2, 13, 13), radius=2, fill=body)
    draw.line((3, 2, 12, 2), fill=highlight)
    draw.line((2, 3, 2, 12), fill=highlight)
    # Screen.
    draw.rounded_rectangle((3, 3, 12, 12), radius=1, fill=screen)
    draw.line((4, 12, 11, 12), fill=screen_shadow)
    # Honeycomb cell.
    hexagon(draw, 8, 8, 3, fill=gold, outline=gold_dark)
    draw.point((7, 6), fill=gold_light)
    draw.point((9, 10), fill=gold_light)
    # Two little antennae so it reads as a bee item at a glance.
    draw.point((6, 1), fill=gold_dark)
    draw.point((10, 1), fill=gold_dark)

    return img


def main() -> None:
    assets = ROOT / "src/main/resources/assets/forestryio"
    item_textures = assets / "textures/item"
    item_textures.mkdir(parents=True, exist_ok=True)

    filter = make_filter_texture()
    filter_path = item_textures / "forestry_filter.png"
    filter.save(filter_path)

    # 128x128 mod logo: dark rounded panel + the filter sprite scaled up with nearest neighbour.
    logo = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(logo)
    draw.rounded_rectangle((2, 2, 125, 125), radius=24, fill=(30, 23, 18, 255),
                           outline=(244, 182, 44, 255), width=3)
    draw.rounded_rectangle((8, 8, 119, 119), radius=18, outline=(120, 78, 32, 255), width=1)
    logo.alpha_composite(filter.resize((96, 96), Image.Resampling.NEAREST), (16, 16))
    logo.save(ROOT / "src/main/resources/logo.png")

    print(f"wrote {filter_path}")
    print(f"wrote {ROOT / 'src/main/resources/logo.png'}")


if __name__ == "__main__":
    main()
