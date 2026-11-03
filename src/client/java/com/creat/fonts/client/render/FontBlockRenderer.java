package com.creat.fonts.client.render;

import com.creat.fonts.block.FontBlock;
import com.creat.fonts.block.FontBlockEntity;
import com.creat.fonts.font.FontData;
import com.creat.fonts.font.GlyphRect;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;



public class FontBlockRenderer implements BlockEntityRenderer<FontBlockEntity, FontBlockRenderState> {
	@Override
	public FontBlockRenderState createRenderState() {
		return new FontBlockRenderState();
	}

	@Override
	public void extractRenderState(
			FontBlockEntity blockEntity,
			FontBlockRenderState state,
			float tickDelta,
			Vec3 cameraPos,
			ModelFeatureRenderer.CrumblingOverlay overlay
	) {
		BlockEntityRenderState.extractBase(blockEntity, state, overlay);
		state.data = blockEntity.getData();
		state.facing = blockEntity.getBlockState().getValue(FontBlock.FACING);



		if (!state.data.blockId().equals(state.resolvedBlockId)) {
			state.resolvedBlockId = state.data.blockId();
			state.blockState = resolveBlockState(state.resolvedBlockId);
		}
	}

	@Override
	public void submit(
			FontBlockRenderState state,
			PoseStack poseStack,
			SubmitNodeCollector collector,
			CameraRenderState cameraState
	) {
		FontData data = state.data;
		submitText(poseStack, collector, data, state.facing, state.blockState, state.lightCoords);
	}



	public static void submitText(
			PoseStack poseStack,
			SubmitNodeCollector collector,
			FontData data,
			Direction facing,
			BlockState blockState,
			int light
	) {
		List<GlyphRect> rectangles = data.rectangles();
		if (rectangles.isEmpty() || data.bitmapWidth() <= 0 || data.bitmapHeight() <= 0) {
			return;
		}

		float pixelScale = data.pixelScale();
		float depth = data.thickness();
		float halfWidth = data.bitmapWidth() * pixelScale * 0.5F;
		float halfHeight = data.bitmapHeight() * pixelScale * 0.5F;
		float inset = pixelScale * data.gap() * 0.5F;


		float zStart = data.attached() ? -0.5F + 0.001F : -depth * 0.5F;

		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));

		for (GlyphRect rect : rectangles) {
			float x0 = -halfWidth + rect.x() * pixelScale + inset;
			float x1 = -halfWidth + (rect.x() + rect.width()) * pixelScale - inset;
			float y1 = halfHeight - rect.y() * pixelScale - inset;
			float y0 = halfHeight - (rect.y() + rect.height()) * pixelScale + inset;
			float width = x1 - x0;
			float height = y1 - y0;
			if (width <= 0.0F || height <= 0.0F) {
				continue;
			}

			poseStack.pushPose();
			poseStack.translate(x0, y0, zStart);
			poseStack.scale(width, height, depth);
			collector.submitBlock(poseStack, blockState, light, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}

		poseStack.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 256;
	}

	public static BlockState resolveBlockState(String blockId) {
		Identifier id = Identifier.tryParse(blockId);
		Block block = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
		if (block == null || block == Blocks.AIR || block instanceof FontBlock) {
			block = Blocks.WHITE_CONCRETE;
		}
		return block.defaultBlockState();
	}
}
