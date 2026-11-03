package com.creat.fonts.client.history;

import com.creat.fonts.CreatFonts;
import com.creat.fonts.font.FontData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;



public final class FontHistory {
	public static final int MAX_ENTRIES = 60;
	private static final int SCHEMA_VERSION = 1;
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private static final List<Entry> ENTRIES = new ArrayList<>();
	private static boolean loaded;

	private FontHistory() {
	}


	public static synchronized List<Entry> entries() {
		loadIfNeeded();
		return List.copyOf(ENTRIES);
	}



	public static synchronized void add(FontData data) {
		if (data == null || data.text().isBlank()) {
			return;
		}
		loadIfNeeded();

		Entry entry = Entry.from(data, System.currentTimeMillis());
		ENTRIES.removeIf(existing -> existing.sameLook(entry));
		ENTRIES.add(0, entry);
		while (ENTRIES.size() > MAX_ENTRIES) {
			ENTRIES.remove(ENTRIES.size() - 1);
		}
		save();
	}

	public static synchronized void remove(Entry entry) {
		if (entry == null) {
			return;
		}
		loadIfNeeded();
		if (ENTRIES.remove(entry)) {
			save();
		}
	}

	public static synchronized void clear() {
		loadIfNeeded();
		if (ENTRIES.isEmpty()) {
			return;
		}
		ENTRIES.clear();
		save();
	}

	private static void loadIfNeeded() {
		if (loaded) {
			return;
		}
		loaded = true;

		Path file = historyFile();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Store store = GSON.fromJson(reader, Store.class);
			if (store == null || store.entries == null) {
				return;
			}
			for (Entry entry : store.entries) {
				if (entry != null && !entry.text().isBlank()) {
					ENTRIES.add(entry);
				}
			}
			while (ENTRIES.size() > MAX_ENTRIES) {
				ENTRIES.remove(ENTRIES.size() - 1);
			}
		} catch (IOException | RuntimeException exception) {
			CreatFonts.LOGGER.warn("Could not read font history from {}", file, exception);
			ENTRIES.clear();
		}
	}

	private static void save() {
		Path file = historyFile();
		try {
			Files.createDirectories(file.getParent());
			Store store = new Store(SCHEMA_VERSION, List.copyOf(ENTRIES));
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(store, writer);
			}
		} catch (IOException exception) {
			CreatFonts.LOGGER.warn("Could not save font history to {}", file, exception);
		}
	}

	private static Path historyFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("creat-fonts-history.json");
	}

	private static final class Store {
		private final int schema;
		private final List<Entry> entries;

		private Store(int schema, List<Entry> entries) {
			this.schema = schema;
			this.entries = entries;
		}
	}


	public record Entry(
			String text,
			int pixelHeight,
			float pixelScale,
			float thickness,
			float gap,
			boolean bold,
			boolean italic,
			boolean merge,
			boolean attached,
			String blockId,
			String fontName,
			List<Float> charScales,
			long createdAt
	) {
		public Entry {
			text = FontData.sanitize(text);
			pixelHeight = Math.max(FontData.MIN_PIXEL_HEIGHT, Math.min(FontData.MAX_PIXEL_HEIGHT, pixelHeight));
			pixelScale = clamp(pixelScale, FontData.MIN_PIXEL_SCALE, FontData.MAX_PIXEL_SCALE);
			thickness = clamp(thickness, FontData.MIN_THICKNESS, FontData.MAX_THICKNESS);
			gap = clamp(gap, FontData.MIN_GAP, FontData.MAX_GAP);
			blockId = FontData.sanitizeBlockId(blockId);
			fontName = FontData.sanitizeFontName(fontName);
			charScales = FontData.sanitizeCharScales(charScales);
			if (createdAt <= 0L) {
				createdAt = System.currentTimeMillis();
			}
		}

		public static Entry from(FontData data, long createdAt) {
			return new Entry(
					data.text(),
					data.pixelHeight(),
					data.pixelScale(),
					data.thickness(),
					data.gap(),
					data.bold(),
					data.italic(),
					data.merge(),
					data.attached(),
					data.blockId(),
					data.fontName(),
					data.charScales(),
					createdAt
			);
		}

		private boolean sameLook(Entry other) {
			return this.text.equals(other.text)
					&& this.pixelHeight == other.pixelHeight
					&& Float.compare(this.pixelScale, other.pixelScale) == 0
					&& Float.compare(this.thickness, other.thickness) == 0
					&& Float.compare(this.gap, other.gap) == 0
					&& this.bold == other.bold
					&& this.italic == other.italic
					&& this.merge == other.merge
					&& this.attached == other.attached
					&& this.blockId.equals(other.blockId)
					&& this.fontName.equals(other.fontName)
					&& this.charScales.equals(other.charScales);
		}


		public String summary() {
			String firstLine = this.text;
			int newline = firstLine.indexOf('\n');
			if (newline >= 0) {
				firstLine = firstLine.substring(0, newline);
			}
			firstLine = firstLine.strip();
			if (firstLine.length() > 34) {
				return firstLine.substring(0, 33) + "…";
			}
			if (firstLine.isEmpty()) {
				return this.text.strip();
			}
			return firstLine;
		}

		public int lineCount() {
			if (this.text.isEmpty()) {
				return 0;
			}
			int lines = 1;
			for (int i = 0; i < this.text.length(); i++) {
				if (this.text.charAt(i) == '\n') {
					lines++;
				}
			}
			return lines;
		}

		public int visibleCharacterCount() {
			String visible = this.text.replace("\n", "");
			return visible.codePointCount(0, visible.length());
		}

		private static float clamp(float value, float min, float max) {
			if (Float.isNaN(value)) {
				return min;
			}
			return Math.max(min, Math.min(max, value));
		}
	}
}
