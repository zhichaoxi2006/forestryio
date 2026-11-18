"""Compose the ForestryIO logo out of the two real item renders.

  * laser node  : LaserIO's block model, rendered like the inventory icon
  * forest queen: Forestry's layered bee item texture (+ species tint)

No repainting: only the shipped assets, isometric item projection and composition.
"""

from __future__ import annotations

import json
import math
import os
import zipfile

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
REFS = os.path.join(HERE, "refs")
LASER = r"D:\dev\ForestryIO\libs\laserio-1.9.11.jar"
FORESTRY = r"D:\dev\ForestryIO\libs\forestry-1.21.1-3.0.0-alpha9.jar"

# Forest species colours (Queen body / stripes / outline)
# data/forestry/bee_species/forest.json -> outline; body/stripes fall back to the
# SpritePalette defaults (16768022 / 0). Tints: 0 = outline, 1 = body, 2 = stripes.
BODY = (255, 220, 22)      # #FFDC16
STRIPE = (0, 0, 0)         # #000000
OUTLINE = (25, 208, 236)   # #19D0EC
CORE_TINT = (255, 52, 40)   # red laser core
CORE_FLOOR = 0.5           # brightness floor for the emissive core
CORE_GAIN = 1.05           # how much texture shading still modulates it


# --------------------------------------------------------------------------- assets
def read_jar(jar, name):
    with zipfile.ZipFile(jar) as z:
        return z.read(name)


def node_model():
    raw = read_jar(LASER, "assets/laserio/models/block/laser_node.json")
    tex = Image.open(os.path.join(REFS, "assets_laserio_textures_block_laser_node.png")).convert("RGBA")
    tex = tex.crop((0, 0, 32, 32))  # animation frame 0
    return json.loads(raw), tex


# --------------------------------------------------------------------------- 3d helpers
def mat_mul(a, b):
    return a @ b


def rot_x(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], dtype=np.float64)


def rot_y(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=np.float64)


FACE_DEFS = {
    # face: (normal, corners counter-clockwise as seen from outside)
    "north": ((0, 0, -1), lambda x1, y1, z1, x2, y2, z2: [(x1, y2, z1), (x2, y2, z1), (x2, y1, z1), (x1, y1, z1)]),
    "south": ((0, 0, 1), lambda x1, y1, z1, x2, y2, z2: [(x2, y2, z2), (x1, y2, z2), (x1, y1, z2), (x2, y1, z2)]),
    "east": ((1, 0, 0), lambda x1, y1, z1, x2, y2, z2: [(x2, y2, z1), (x2, y2, z2), (x2, y1, z2), (x2, y1, z1)]),
    "west": ((-1, 0, 0), lambda x1, y1, z1, x2, y2, z2: [(x1, y2, z2), (x1, y2, z1), (x1, y1, z1), (x1, y1, z2)]),
    "up": ((0, 1, 0), lambda x1, y1, z1, x2, y2, z2: [(x1, y2, z2), (x2, y2, z2), (x2, y2, z1), (x1, y2, z1)]),
    "down": ((0, -1, 0), lambda x1, y1, z1, x2, y2, z2: [(x1, y1, z1), (x2, y1, z1), (x2, y1, z2), (x1, y1, z2)]),
}


def default_uv(face, x1, y1, z1, x2, y2, z2):
    if face in ("north", "south"):
        return [x1, 16 - y2, x2, 16 - y1]
    if face in ("east", "west"):
        return [z1, 16 - y2, z2, 16 - y1]
    return [x1, z1, x2, z2]


def rotate_uv(corner_s, corner_t, rotation):
    """Map a corner's (s, t) in face space to texture space, honouring uv rotation."""
    if rotation == 90:
        return corner_t, 1 - corner_s
    if rotation == 180:
        return 1 - corner_s, 1 - corner_t
    if rotation == 270:
        return 1 - corner_t, corner_s
    return corner_s, corner_t


