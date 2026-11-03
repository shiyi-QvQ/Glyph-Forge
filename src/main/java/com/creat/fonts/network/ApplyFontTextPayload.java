package com.creat.fonts.network;

import com.creat.fonts.CreatFonts;
import com.creat.fonts.font.FontData;
import com.creat.fonts.font.GlyphRect;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;



public record ApplyFontTextPayload(BlockPos pos, Direction facing, boolean create, FontData data)
		implements CustomPacketPayload {
	public static final Type<ApplyFontTextPayload> TYPE =
			new Type<>(CreatFonts.id("apply_font_text"));

	public static final StreamCodec<FriendlyByteBuf, ApplyFontTextPayload> STREAM_CODEC = StreamCodec.of(
			(buf, payload) -> {
				FontData data = payload.data();
				buf.writeBlockPos(payload.pos());
				buf.writeByte(payload.facing().get3DDataValue());
				buf.writeBoolean(payload.create());
				buf.writeUtf(data.text(), FontData.MAX_TEXT_LENGTH);
				buf.writeVarInt(data.pixelHeight());
				buf.writeFloat(data.pixelScale());
				buf.writeFloat(data.thickness());
				buf.writeFloat(data.gap());
				buf.writeBoolean(data.bold());
				buf.writeBoolean(data.italic());
				buf.writeVarInt(data.charScales().size());
				for (float scale : data.charScales()) {
					buf.writeFloat(scale);
				}
				buf.writeBoolean(data.merge());
				buf.writeBoolean(data.attached());
				buf.writeUtf(data.blockId(), 128);
				buf.writeUtf(data.fontName(), 256);
				buf.writeVarInt(data.bitmapWidth());
				buf.writeVarInt(data.bitmapHeight());
				List<GlyphRect> rectangles = data.rectangles();
				buf.writeVarInt(rectangles.size());
				for (GlyphRect rect : rectangles) {
					buf.writeVarInt(rect.x());
					buf.writeVarInt(rect.y());
					buf.writeVarInt(rect.width());
					buf.writeVarInt(rect.height());
				}
			},
			buf -> {
				BlockPos pos = buf.readBlockPos();
				Direction facing = Direction.from3DDataValue(buf.readUnsignedByte());
				boolean create = buf.readBoolean();
				String text = buf.readUtf(FontData.MAX_TEXT_LENGTH);
				int pixelHeight = buf.readVarInt();
				float pixelScale = buf.readFloat();
				float thickness = buf.readFloat();
				float gap = buf.readFloat();
				boolean bold = buf.readBoolean();
				boolean italic = buf.readBoolean();
				int scaleCount = buf.readVarInt();
				if (scaleCount < 0 || scaleCount > FontData.MAX_TEXT_LENGTH) {
					throw new DecoderException("Invalid per-character scale count: " + scaleCount);
				}
				List<Float> charScales = new ArrayList<>(scaleCount);
				for (int i = 0; i < scaleCount; i++) {
					charScales.add(buf.readFloat());
				}
				boolean merge = buf.readBoolean();
				boolean attached = buf.readBoolean();
				String blockId = buf.readUtf(128);
				String fontName = buf.readUtf(256);
				int bitmapWidth = buf.readVarInt();
				int bitmapHeight = buf.readVarInt();
				int rectangleCount = buf.readVarInt();
				if (rectangleCount < 0 || rectangleCount > FontData.MAX_RECTANGLES) {
					throw new DecoderException("Invalid font rectangle count: " + rectangleCount);
				}
				List<GlyphRect> rectangles = new ArrayList<>(rectangleCount);
				for (int i = 0; i < rectangleCount; i++) {
					rectangles.add(new GlyphRect(
							buf.readVarInt(),
							buf.readVarInt(),
							buf.readVarInt(),
							buf.readVarInt()
					));
				}
				FontData data = new FontData(
						text,
						pixelHeight,
						pixelScale,
						thickness,
						gap,
						bold,
						italic,
						charScales,
						merge,
						attached,
						blockId,
						fontName,
						bitmapWidth,
						bitmapHeight,
						rectangles
				);
				return new ApplyFontTextPayload(pos, facing, create, data);
			}
	);

	public ApplyFontTextPayload {
		pos = pos == null ? BlockPos.ZERO : pos.immutable();
		if (facing == null || facing.getAxis().isVertical()) {
			facing = Direction.NORTH;
		}
		data = data == null ? FontData.DEFAULT : data;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
