package com.creat.fonts.font;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;



public record FontData(
		String text,
		int pixelHeight,
		float pixelScale,
		float thickness,
		float gap,
		boolean bold,
		boolean italic,
		List<Float> charScales,
		boolean merge,
		boolean attached,
		String blockId,
		String fontName,
		int bitmapWidth,
		int bitmapHeight,
		List<GlyphRect> rectangles
) {


	public static final int MAX_TEXT_LENGTH = 32767;
	public static final int MAX_RECTANGLES = 20000;
	public static final int MAX_BITMAP_SIZE = 4096;
	public static final int MAX_FONT_NAME_LENGTH = 80;

	public static final int MIN_PIXEL_HEIGHT = 12;
	public static final int MAX_PIXEL_HEIGHT = 128;
	public static final float MIN_PIXEL_SCALE = 0.004F;
	public static final float MAX_PIXEL_SCALE = 0.25F;
	public static final float MIN_THICKNESS = 0.02F;
	public static final float MAX_THICKNESS = 0.9F;
	public static final float MIN_GAP = 0.0F;
	public static final float MAX_GAP = 0.6F;

	public static final float MIN_CHAR_SCALE = 0.25F;
	public static final float MAX_CHAR_SCALE = 4.0F;
	public static final float DEFAULT_CHAR_SCALE = 1.0F;

	public static final String DEFAULT_BLOCK_ID = "minecraft:white_concrete";

	public static final FontData DEFAULT = new FontData(
			"我",
			24,
			0.0625F,
			0.25F,
			0.06F,
			false,
			false,
			List.of(),
			false,
			false,
			DEFAULT_BLOCK_ID,
			"",
			0,
			0,
			List.of()
	);

	public FontData {
		text = sanitize(text);
		pixelHeight = clamp(Math.round(pixelHeight), MIN_PIXEL_HEIGHT, MAX_PIXEL_HEIGHT);
		pixelScale = clamp(pixelScale, MIN_PIXEL_SCALE, MAX_PIXEL_SCALE);
		thickness = clamp(thickness, MIN_THICKNESS, MAX_THICKNESS);
		gap = clamp(gap, MIN_GAP, MAX_GAP);
		charScales = sanitizeCharScales(charScales);
		blockId = sanitizeBlockId(blockId);
		fontName = sanitizeFontName(fontName);
		bitmapWidth = clamp(bitmapWidth, 0, MAX_BITMAP_SIZE);
		bitmapHeight = clamp(bitmapHeight, 0, MAX_BITMAP_SIZE);
		rectangles = sanitizeRectangles(rectangles, bitmapWidth, bitmapHeight);
	}

	public int lineCount() {
		if (text.isEmpty()) {
			return 0;
		}
		int lines = 1;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				lines++;
			}
		}
		return lines;
	}

	public int visibleCharacterCount() {
		String visible = text.replace("\n", "");
		return visible.codePointCount(0, visible.length());
	}



	public static String sanitize(String raw) {
		if (raw == null) {
			return "";
		}

		String normalised = raw.replace("\r\n", "\n").replace('\r', '\n');
		StringBuilder cleaned = new StringBuilder(normalised.length());
		for (int i = 0; i < normalised.length(); ) {
			int codePoint = normalised.codePointAt(i);
			i += Character.charCount(codePoint);
			if (codePoint == '\n') {
				cleaned.append('\n');
			} else if (codePoint == '\t') {
				cleaned.append("    ");
			} else if (!Character.isISOControl(codePoint)) {
				cleaned.appendCodePoint(codePoint);
			}
		}

		if (cleaned.length() > MAX_TEXT_LENGTH) {
			int end = MAX_TEXT_LENGTH;
			if (Character.isHighSurrogate(cleaned.charAt(end - 1))) {
				end--;
			}
			cleaned.setLength(end);
		}
		return cleaned.toString();
	}

	public static String sanitizeBlockId(String raw) {
		if (raw == null) {
			return DEFAULT_BLOCK_ID;
		}
		Identifier identifier = Identifier.tryParse(raw);
		if (identifier == null || identifier.getPath().isEmpty()) {
			return DEFAULT_BLOCK_ID;
		}
		return identifier.toString();
	}

	public static String sanitizeFontName(String raw) {
		if (raw == null) {
			return "";
		}
		StringBuilder cleaned = new StringBuilder(raw.length());
		int codePoints = 0;
		for (int i = 0; i < raw.length() && codePoints < MAX_FONT_NAME_LENGTH; ) {
			int codePoint = raw.codePointAt(i);
			i += Character.charCount(codePoint);
			if (!Character.isISOControl(codePoint)) {
				cleaned.appendCodePoint(codePoint);
				codePoints++;
			}
		}
		return cleaned.toString().trim();
	}


	public float charScaleAt(int visibleIndex) {
		if (visibleIndex < 0 || visibleIndex >= this.charScales.size()) {
			return DEFAULT_CHAR_SCALE;
		}
		return normalizeCharScale(this.charScales.get(visibleIndex));
	}

	public static float normalizeCharScale(float value) {
		if (Float.isNaN(value) || Float.isInfinite(value)) {
			return DEFAULT_CHAR_SCALE;
		}
		return clamp(value, MIN_CHAR_SCALE, MAX_CHAR_SCALE);
	}



	public static List<Float> sanitizeCharScales(List<Float> input) {
		if (input == null || input.isEmpty()) {
			return List.of();
		}

		int lastOverride = -1;
		for (int i = 0; i < input.size(); i++) {
			Float value = input.get(i);
			float scale = value == null ? DEFAULT_CHAR_SCALE : normalizeCharScale(value);
			if (Math.abs(scale - DEFAULT_CHAR_SCALE) > 0.0001F) {
				lastOverride = i;
			}
		}
		if (lastOverride < 0) {
			return List.of();
		}

		List<Float> cleaned = new ArrayList<>(lastOverride + 1);
		for (int i = 0; i <= lastOverride; i++) {
			Float value = input.get(i);
			cleaned.add(value == null ? DEFAULT_CHAR_SCALE : normalizeCharScale(value));
		}
		return List.copyOf(cleaned);
	}

	public static int[] encodeCharScales(List<Float> scales) {
		if (scales == null || scales.isEmpty()) {
			return new int[0];
		}
		int[] encoded = new int[scales.size()];
		for (int i = 0; i < scales.size(); i++) {
			Float value = scales.get(i);
			encoded[i] = Math.round((value == null ? DEFAULT_CHAR_SCALE : normalizeCharScale(value)) * 1000.0F);
		}
		return encoded;
	}

	public static List<Float> decodeCharScales(int[] encoded) {
		if (encoded == null || encoded.length == 0) {
			return List.of();
		}
		List<Float> scales = new ArrayList<>(Math.min(encoded.length, MAX_TEXT_LENGTH));
		for (int i = 0; i < encoded.length && i < MAX_TEXT_LENGTH; i++) {
			scales.add(normalizeCharScale(encoded[i] / 1000.0F));
		}
		return sanitizeCharScales(scales);
	}

	public static int[] encodeRectangles(List<GlyphRect> rectangles) {
		if (rectangles == null || rectangles.isEmpty()) {
			return new int[0];
		}
		int count = Math.min(rectangles.size(), MAX_RECTANGLES);
		int[] encoded = new int[count * 4];
		for (int i = 0; i < count; i++) {
			GlyphRect rect = rectangles.get(i);
			int base = i * 4;
			encoded[base] = rect.x();
			encoded[base + 1] = rect.y();
			encoded[base + 2] = rect.width();
			encoded[base + 3] = rect.height();
		}
		return encoded;
	}

	public static List<GlyphRect> decodeRectangles(int[] encoded) {
		if (encoded == null || encoded.length < 4) {
			return List.of();
		}
		int count = Math.min(encoded.length / 4, MAX_RECTANGLES);
		List<GlyphRect> rectangles = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			int base = i * 4;
			rectangles.add(new GlyphRect(encoded[base], encoded[base + 1], encoded[base + 2], encoded[base + 3]));
		}
		return List.copyOf(rectangles);
	}

	private static List<GlyphRect> sanitizeRectangles(List<GlyphRect> input, int width, int height) {
		if (input == null || input.isEmpty() || width <= 0 || height <= 0) {
			return List.of();
		}

		List<GlyphRect> cleaned = new ArrayList<>(Math.min(input.size(), MAX_RECTANGLES));
		for (GlyphRect rect : input) {
			if (rect == null) {
				continue;
			}
			int x = clamp(rect.x(), 0, width - 1);
			int y = clamp(rect.y(), 0, height - 1);
			int rectWidth = Math.min(width - x, rect.width());
			int rectHeight = Math.min(height - y, rect.height());
			if (rectWidth <= 0 || rectHeight <= 0) {
				continue;
			}
			cleaned.add(new GlyphRect(x, y, rectWidth, rectHeight));
			if (cleaned.size() >= MAX_RECTANGLES) {
				break;
			}
		}
		return List.copyOf(cleaned);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float clamp(float value, float min, float max) {
		if (Float.isNaN(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}
}
