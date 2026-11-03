package com.creat.fonts.client.render;

import com.creat.fonts.font.GlyphRect;
import java.util.ArrayList;
import java.util.List;



public final class TextBitmap {
	public static final TextBitmap EMPTY = new TextBitmap(0, 0, new boolean[0], List.of());

	private final int width;
	private final int height;
	private final boolean[] pixels;
	private final List<GlyphRect> rectangles;
	private final int filledPixels;
	private List<GlyphRect> pixelRectangles;

	public TextBitmap(int width, int height, boolean[] pixels, List<GlyphRect> rectangles) {
		if (width < 0 || height < 0) {
			throw new IllegalArgumentException("Negative bitmap size");
		}
		if (pixels.length != width * height) {
			throw new IllegalArgumentException("Pixel array does not match bitmap size");
		}
		this.width = width;
		this.height = height;
		this.pixels = pixels;
		this.rectangles = List.copyOf(rectangles);
		int filled = 0;
		for (boolean pixel : pixels) {
			if (pixel) {
				filled++;
			}
		}
		this.filledPixels = filled;
	}



	public static TextBitmap fromRectangles(int width, int height, List<GlyphRect> rectangles) {
		if (width <= 0 || height <= 0 || width > 4096 || height > 4096 || rectangles == null || rectangles.isEmpty()) {
			return EMPTY;
		}

		boolean[] pixels = new boolean[width * height];
		for (GlyphRect rect : rectangles) {
			if (rect == null) {
				continue;
			}
			int x0 = Math.max(0, rect.x());
			int y0 = Math.max(0, rect.y());
			int x1 = Math.min(width, rect.x() + rect.width());
			int y1 = Math.min(height, rect.y() + rect.height());
			for (int y = y0; y < y1; y++) {
				int row = y * width;
				for (int x = x0; x < x1; x++) {
					pixels[row + x] = true;
				}
			}
		}

		int filled = 0;
		for (boolean pixel : pixels) {
			if (pixel) {
				filled++;
			}
		}
		if (filled == 0) {
			return EMPTY;
		}

		return new TextBitmap(width, height, pixels, TextRasterizer.greedyRectangles(width, height, pixels));
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public boolean isEmpty() {
		return width == 0 || height == 0 || filledPixels == 0;
	}

	public boolean pixel(int x, int y) {
		if (x < 0 || y < 0 || x >= width || y >= height) {
			return false;
		}
		return pixels[y * width + x];
	}

	public int filledPixels() {
		return filledPixels;
	}

	public List<GlyphRect> rectangles() {
		return rectangles;
	}



	public List<GlyphRect> pixelRectangles() {
		List<GlyphRect> cached = this.pixelRectangles;
		if (cached != null) {
			return cached;
		}

		List<GlyphRect> built = new ArrayList<>(this.filledPixels);
		for (int y = 0; y < this.height; y++) {
			int row = y * this.width;
			for (int x = 0; x < this.width; x++) {
				if (this.pixels[row + x]) {
					built.add(new GlyphRect(x, y, 1, 1));
				}
			}
		}
		cached = List.copyOf(built);
		this.pixelRectangles = cached;
		return cached;
	}
}
