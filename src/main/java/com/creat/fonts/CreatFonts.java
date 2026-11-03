package com.creat.fonts;

import com.creat.fonts.network.ModNetworking;
import com.creat.fonts.registry.ModRegistry;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreatFonts implements ModInitializer {
	public static final String MOD_ID = "creat-fonts";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModRegistry.init();
		ModNetworking.init();
		LOGGER.info("Glyph Forge loaded - write with scaled mini blocks.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
