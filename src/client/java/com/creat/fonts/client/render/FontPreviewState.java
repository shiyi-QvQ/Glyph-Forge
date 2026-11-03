package com.creat.fonts.client.render;

import com.creat.fonts.font.FontData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;



public final class FontPreviewState {
	private static @Nullable Snapshot snapshot;

	private FontPreviewState() {
	}

	public static void show(FontData data, BlockPos pos, Direction facing) {
		if (data == null || pos == null) {
			snapshot = null;
			return;
		}
		snapshot = new Snapshot(
				data,
				pos.immutable(),
				facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing
		);
	}

	public static void setPosition(BlockPos pos, Direction facing) {
		if (snapshot == null) {
			return;
		}
		show(snapshot.data(), pos, facing);
	}

	public static @Nullable Snapshot get() {
		return snapshot;
	}

	public static void clear() {
		snapshot = null;
	}

	public record Snapshot(FontData data, BlockPos pos, Direction facing) {
	}
}