def box_faces(el):
    x1, y1, z1 = el["from"]
    x2, y2, z2 = el["to"]
    b = dict(x1=x1, x2=x2, y1=y1, y2=y2, z1=z1, z2=z2)
    out = []
    for face, (normal, picker) in FACE_DEFS.items():
        f = el.get("faces", {}).get(face)
        if not f:
            continue
        if "uv" in f:
            u1, v1, u2, v2 = f["uv"]
        else:
            u1, v1, u2, v2 = default_uv(face, x1, y1, z1, x2, y2, z2)
        rot = f.get("rotation", 0)
        corners = picker(x1, y1, z1, x2, y2, z2)
        face_s = [(0, 0), (1, 0), (1, 1), (0, 1)]
        uvs = []
        for (s, t) in face_s:
            ns, nt = rotate_uv(s, t, rot)
            uvs.append((u1 + (u2 - u1) * ns, v1 + (v2 - v1) * nt))
        out.append({
            "face": face,
            "normal": np.array(normal, dtype=np.float64),
            "pts": np.array([[c[0], c[1], c[2]] for c in corners], dtype=np.float64),
            "uv": uvs,
            "tint": f.get("tintindex"),
            "rotation": rot,
        })
    return out


def project(points, rot, scale, center, size, margin=0.08):
    """points: (n,3) in model space 0..16. Returns screen points (y down)."""
    p = (points - 8.0) / 16.0
    R = rot_x(rot[0]) @ rot_y(rot[1]) @ rot_z(rot[2])
    v = (r @ p.T).T * scale
    return v


def rot_z(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], dtype=np.float64)


def hot_core(im, tint, shade=1.0):
    """Push a tinted face towards a saturated, self-lit laser red."""
    a = np.asarray(im).astype(np.float32)
    lum = a[..., :3].mean(axis=2) / 255.0
    form = 0.75 + 0.35 * shade  # keep a hint of the cube's form
    scale = (CORE_FLOOR + CORE_GAIN * lum)[..., None] * form
    a[..., :3] = np.clip(np.array(tint, dtype=np.float32) * scale, 0, 255)
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def render_model(model, texture, size=1200, rot=(30, 225, 0), scale=0.625, tint_core=None):
    draw_scale = 3
    canvas = size * draw_scale
    tex = texture
    faces = []
    for el in model["elements"]:
        for f in box_faces(el):
            faces.append(f)

    # view-space geometry
    R = rot_x(rot[0]) @ rot_y(rot[1]) @ rot_z(rot[2])
    for f in faces:
        p = (f["pts"] - 8.0) / 16.0
        f["view"] = (R @ p.T).T * scale
        f["nview"] = R @ f["normal"]
        f["depth"] = float(f["view"][:, 2].mean())

    # fit to canvas
    all_pts = np.concatenate([f["view"] for f in faces], axis=0)
    span_x = all_pts[:, 0].max() - all_pts[:, 0].min()
    span_y = all_pts[:, 1].max() - all_pts[:, 1].min()
    k = (1 - 2 * 0.08) * canvas / max(span_x, span_y)
    cx = (all_pts[:, 0].max() + all_pts[:, 0].min()) / 2
    cy = (all_pts[:, 1].max() + all_pts[:, 1].min()) / 2

    def to_screen(v):
        return np.stack([(v[:, 0] - cx) * k + canvas / 2, -(v[:, 1] - cy) * k + canvas / 2], axis=1)

    L1 = np.array([-0.2, 1.0, -0.7]); L1 /= np.linalg.norm(L1)
    L2 = np.array([0.2, 1.0, 0.7]); L2 /= np.linalg.norm(L2)

    img = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    faces.sort(key=lambda f: f["depth"])

    for f in faces:
        scr = to_screen(f["view"])
        n = f["nview"] / (np.linalg.norm(f["nview"]) or 1)
        shade = 0.42 + 0.62 * max(0.0, float(n @ L1)) + 0.62 * max(0.0, float(n @ L2))
        shade = min(1.0, shade)

        # affine uv(16) -> screen from three corners
        (u0, v0), (u1, v1), (u2, v2) = f["uv"][0], f["uv"][1], f["uv"][2]
        s0, s1, s2 = scr[0], scr[1], scr[2]
        A = np.array([[u1 - u0, u2 - u0], [v1 - v0, v2 - v0]], dtype=np.float64)
        B = np.array([[s1[0] - s0[0], s2[0] - s0[0]], [s1[1] - s0[1], s2[1] - s0[1]]], dtype=np.float64)
        M = B @ np.linalg.inv(A)  # screen = M @ uv + t
        t = s0 - M @ np.array([u0, v0])
        # PIL wants output->input: uv = Minv @ (screen - t)
        Minv = np.linalg.inv(M)
        tex_scale = tex.width / 16.0
        a, b = Minv[0, 0] * tex_scale, Minv[0, 1] * tex_scale
        d, e = Minv[1, 0] * tex_scale, Minv[1, 1] * tex_scale
        c = (Minv[0, 0] * -t[0] + Minv[0, 1] * -t[1]) * tex_scale
        g = (Minv[1, 0] * -t[0] + Minv[1, 1] * -t[1]) * tex_scale

        # transform only the face's bounding box: far cheaper than a full canvas
        bx0 = int(math.floor(min(p[0] for p in scr))) - 1
        by0 = int(math.floor(min(p[1] for p in scr))) - 1
        bx1 = int(math.ceil(max(p[0] for p in scr))) + 1
        by1 = int(math.ceil(max(p[1] for p in scr))) + 1
        bw, bh = max(1, bx1 - bx0), max(1, by1 - by0)
        coeffs = (a, b, c + a * bx0 + b * by0, d, e, g + d * bx0 + e * by0)
        warped = tex.transform((bw, bh), Image.AFFINE, coeffs, resample=Image.NEAREST)

        # tint a face the way Minecraft's ItemColor does: multiply, then light it
        arr = np.asarray(warped).astype(np.float32)
        if f["tint"] is not None and tint_core:
            arr[..., :3] *= np.array(tint_core, dtype=np.float32) / 255.0 * 1.25
        arr[..., :3] *= shade
        warped = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")

        mask = Image.new("L", (bw, bh), 0)
        ImageDraw.Draw(mask).polygon([(p[0] - bx0, p[1] - by0) for p in scr], fill=255)
        warped.putalpha(ImageChops.multiply(warped.getchannel("A"), mask))
        img.alpha_composite(warped, (bx0, by0))

    return img.resize((size, size), Image.LANCZOS)


