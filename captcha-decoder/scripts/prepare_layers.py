#!/usr/bin/env python3
"""Prepare nearest-neighbor color-layer views for the local CAPTCHA family."""

from __future__ import annotations

import argparse
import tempfile
import sys
from pathlib import Path

sys.dont_write_bytecode = True

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont


BACKGROUND = (128, 128, 128)
PALETTE = [
    ("red", (255, 0, 0)),
    ("green", (0, 255, 0)),
    ("blue", (0, 0, 255)),
    ("yellow", (255, 255, 0)),
    ("magenta", (255, 0, 255)),
    ("white", (255, 255, 255)),
    ("black", (0, 0, 0)),
]


def open_2x2(mask: np.ndarray) -> np.ndarray:
    """Binary opening with a 2x2 square, used only as a glyph locator."""
    if mask.shape[0] < 2 or mask.shape[1] < 2:
        return np.zeros_like(mask)

    eroded = (
        mask[:-1, :-1]
        & mask[1:, :-1]
        & mask[:-1, 1:]
        & mask[1:, 1:]
    )
    opened = np.zeros_like(mask)
    opened[:-1, :-1] |= eroded
    opened[1:, :-1] |= eroded
    opened[:-1, 1:] |= eroded
    opened[1:, 1:] |= eroded
    return opened


def mask_image(mask: np.ndarray) -> Image.Image:
    pixels = np.where(mask, 0, 255).astype(np.uint8)
    return Image.fromarray(pixels, mode="L").convert("RGB")


def shift_mask(mask: np.ndarray, dx: int, dy: int) -> np.ndarray:
    """Shift without wraparound."""
    shifted = np.zeros_like(mask)
    source_x0 = max(0, -dx)
    source_x1 = mask.shape[1] - max(0, dx)
    source_y0 = max(0, -dy)
    source_y1 = mask.shape[0] - max(0, dy)
    target_x0 = max(0, dx)
    target_x1 = mask.shape[1] - max(0, -dx)
    target_y0 = max(0, dy)
    target_y1 = mask.shape[0] - max(0, -dy)
    shifted[target_y0:target_y1, target_x0:target_x1] = mask[
        source_y0:source_y1, source_x0:source_x1
    ]
    return shifted


def normal_step(x1: int, y1: int, x2: int, y2: int) -> tuple[int, int]:
    """Return a one-pixel grid step approximately normal to a line."""
    dx = x2 - x1
    dy = y2 - y1
    if abs(dx) >= 2 * abs(dy):
        return (0, 1)
    if abs(dy) >= 2 * abs(dx):
        return (1, 0)
    return (1, -1 if dx * dy >= 0 else 1)


