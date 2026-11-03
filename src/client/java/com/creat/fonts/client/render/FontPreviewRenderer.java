package com.creat.fonts.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;



public final class FontPreviewRenderer {
	private FontPreviewRenderer() {
	}

	public static void register() {
		WorldRenderEvents.BEFORE_ENTITIES.register(FontPreviewRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		FontPreviewState.Snapshot preview = FontPreviewState.get();
		if (preview == null || preview.data().rectangles().isEmpty()) {
			return;
		}
		if (context.worldState() == null || context.worldState().cameraRenderState == null) {
			return;
		}

		Vec3 camera = context.worldState().cameraRenderState.pos;
		if (camera == null) {
			return;
		}

		BlockState blockState = FontBlockRenderer.resolveBlockState(preview.data().blockId());
		PoseStack poses = context.matrices();
		SubmitNodeCollector collector = context.commandQueue();
		poses.pushPose();
		poses.translate(
				preview.pos().getX() - camera.x,
				preview.pos().getY() - camera.y,
				preview.pos().getZ() - camera.z
		);
		FontBlockRenderer.submitText(
				poses,
				collector,
				preview.data(),
				preview.facing(),
				blockState,
				LightTexture.FULL_BRIGHT
		);
		poses.popPose();
	}
}