# --------------------------------------------------------------------------- bee
def bee_sprite():
    def layer(name):
        p = os.path.join(REFS, f"assets_forestry_textures_item_bees_default_{name}.png")
        im = Image.open(p).convert("RGBA")
        return im.crop((0, 0, 16, 16))  # animation frame 0

    def tinted(name, colour):
        im = layer(name)
        arr = np.asarray(im).astype(np.float32)
        arr[..., :3] *= np.array(colour, dtype=np.float32) / 255.0
        return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")

    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for im in (tinted("queen.outline", OUTLINE), tinted("body1", BODY), tinted("stripes", STRIPE), layer("queen.body2")):
        out = Image.alpha_composite(out, im)
    return out


# --------------------------------------------------------------------------- compose
def drop_shadow(mask, blur, alpha):
    sh = Image.new('RGBA', mask.size, (0, 0, 0, 0))
    sh.paste((0, 0, 0, alpha), (0, 0), mask)
    return sh.filter(ImageFilter.GaussianBlur(blur))


def node_sprite(tint=CORE_TINT):
    model, tex = node_model()
    img = render_model(model, tex, size=1400, tint_core=tint)
    return img.crop(img.getbbox())


def place(canvas, sprite, width, center, resample, blur, offset, alpha):
    w = max(1, int(round(width)))
    h = max(1, int(round(w * sprite.height / sprite.width)))
    img = sprite.resize((w, h), resample)
    x, y = int(round(center[0] - w / 2)), int(round(center[1] - h / 2))
    canvas.alpha_composite(drop_shadow(img.getchannel('A'), blur, alpha), (x + 2, y + int(offset)))
    canvas.alpha_composite(img, (x, y))


def compose(size=512, *, tint=CORE_TINT, node_w=300, node_at=(246, 232), bee_w=168, bee_at=(420, 420),
            node=None, bee=None):
    ss = 2
    canvas = Image.new('RGBA', (size * ss, size * ss), (0, 0, 0, 0))
    k = size * ss / 512.0
    node = node_sprite(tint) if node is None else node
    bee = bee_sprite() if bee is None else bee
    place(canvas, node, node_w * k, (node_at[0] * k, node_at[1] * k), Image.LANCZOS, 7 * k, 8 * k, 110)
    place(canvas, bee, bee_w * k, (bee_at[0] * k, bee_at[1] * k), Image.NEAREST, 5 * k, 5 * k, 120)
    return canvas.resize((size, size), Image.LANCZOS)


if __name__ == '__main__':
    node = node_sprite()
    bee = bee_sprite()
    layout = dict(node_w=310, node_at=(250, 240), bee_w=182, bee_at=(414, 414))
    compose(1024, node=node, bee=bee, **layout).save(os.path.join(HERE, 'forestryio-logo.png'))
    compose(512, node=node, bee=bee, **layout).save(os.path.join(HERE, 'forestryio-logo-512.png'))
    compose(128, node=node, bee=bee, **layout).save(os.path.join(HERE, 'forestryio-logo-128.png'))
    print('ok')
