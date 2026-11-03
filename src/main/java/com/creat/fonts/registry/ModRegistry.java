package com.creat.fonts.registry;

import com.creat.fonts.CreatFonts;
import com.creat.fonts.block.FontBlock;
import com.creat.fonts.block.FontBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;



public final class ModRegistry {
	public static final ResourceKey<Block> FONT_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, CreatFonts.id("font_block"));

	public static final FontBlock FONT_BLOCK = new FontBlock(BlockBehaviour.Properties.of()
			.setId(FONT_BLOCK_KEY)
			.strength(0.8F)
			.sound(SoundType.STONE)
			.noCollision()
			.noOcclusion()
			.isViewBlocking((state, level, pos) -> false)
			.isSuffocating((state, level, pos) -> false));

	public static final BlockEntityType<FontBlockEntity> FONT_BLOCK_ENTITY =
			FabricBlockEntityTypeBuilder.create(FontBlockEntity::new, FONT_BLOCK).build();

	private static boolean initialised;

	private ModRegistry() {
	}

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;

		Registry.register(BuiltInRegistries.BLOCK, FONT_BLOCK_KEY, FONT_BLOCK);
		Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, CreatFonts.id("font_block"), FONT_BLOCK_ENTITY);
	}
}
