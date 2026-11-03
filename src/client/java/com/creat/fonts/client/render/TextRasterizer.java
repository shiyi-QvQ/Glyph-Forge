package com.creat.fonts.client.render;

import com.creat.fonts.CreatFonts;
import com.creat.fonts.font.FontData;
import com.creat.fonts.font.GlyphRect;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;



public final class TextRasterizer {


	public static final int AUTO_MERGE_THRESHOLD = 1500;

	private static final int PADDING = 6;
	private static final float COVERAGE_THRESHOLD = 0.32F;
	private static final int MAX_BITMAP_SIZE = 4096;
	private static final long SUPERSAMPLE_3_LIMIT = 400_000L;
	private static final long SUPERSAMPLE_2_LIMIT = 4_000_000L;

	private static final String[] ASCII_FONTS = {
			"Segoe UI", "Arial", "Tahoma", "Verdana", Font.SANS_SERIF
	};
	private static final String[] UNICODE_FONTS = {
			"Microsoft YaHei UI",
			"Microsoft YaHei",
			"SimHei",
			"Noto Sans CJK SC",
			"Source Han Sans SC",
			"PingFang SC",
			"Hiragino Sans GB",
			"WenQuanYi Micro Hei",
			"Arial Unicode MS",
			Font.SANS_SERIF
	};

