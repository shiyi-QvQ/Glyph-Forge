package com.creat.fonts.block;

import com.creat.fonts.font.FontData;
import com.creat.fonts.font.GlyphRect;
import com.creat.fonts.registry.ModRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;



public class FontBlockEntity extends BlockEntity {
	private FontData data = FontData.DEFAULT;

	public FontBlockEntity(BlockPos pos, BlockState state) {
		super(ModRegistry.FONT_BLOCK_ENTITY, pos, state);
	}

	public FontData getData() {
		return this.data;
	}

	public void setData(@Nullable FontData data) {
		FontData normalised = data == null ? FontData.DEFAULT : data;
		if (this.data.equals(normalised)) {
			return;
		}

		this.data = normalised;
		this.setChanged();
		if (this.level != null) {
			BlockState state = this.getBlockState();
			this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		FontData defaults = FontData.DEFAULT;
		int[] encodedRectangles = input.getIntArray("rectangles").orElse(new int[0]);
		List<GlyphRect> rectangles = FontData.decodeRectangles(encodedRectangles);
		int[] encodedScales = input.getIntArray("char_scales").orElse(new int[0]);
		this.data = new FontData(
				input.getStringOr("text", defaults.text()),
				input.getIntOr("pixel_height", defaults.pixelHeight()),
				input.getFloatOr("pixel_scale", defaults.pixelScale()),
				input.getFloatOr("thickness", defaults.thickness()),
				input.getFloatOr("gap", defaults.gap()),
				input.getBooleanOr("bold", defaults.bold()),
				input.getBooleanOr("italic", defaults.italic()),
				FontData.decodeCharScales(encodedScales),
				input.getBooleanOr("merge", defaults.merge()),
				input.getBooleanOr("attached", defaults.attached()),
				input.getStringOr("block_id", defaults.blockId()),
				input.getStringOr("font_name", defaults.fontName()),
				input.getIntOr("bitmap_width", defaults.bitmapWidth()),
				input.getIntOr("bitmap_height", defaults.bitmapHeight()),
				rectangles
		);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		output.putString("text", this.data.text());
		output.putInt("pixel_height", this.data.pixelHeight());
		output.putFloat("pixel_scale", this.data.pixelScale());
		output.putFloat("thickness", this.data.thickness());
		output.putFloat("gap", this.data.gap());
		output.putBoolean("bold", this.data.bold());
		output.putBoolean("italic", this.data.italic());
		output.putIntArray("char_scales", FontData.encodeCharScales(this.data.charScales()));
		output.putBoolean("merge", this.data.merge());
		output.putBoolean("attached", this.data.attached());
		output.putString("block_id", this.data.blockId());
		output.putString("font_name", this.data.fontName());
		output.putInt("bitmap_width", this.data.bitmapWidth());
		output.putInt("bitmap_height", this.data.bitmapHeight());
		output.putIntArray("rectangles", FontData.encodeRectangles(this.data.rectangles()));
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return this.saveCustomOnly(provider);
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