def detect_long_one_pixel_lines(mask: np.ndarray) -> np.ndarray:
    """Locate long runs whose observed perpendicular thickness is mostly 1 px."""
    height, width = mask.shape
    binary = mask.astype(np.uint8) * 255
    minimum_length = max(24, int(width * 0.18))
    lines = cv2.HoughLinesP(
        binary,
        rho=1,
        theta=np.pi / 360,
        threshold=max(12, minimum_length // 2),
        minLineLength=minimum_length,
        maxLineGap=8,
    )
    detected = np.zeros_like(binary)
    if lines is not None:
        for line in np.asarray(lines).reshape(-1, 4):
            x1, y1, x2, y2 = (int(value) for value in line)
            candidate = np.zeros_like(binary)
            cv2.line(candidate, (x1, y1), (x2, y2), 255, 1, cv2.LINE_8)
            center = (candidate > 0) & mask
            center_count = int(center.sum())
            if center_count < minimum_length * 0.6:
                continue

            normal_x, normal_y = normal_step(x1, y1, x2, y2)
            adjacent = shift_mask(mask, normal_x, normal_y) | shift_mask(
                mask, -normal_x, -normal_y
            )
            thick_fraction = float((center & adjacent).sum()) / center_count
            if thick_fraction <= 0.45:
                detected[center] = 255

    # Keep only pixels that really exist in the original color mask.
    return (detected > 0) & mask


def make_sheet(
    entries: list[tuple[str, np.ndarray]],
    title: str,
    scale: int,
    columns: int = 2,
) -> Image.Image:
    font = ImageFont.load_default()
    source_height, source_width = entries[0][1].shape
    panel_width = source_width * scale
    panel_height = source_height * scale
    label_height = 22
    title_height = 30
    rows = (len(entries) + columns - 1) // columns

    sheet = Image.new(
        "RGB",
        (columns * panel_width, title_height + rows * (label_height + panel_height)),
        (214, 214, 214),
    )
    draw = ImageDraw.Draw(sheet)
    draw.text((8, 8), title, fill="black", font=font)

    for index, (label, mask) in enumerate(entries):
        column = index % columns
        row = index // columns
        x = column * panel_width
        y = title_height + row * (label_height + panel_height)
        panel_label = f"{label} | panel-local x=0..{source_width - 1}"
        draw.text((x + 6, y + 5), panel_label, fill="black", font=font)
        enlarged = mask_image(mask).resize(
            (panel_width, panel_height), Image.Resampling.NEAREST
        )
        sheet.paste(enlarged, (x, y + label_height))

    return sheet


def validate_palette(rgb: np.ndarray) -> list[tuple[int, int, int]]:
    colors = {
        tuple(int(channel) for channel in color)
        for color in np.unique(rgb.reshape(-1, 3), axis=0)
    }
    allowed = {BACKGROUND, *(color for _, color in PALETTE)}
    return sorted(colors - allowed)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Create enlarged original, top color masks, top locator masks, and rule color masks."
    )
    parser.add_argument("image", type=Path)
    parser.add_argument(
        "--output-dir",
        type=Path,
        help="Defaults to a temporary captcha-decoder directory.",
    )
    parser.add_argument("--scale", type=int, default=4)
    parser.add_argument("--top-height", type=int, default=50)
    args = parser.parse_args()

    if args.scale < 1:
        parser.error("--scale must be at least 1")
    if not args.image.is_file():
        parser.error(f"Image not found: {args.image}")

    image = Image.open(args.image).convert("RGB")
    rgb = np.asarray(image)
    if not 1 <= args.top_height < rgb.shape[0]:
        parser.error("--top-height must split the image into non-empty top and rule regions")

    unexpected = validate_palette(rgb)
    if unexpected:
        values = ", ".join(str(color) for color in unexpected[:12])
        parser.error(f"Unexpected RGB colors for bgonly image: {values}")

    output_dir = args.output_dir
    if output_dir is None:
        output_dir = (
            Path(tempfile.gettempdir())
            / "captcha-decoder"
            / args.image.stem
        )
    output_dir.mkdir(parents=True, exist_ok=True)

    top = rgb[: args.top_height]
    rules = rgb[args.top_height :]
    top_raw: list[tuple[str, np.ndarray]] = []
    top_locator: list[tuple[str, np.ndarray]] = []
    top_bands: list[tuple[str, np.ndarray]] = []
    top_residual: list[tuple[str, np.ndarray]] = []
    rule_raw: list[tuple[str, np.ndarray]] = []
    rule_bands: list[tuple[str, np.ndarray]] = []
    rule_residual: list[tuple[str, np.ndarray]] = []

    for name, color in PALETTE:
        color_array = np.asarray(color, dtype=np.uint8)
        top_mask = np.all(top == color_array, axis=2)
        rule_mask = np.all(rules == color_array, axis=2)
        top_band_mask = detect_long_one_pixel_lines(top_mask)
        rule_band_mask = detect_long_one_pixel_lines(rule_mask)
        label = f"{name} RGB{color}"
        top_raw.append((label, top_mask))
        top_locator.append((label, open_2x2(top_mask)))
        top_bands.append((label, top_band_mask))
        top_residual.append((label, top_mask & ~top_band_mask))
        rule_raw.append((label, rule_mask))
        rule_bands.append((label, rule_band_mask))
        rule_residual.append((label, rule_mask & ~rule_band_mask))

    stem = args.image.stem
    overview_path = output_dir / f"{stem}_overview.png"
    top_path = output_dir / f"{stem}_top-layers.png"
    locator_path = output_dir / f"{stem}_top-locator.png"
    top_bands_path = output_dir / f"{stem}_top-detected-bands.png"
    top_residual_path = output_dir / f"{stem}_top-residual.png"
    rules_path = output_dir / f"{stem}_rule-layers.png"
    rule_bands_path = output_dir / f"{stem}_rule-detected-bands.png"
    rule_residual_path = output_dir / f"{stem}_rule-residual.png"

    image.resize(
        (image.width * args.scale, image.height * args.scale),
        Image.Resampling.NEAREST,
    ).save(overview_path)
    # Top sheets intentionally use one column. Every color mask therefore shares
    # the same visible x origin, preventing a vision model from sorting glyphs by
    # the sheet column instead of their original panel-local x coordinate.
    make_sheet(
        top_raw,
        "TOP RAW MASKS - one aligned x axis; use for final stroke evidence",
        args.scale,
        columns=1,
    ).save(top_path)
    make_sheet(
        top_locator,
        "TOP 2x2 LOCATORS - one aligned x axis; locate clusters only",
        args.scale,
        columns=1,
    ).save(locator_path)
    make_sheet(
        top_bands,
        "TOP LONG 1PX LINES - one aligned x axis; remove before recognition",
        args.scale,
        columns=1,
    ).save(top_bands_path)
    make_sheet(
        top_residual,
        "TOP FIRST VIEW - one aligned x axis; 1px lines removed",
        args.scale,
        columns=1,
    ).save(top_residual_path)
    make_sheet(
        rule_raw,
        "RULE RAW MASKS - read all five mappings here before the top",
        args.scale,
    ).save(rules_path)
    make_sheet(
        rule_bands,
        "RULE LONG 1PX LINES - remove before visual recognition",
        args.scale,
    ).save(rule_bands_path)
    make_sheet(
        rule_residual,
        "RULE FIRST VIEW - 1px lines removed; read key/arrow/digit here",
        args.scale,
    ).save(rule_residual_path)

    print(f"overview={overview_path.resolve()}")
    print(f"top_layers={top_path.resolve()}")
    print(f"top_locator={locator_path.resolve()}")
    print(f"top_detected_bands={top_bands_path.resolve()}")
    print(f"top_residual={top_residual_path.resolve()}")
    print(f"rule_layers={rules_path.resolve()}")
    print(f"rule_detected_bands={rule_bands_path.resolve()}")
    print(f"rule_residual={rule_residual_path.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