	private static final Map<RasterKey, TextBitmap> CACHE = Collections.synchronizedMap(
			new LinkedHashMap<>(32, 0.75F, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<RasterKey, TextBitmap> eldest) {
					return size() > 64;
				}
			}
	);

	private TextRasterizer() {
	}

	public static TextBitmap rasterize(FontData data) {
		return rasterize(
				data.text(),
				data.pixelHeight(),
				data.bold(),
				data.italic(),
				data.charScales(),
				null,
				""
		);
	}

	public static TextBitmap rasterize(
			String rawText,
			int pixelHeight,
			boolean bold,
			boolean italic,
			@Nullable Font externalFont,
			String fontKey
	) {
		return rasterize(rawText, pixelHeight, bold, italic, List.of(), externalFont, fontKey);
	}

	public static TextBitmap rasterize(
			String rawText,
			int pixelHeight,
			boolean bold,
			boolean italic,
			List<Float> charScales,
			@Nullable Font externalFont,
			String fontKey
	) {
		String text = FontData.sanitize(rawText);
		List<Float> scales = FontData.sanitizeCharScales(charScales);
		String externalKey = externalFont == null ? "" : (fontKey == null ? "external" : fontKey);
		RasterKey key = new RasterKey(text, pixelHeight, bold, italic, scales, externalKey);
		TextBitmap cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}

		TextBitmap bitmap;
		try {
			bitmap = rasterizeUncached(key, externalFont);
		} catch (Exception exception) {
			CreatFonts.LOGGER.warn("Failed to rasterise font text {}", text, exception);
			bitmap = TextBitmap.EMPTY;
		}
		CACHE.put(key, bitmap);
		return bitmap;
	}

	private static TextBitmap rasterizeUncached(RasterKey key, @Nullable Font externalFont) {
		String text = key.text();
		if (text.isEmpty()) {
			return TextBitmap.EMPTY;
		}

		int style = (key.bold() ? Font.BOLD : 0) | (key.italic() ? Font.ITALIC : 0);
		Font baseFont = externalFont == null
				? chooseFont(text, style, key.pixelHeight())
				: deriveExternalFont(externalFont, text, style, key.pixelHeight());

		BufferedImage probeImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D probe = probeImage.createGraphics();
		applyHints(probe);
		FontRenderContext frc = probe.getFontRenderContext();
		FontMetrics baseMetrics = probe.getFontMetrics(baseFont);

		Map<Float, Font> fonts = new HashMap<>();
		Map<Float, FontMetrics> metrics = new HashMap<>();
		fonts.put((float) key.pixelHeight(), baseFont);
		metrics.put((float) key.pixelHeight(), baseMetrics);

		String[] lines = text.split("\n", -1);
		List<LineLayout> layouts = new ArrayList<>(lines.length);
		int visibleIndex = 0;
		float maxWidth = 0.0F;
		float totalHeight = 0.0F;

		for (String line : lines) {
			List<GlyphRun> runs = new ArrayList<>();
			StringBuilder pending = new StringBuilder();
			float pendingSize = -1.0F;

			for (int i = 0; i < line.length(); ) {
				int codePoint = line.codePointAt(i);
				i += Character.charCount(codePoint);

				float scale = visibleIndex < key.charScales().size()
						? key.charScales().get(visibleIndex)
						: FontData.DEFAULT_CHAR_SCALE;
				visibleIndex++;
				float size = Math.max(1.0F, key.pixelHeight() * FontData.normalizeCharScale(scale));

				if (pending.length() > 0 && Math.abs(size - pendingSize) > 0.001F) {
					runs.add(createRun(pending.toString(), pendingSize, baseFont, fonts, metrics, probe, frc));
					pending.setLength(0);
				}
				pendingSize = size;
				pending.appendCodePoint(codePoint);
			}
			if (pending.length() > 0) {
				runs.add(createRun(pending.toString(), pendingSize, baseFont, fonts, metrics, probe, frc));
			}

			float ascent = 0.0F;
			float descent = 0.0F;
			float leading = 0.0F;
			float advance = 0.0F;
			for (GlyphRun run : runs) {
				ascent = Math.max(ascent, run.ascent());
				descent = Math.max(descent, run.descent());
				leading = Math.max(leading, run.leading());
				advance += run.advance();
			}
			if (runs.isEmpty()) {
				ascent = baseMetrics.getAscent();
				descent = baseMetrics.getDescent();
				leading = baseMetrics.getLeading();
			}

			float lineHeight = Math.max(1.0F, ascent + descent + leading);
			maxWidth = Math.max(maxWidth, advance);
			totalHeight += lineHeight;
			layouts.add(new LineLayout(List.copyOf(runs), ascent, lineHeight));
		}
		probe.dispose();

		int imageWidth = (int) Math.min(
				MAX_BITMAP_SIZE,
				Math.max(1L, (long) Math.ceil(maxWidth) + PADDING * 2L)
		);
		int imageHeight = (int) Math.min(
				MAX_BITMAP_SIZE,
				Math.max(1L, (long) Math.ceil(totalHeight) + PADDING * 2L)
		);

		long targetPixels = (long) imageWidth * imageHeight;
		int supersample = targetPixels <= SUPERSAMPLE_3_LIMIT
				? 3
				: targetPixels <= SUPERSAMPLE_2_LIMIT ? 2 : 1;

		BufferedImage image = new BufferedImage(
				imageWidth * supersample,
				imageHeight * supersample,
				BufferedImage.TYPE_INT_ARGB
		);
		Graphics2D graphics = image.createGraphics();
		applyHints(graphics);
		graphics.scale(supersample, supersample);
		graphics.setColor(Color.WHITE);

		float lineTop = PADDING;
		for (LineLayout layout : layouts) {
			float baseline = lineTop + layout.ascent();
			float penX = PADDING;
			for (GlyphRun run : layout.runs()) {
				graphics.setFont(run.font());
				graphics.drawString(run.text(), penX, baseline);
				penX += run.advance();
			}
			lineTop += layout.height();
		}
		graphics.dispose();

		return downsampleAndMesh(image, imageWidth, imageHeight, supersample);
	}

	private static GlyphRun createRun(
			String text,
			float size,
			Font baseFont,
			Map<Float, Font> fonts,
			Map<Float, FontMetrics> metrics,
			Graphics2D probe,
			FontRenderContext frc
	) {
		Font font = fonts.computeIfAbsent(size, value -> deriveFont(baseFont, value));
		FontMetrics fontMetrics = metrics.computeIfAbsent(size, value -> probe.getFontMetrics(fonts.get(value)));
		float advance;
		try {
			advance = (float) new TextLayout(text, font, frc).getAdvance();
		} catch (RuntimeException exception) {
			advance = (float) font.getStringBounds(text, frc).getWidth();
		}
		return new GlyphRun(
				text,
				font,
				advance,
				fontMetrics.getAscent(),
				fontMetrics.getDescent(),
				fontMetrics.getLeading()
		);
	}

	private static Font deriveFont(Font baseFont, float size) {
		try {
			return baseFont.deriveFont(size);
		} catch (RuntimeException exception) {
			return baseFont;
		}
	}

	private static void applyHints(Graphics2D graphics) {
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}

	private static TextBitmap downsampleAndMesh(BufferedImage image, int width, int height, int supersample) {
		boolean[][] grid = new boolean[height][width];
		int minX = width;
		int minY = height;
		int maxX = -1;
		int maxY = -1;
		int[] sums = new int[width];
		int[] rowPixels = new int[image.getWidth()];
		int threshold = Math.max(
				1,
				Math.round(COVERAGE_THRESHOLD * 255.0F * supersample * supersample)
		);

		for (int y = 0; y < height; y++) {
			java.util.Arrays.fill(sums, 0);
			for (int sy = 0; sy < supersample; sy++) {
				int sourceY = y * supersample + sy;
				image.getRGB(0, sourceY, image.getWidth(), 1, rowPixels, 0, image.getWidth());
				for (int x = 0; x < width; x++) {
					int base = x * supersample;
					int sum = 0;
					for (int sx = 0; sx < supersample; sx++) {
						sum += (rowPixels[base + sx] >>> 24) & 0xFF;
					}
					sums[x] += sum;
				}
			}
			for (int x = 0; x < width; x++) {
				if (sums[x] >= threshold) {
					grid[y][x] = true;
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}

		if (maxX < minX || maxY < minY) {
			return TextBitmap.EMPTY;
		}

		int croppedWidth = maxX - minX + 1;
		int croppedHeight = maxY - minY + 1;
		boolean[] pixels = new boolean[croppedWidth * croppedHeight];
		for (int y = 0; y < croppedHeight; y++) {
			for (int x = 0; x < croppedWidth; x++) {
				pixels[y * croppedWidth + x] = grid[y + minY][x + minX];
			}
		}

		return new TextBitmap(
				croppedWidth,
				croppedHeight,
				pixels,
				greedyRectangles(croppedWidth, croppedHeight, pixels)
		);
	}

	static List<GlyphRect> greedyRectangles(int width, int height, boolean[] pixels) {
		boolean[] used = new boolean[pixels.length];
		List<GlyphRect> rectangles = new ArrayList<>();

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int index = y * width + x;
				if (!pixels[index] || used[index]) {
					continue;
				}

				int runWidth = 0;
				while (x + runWidth < width) {
					int next = y * width + x + runWidth;
					if (!pixels[next] || used[next]) {
						break;
					}
					runWidth++;
				}

				int runHeight = 1;
				outer:
				while (y + runHeight < height) {
					for (int i = 0; i < runWidth; i++) {
						int next = (y + runHeight) * width + x + i;
						if (!pixels[next] || used[next]) {
							break outer;
						}
					}
					runHeight++;
				}

				for (int dy = 0; dy < runHeight; dy++) {
					for (int dx = 0; dx < runWidth; dx++) {
						used[(y + dy) * width + x + dx] = true;
					}
				}
				rectangles.add(new GlyphRect(x, y, runWidth, runHeight));
			}
		}

		return rectangles;
	}

	private static Font chooseFont(String text, int style, int size) {
		String displayableText = text.replace("\n", "");
		boolean ascii = displayableText.codePoints().allMatch(codePoint -> codePoint < 128);
		String[] candidates = ascii ? ASCII_FONTS : UNICODE_FONTS;

		for (String candidate : candidates) {
			Font font = new Font(candidate, style, size);
			if (font.canDisplayUpTo(displayableText) == -1) {
				return font;
			}
		}

		return new Font(Font.SANS_SERIF, style, size);
	}

	private static Font deriveExternalFont(Font externalFont, String text, int style, int size) {
		try {
			return externalFont.deriveFont(style, (float) size);
		} catch (RuntimeException exception) {
			CreatFonts.LOGGER.warn("Could not derive external font, falling back to a system font", exception);
			return chooseFont(text, style, size);
		}
	}

	private record GlyphRun(
			String text,
			Font font,
			float advance,
			float ascent,
			float descent,
			float leading
	) {
	}

	private record LineLayout(List<GlyphRun> runs, float ascent, float height) {
	}

	private record RasterKey(
			String text,
			int pixelHeight,
			boolean bold,
			boolean italic,
			List<Float> charScales,
			String fontKey
	) {
		private RasterKey {
			charScales = List.copyOf(charScales);
		}
	}
}
