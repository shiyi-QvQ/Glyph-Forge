package com.creat.fonts.client.render;

import com.creat.fonts.font.FontData;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;



public class FontBlockRenderState extends BlockEntityRenderState {
	public FontData data = FontData.DEFAULT;
	public Direction facing = Direction.NORTH;
	public String resolvedBlockId = "";
	public BlockState blockState = Blocks.WHITE_CONCRETE.defaultBlockState();
}
