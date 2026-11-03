package com.creat.fonts.network;

import com.creat.fonts.block.FontBlock;
import com.creat.fonts.block.FontBlockEntity;
import com.creat.fonts.font.FontData;
import com.creat.fonts.registry.ModRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;



public final class ModNetworking {
	private static final double MAX_EDIT_DISTANCE = 64.0;

	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.playC2S().register(ApplyFontTextPayload.TYPE, ApplyFontTextPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ApplyFontTextPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			ServerLevel level = player.level();
			BlockPos pos = payload.pos();

			if (player.isSpectator()) {
				return;
			}
			if (!level.isLoaded(pos)) {
				fail(player, "message.creat-fonts.failed.space");
				return;
			}
			if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_EDIT_DISTANCE * MAX_EDIT_DISTANCE) {
				fail(player, "message.creat-fonts.failed.space");
				return;
			}
			if (!level.mayInteract(player, pos)) {
				fail(player, "message.creat-fonts.failed.permission");
				return;
			}

			FontData data = payload.data();
			Block fillBlock = resolveBlock(data.blockId());
			if (fillBlock == null || fillBlock == Blocks.AIR || fillBlock instanceof FontBlock) {
				fail(player, "message.creat-fonts.failed.block_invalid");
				return;
			}
			Item fillItem = fillBlock.asItem();
			if (fillItem == Items.AIR) {
				fail(player, "message.creat-fonts.failed.block_invalid");
				return;
			}

			if (payload.create()) {
				if (!level.getBlockState(pos).canBeReplaced()) {
					fail(player, "message.creat-fonts.failed.space");
					return;
				}
				if (!hasItem(player, fillItem)) {
					fail(player, "message.creat-fonts.failed.block_missing");
					return;
				}

				Direction facing = payload.facing();
				BlockState state = ModRegistry.FONT_BLOCK.defaultBlockState()
						.setValue(FontBlock.FACING, facing);
				if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
					fail(player, "message.creat-fonts.failed.space");
					return;
				}
				if (level.getBlockEntity(pos) instanceof FontBlockEntity fontBlock) {
					fontBlock.setData(data);
				}
				player.displayClientMessage(Component.translatable("message.creat-fonts.placed"), true);
				return;
			}

			if (level.getBlockEntity(pos) instanceof FontBlockEntity fontBlock) {
				fontBlock.setData(data);
				player.displayClientMessage(Component.translatable("message.creat-fonts.updated"), true);
			} else {
				fail(player, "message.creat-fonts.failed.not_font_block");
			}
		});
	}

	private static Block resolveBlock(String blockId) {
		Identifier id = Identifier.tryParse(blockId);
		return id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
	}

	private static boolean hasItem(ServerPlayer player, Item item) {
		return player.getInventory().contains(stack -> stack.getItem() == item)
				|| player.getOffhandItem().getItem() == item;
	}

	private static void fail(ServerPlayer player, String translationKey) {
		player.displayClientMessage(Component.translatable(translationKey), false);
	}
}
