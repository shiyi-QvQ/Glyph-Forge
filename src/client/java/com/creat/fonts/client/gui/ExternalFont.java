package com.creat.fonts.client.gui;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;



record ExternalFont(Font font, String displayName, String cacheKey) {
	static ExternalFont load(Path path) throws IOException, FontFormatException {
		File file = path.toFile();
		Font[] fonts = Font.createFonts(file);
		if (fonts.length == 0) {
			throw new FontFormatException("No fonts found in " + path);
		}



		Font font = fonts[0];
		for (Font candidate : fonts) {
			String name = candidate.getFontName(Locale.ROOT).toLowerCase(Locale.ROOT);
			if (name.contains("regular") || name.contains("normal")) {
				font = candidate;
				break;
			}
		}

		String family = font.getFamily(Locale.ROOT);
		if (family == null || family.isBlank()) {
			family = font.getFontName(Locale.ROOT);
		}
		if (family == null || family.isBlank()) {
			family = path.getFileName().toString();
		}

		String key = path.toAbsolutePath()
				+ "|" + Files.getLastModifiedTime(path).toMillis()
				+ "|" + Files.size(path);
		return new ExternalFont(font, family, key);
	}
}
