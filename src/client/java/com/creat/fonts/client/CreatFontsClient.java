package com.creat.fonts.client;

import com.creat.fonts.CreatFonts;
import com.creat.fonts.block.FontBlock;
import com.creat.fonts.block.FontBlockEntity;
import com.creat.fonts.client.gui.FontEditorScreen;
import com.creat.fonts.client.gui.FontPlacementController;
import com.creat.fonts.client.render.FontBlockRenderer;
import com.creat.fonts.client.render.FontPreviewRenderer;
import com.creat.fonts.registry.ModRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

public class CreatFontsClient implements ClientModInitializer {
	private static KeyMapping openEditorKey;

	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(ModRegistry.FONT_BLOCK_ENTITY, context -> new FontBlockRenderer());
		FontPreviewRenderer.register();

		openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.creat-fonts.open_editor",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_K,
				KeyMapping.Category.register(CreatFonts.id("main"))
		));
		ClientTickEvents.END_CLIENT_TICK.register(CreatFontsClient::onEndClientTick);

		UseBlockCallback.EVENT.register(CreatFontsClient::onUseBlock);
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
				FontPlacementController.onUse(player, world, hand));
		UseItemCallback.EVENT.register((player, world, hand) -> FontPlacementController.onUse(player, world, hand));
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
				FontPlacementController.onUse(player, world, hand));
		ClientPreAttackCallback.EVENT.register(
				(client, player, clickCount) -> FontPlacementController.onAttack(client, player, clickCount)
		);
	}

	private static void onEndClientTick(Minecraft client) {
		FontPlacementController.tick(client);

		while (openEditorKey != null && openEditorKey.consumeClick()) {
			if (FontPlacementController.isActive()
					|| client.screen != null
					|| client.player == null
					|| client.level == null
					|| client.getOverlay() != null
					|| client.player.isSpectator()) {
				continue;
			}
			openEditor(client);
		}
	}

	private static void openEditor(Minecraft client) {
		Player player = client.player;
		Level level = client.level;
		if (player == null || level == null) {
			return;
		}



		if (client.hitResult instanceof BlockHitResult hit
				&& level.getBlockState(hit.getBlockPos()).getBlock() instanceof FontBlock
				&& level.getBlockEntity(hit.getBlockPos()) instanceof FontBlockEntity blockEntity) {
			BlockState state = level.getBlockState(hit.getBlockPos());
			client.setScreen(FontEditorScreen.forEditing(
					hit.getBlockPos(),
					state.getValue(FontBlock.FACING),
					blockEntity.getData()
			));
			return;
		}

		BlockPos target = FontPlacementController.findTarget(client);
		if (target == null) {
			player.displayClientMessage(Component.translatable("message.creat-fonts.no_space"), true);
			return;
		}
		client.setScreen(FontEditorScreen.forCreation(target, player.getDirection().getOpposite()));
	}

	private static InteractionResult onUseBlock(
			Player player,
			Level world,
			InteractionHand hand,
			BlockHitResult hitResult
	) {
		if (!world.isClientSide() || player.isSpectator()) {
			return InteractionResult.PASS;
		}


		if (FontPlacementController.isActive()) {
			return InteractionResult.PASS;
		}
		if (!player.getItemInHand(hand).isEmpty()) {
			return InteractionResult.PASS;
		}

		BlockPos pos = hitResult.getBlockPos();
		if (!(world.getBlockState(pos).getBlock() instanceof FontBlock)) {
			return InteractionResult.PASS;
		}
		if (world.getBlockEntity(pos) instanceof FontBlockEntity fontBlock) {
			BlockState state = world.getBlockState(pos);
			Minecraft.getInstance().setScreen(FontEditorScreen.forEditing(
					pos,
					state.getValue(FontBlock.FACING),
					fontBlock.getData()
			));
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}
