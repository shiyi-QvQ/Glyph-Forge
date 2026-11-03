package com.creat.fonts.client.gui;

import com.creat.fonts.CreatFonts;
import com.creat.fonts.client.history.FontHistory;
import com.creat.fonts.client.render.FontPreviewState;
import com.creat.fonts.client.render.TextBitmap;
import com.creat.fonts.client.render.TextRasterizer;
import com.creat.fonts.font.FontData;
import com.creat.fonts.font.GlyphRect;
import com.creat.fonts.network.ApplyFontTextPayload;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;



public class FontEditorScreen extends Screen {
	private static final int VIRTUAL_WIDTH = 660;
	private static final int VIRTUAL_HEIGHT = 490;
	private static final String[] AXIS_LABELS = {"X", "Y", "Z"};

	private BlockPos pos;
	private final boolean creating;
	private Direction facing;
	private final FontData initialData;
	private final TextBitmap storedBitmap;
	private final boolean hasStoredArt;
	private final boolean hadStoredFont;

	private MultiLineEditBox textBox;
	private DarkButton fontButton;
	private DarkButton blockButton;
	private ItemIconWidget blockIcon;
	private DarkButton boldButton;
	private DarkButton italicButton;
	private DarkButton mergeButton;
	private DarkButton charPrevButton;
	private DarkButton charMinusButton;
	private DarkButton charResetButton;
	private DarkButton charPlusButton;
	private DarkButton charNextButton;

	private String draftText;
	private int pixelHeight;
	private float pixelScale;
	private float thickness;
	private float gap;
	private boolean bold;
	private boolean italic;
	private boolean merge;
	private boolean attached;
	private List<Float> charScales = new ArrayList<>();
	private int selectedCharIndex;

	private String selectedBlockId = "";
	private Component selectedBlockName = Component.translatable("gui.creat-fonts.block.none");
	private ItemStack selectedBlockStack = ItemStack.EMPTY;

	private @Nullable Font externalFont;
	private String externalFontName = "";
	private String externalFontKey = "";

	private @Nullable Component statusText;
	private int statusColor = GuiTheme.MUTED;
	private long statusExpiresAt;

	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private float uiScale = 1.0F;
	private float uiOffsetX;
	private float uiOffsetY;
	private int leftX;
	private int leftY;
	private int leftW;
	private int leftH;
	private int rightX;
	private int rightY;
	private int rightW;
	private int previewX;
	private int previewY;
	private int previewW;
	private int previewH;
	private int effectsLabelY;
	private int effectsRowY;
	private int fontLabelY;
	private int fontRowY;
	private int blockLabelY;
	private int blockRowY;
	private int positionLabelY;
	private int positionRowY;
	private int facingRowY;
	private int statusY;
	private DarkButton facingButton;
	private DarkButton attachButton;
	private boolean keepPreviewOnRemove;

	private FontEditorScreen(BlockPos pos, Direction facing, FontData initialData, boolean creating) {
		super(Component.translatable("gui.creat-fonts.title"));
		this.pos = pos == null ? BlockPos.ZERO : pos.immutable();
		this.facing = facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing;
		this.creating = creating;
		this.initialData = initialData == null ? FontData.DEFAULT : initialData;
		this.attached = this.initialData.attached();
		this.storedBitmap = TextBitmap.fromRectangles(
				this.initialData.bitmapWidth(),
				this.initialData.bitmapHeight(),
				this.initialData.rectangles()
		);
		this.hasStoredArt = !this.storedBitmap.isEmpty();
		this.hadStoredFont = !this.initialData.fontName().isEmpty();
		this.applyDataToFields(this.initialData);
	}

	public static FontEditorScreen forCreation(BlockPos pos, Direction facing) {
		return new FontEditorScreen(pos, facing, FontData.DEFAULT, true);
	}

	public static FontEditorScreen forEditing(BlockPos pos, Direction facing, FontData data) {
		return new FontEditorScreen(pos, facing, data, false);
	}

	@Override
	protected void init() {
		super.init();



		if (this.creating && this.selectedBlockId.isEmpty()) {
			this.selectFirstInventoryBlock();
		}

		this.uiScale = Math.min(1.0F, Math.min(
				(this.width - 12.0F) / VIRTUAL_WIDTH,
				(this.height - 12.0F) / VIRTUAL_HEIGHT
		));
		this.uiScale = Math.max(0.05F, this.uiScale);
		this.uiOffsetX = (this.width - VIRTUAL_WIDTH * this.uiScale) * 0.5F;
		this.uiOffsetY = (this.height - VIRTUAL_HEIGHT * this.uiScale) * 0.5F;

		this.panelW = VIRTUAL_WIDTH;
		this.panelH = VIRTUAL_HEIGHT;
		this.panelX = 0;
		this.panelY = 0;

		this.leftX = this.panelX + 14;
		this.leftY = this.panelY + 14;
		this.leftW = Math.max(200, Math.round(this.panelW * 0.46F));
		this.leftH = this.panelH - 28;

		this.rightX = this.leftX + this.leftW + 14;
		this.rightY = this.leftY;
		this.rightW = Math.max(170, this.panelX + this.panelW - 14 - this.rightX);

		this.previewX = this.leftX + 12;
		this.previewY = this.leftY + 42;
		this.previewW = Math.max(80, this.leftW - 24);
		this.previewH = Math.max(70, this.leftH - 130);

		int textY = this.rightY + 30;
		int fieldWidth = this.rightW - 62;
		this.textBox = MultiLineEditBox.builder()
				.setX(this.rightX)
				.setY(textY)
				.setPlaceholder(Component.translatable("gui.creat-fonts.placeholder"))
				.setTextColor(GuiTheme.TEXT)
				.setCursorColor(GuiTheme.ACCENT)
				.setTextShadow(false)
				.setShowBackground(false)
				.setShowDecorations(false)
				.build(this.font, fieldWidth, 54, Component.translatable("gui.creat-fonts.text"));
		this.textBox.setCharacterLimit(FontData.MAX_TEXT_LENGTH);
		this.textBox.setLineLimit(Integer.MAX_VALUE);
		this.textBox.setValue(this.draftText);
		this.textBox.setValueListener(value -> {
			this.draftText = value;
			this.syncCharScales();
			this.updateDynamicLabels();
		});
		this.addRenderableWidget(this.textBox);

		this.addRenderableWidget(new DarkButton(
				this.rightX + this.rightW - 54, textY, 54, 20,
				Component.translatable("gui.creat-fonts.paste"),
				button -> this.pasteClipboard(),
				false
		));
		this.addRenderableWidget(new DarkButton(
				this.rightX + this.rightW - 54, textY + 24, 54, 20,
				Component.translatable("gui.creat-fonts.reset_text"),
				button -> this.resetText(),
				false
		));

		int sliderY = textY + 70;
		this.addRenderableWidget(new DarkSlider(
				this.rightX, sliderY, this.rightW, 20,
				"gui.creat-fonts.slider.size",
				FontData.MIN_PIXEL_HEIGHT, FontData.MAX_PIXEL_HEIGHT, 1,
				this.pixelHeight,
				value -> {
					this.pixelHeight = (int) Math.round(value);
					this.refreshWorldPreview();
				},
				value -> Integer.toString((int) Math.round(value))
		));
		this.addRenderableWidget(new DarkSlider(
				this.rightX, sliderY + 24, this.rightW, 20,
				"gui.creat-fonts.slider.scale",
				FontData.MIN_PIXEL_SCALE, FontData.MAX_PIXEL_SCALE, 0.001,
				this.pixelScale,
				value -> {
					this.pixelScale = (float) value;
					this.refreshWorldPreview();
				},
				value -> String.format(Locale.ROOT, "%.3f", value)
		));
		this.addRenderableWidget(new DarkSlider(
				this.rightX, sliderY + 48, this.rightW, 20,
				"gui.creat-fonts.slider.thickness",
				FontData.MIN_THICKNESS, FontData.MAX_THICKNESS, 0.01,
				this.thickness,
				value -> {
					this.thickness = (float) value;
					this.refreshWorldPreview();
				},
				value -> String.format(Locale.ROOT, "%.2f", value)
		));
		this.addRenderableWidget(new DarkSlider(
				this.rightX, sliderY + 72, this.rightW, 20,
				"gui.creat-fonts.slider.gap",
				FontData.MIN_GAP, FontData.MAX_GAP, 0.01,
				this.gap,
				value -> {
					this.gap = (float) value;
					this.refreshWorldPreview();
				},
				value -> String.format(Locale.ROOT, "%.2f", value)
		));

		this.effectsLabelY = sliderY + 96 + 8;
		this.effectsRowY = this.effectsLabelY + 12;
		int third = Math.max(64, (this.rightW - 16) / 3);
		int thirdStep = third + 8;
		this.boldButton = new DarkButton(
				this.rightX, this.effectsRowY, third, 20,
				toggleMessage("gui.creat-fonts.bold", this.bold),
				button -> {
					this.bold = !this.bold;
					this.updateToggleLabels();
					this.updateDynamicLabels();
				},
				false
		);
		this.italicButton = new DarkButton(
				this.rightX + thirdStep, this.effectsRowY, third, 20,
				toggleMessage("gui.creat-fonts.italic", this.italic),
				button -> {
					this.italic = !this.italic;
					this.updateToggleLabels();
					this.updateDynamicLabels();
				},
				false
		);
		this.mergeButton = new DarkButton(
				this.rightX + thirdStep * 2, this.effectsRowY,
				Math.max(64, this.rightW - thirdStep * 2), 20,
				toggleMessage("gui.creat-fonts.merge", this.merge),
				button -> {
					this.merge = !this.merge;
					this.updateToggleLabels();
				},
				false
		);
		this.addRenderableWidget(this.boldButton);
		this.addRenderableWidget(this.italicButton);
		this.addRenderableWidget(this.mergeButton);

		int charRowY = this.effectsRowY + 24;
		int navWidth = 18;
		int navGap = 4;
		int navSpan = navWidth * 2 + navGap * 2;
		int charLabelWidth = Math.max(80, this.rightW - navSpan * 2);
		this.charPrevButton = new DarkButton(
				this.rightX, charRowY, navWidth, 20,
				Component.literal("◀"),
				button -> this.selectCharacter(-1),
				false
		);
		this.charMinusButton = new DarkButton(
				this.rightX + navWidth + navGap, charRowY, navWidth, 20,
				Component.literal("−"),
				button -> this.nudgeCharacterScale(-0.05F),
				false
		);
		this.charResetButton = new DarkButton(
				this.rightX + navSpan, charRowY, charLabelWidth, 20,
				Component.empty(),
				button -> this.resetCharacterScale(),
				false
		);
		this.charPlusButton = new DarkButton(
				this.rightX + this.rightW - navWidth * 2 - navGap, charRowY, navWidth, 20,
				Component.literal("+"),
				button -> this.nudgeCharacterScale(0.05F),
				false
		);
		this.charNextButton = new DarkButton(
				this.rightX + this.rightW - navWidth, charRowY, navWidth, 20,
				Component.literal("▶"),
				button -> this.selectCharacter(1),
				false
		);
		this.addRenderableWidget(this.charPrevButton);
		this.addRenderableWidget(this.charMinusButton);
		this.addRenderableWidget(this.charResetButton);
		this.addRenderableWidget(this.charPlusButton);
		this.addRenderableWidget(this.charNextButton);
		this.updateCharControls();

		this.fontLabelY = this.effectsRowY + 24 + 30;
		this.fontRowY = this.fontLabelY + 12;
		int chooseWidth = 70;
		this.fontButton = new DarkButton(
				this.rightX, this.fontRowY, this.rightW - chooseWidth - 8, 20,
				this.fontLabel(),
				button -> this.chooseFontFile(),
				false
		);
		this.addRenderableWidget(this.fontButton);
		this.addRenderableWidget(new DarkButton(
				this.rightX + this.rightW - chooseWidth, this.fontRowY, chooseWidth, 20,
				Component.translatable("gui.creat-fonts.font.choose"),
				button -> this.chooseFontFile(),
				false
		));

		this.blockLabelY = this.fontRowY + 20 + 10;
		this.blockRowY = this.blockLabelY + 12;
		this.blockIcon = new ItemIconWidget(this.rightX, this.blockRowY);
		this.addRenderableWidget(this.blockIcon);
		this.blockButton = new DarkButton(
				this.rightX + 24, this.blockRowY, this.rightW - 24, 20,
				this.blockLabel(),
				button -> this.openBlockPicker(),
				false
		);
		this.addRenderableWidget(this.blockButton);

		this.positionLabelY = this.blockRowY + 34;
		this.positionRowY = this.positionLabelY + 12;
		this.facingRowY = this.positionRowY + 24;
		if (this.creating) {
			this.addPositionControls();
		} else {
			this.attachButton = new DarkButton(
					this.rightX, this.facingRowY, this.rightW, 20,
					this.attachLabel(),
					button -> this.toggleAttached(),
					false
			);
			this.addRenderableWidget(this.attachButton);
		}
		this.statusY = this.facingRowY + 26;

		int bottomY = this.panelY + this.panelH - 40;
		int applyWidth = 78;
		int cancelWidth = 60;
		this.addRenderableWidget(new DarkButton(
				this.rightX + this.rightW - applyWidth, bottomY, applyWidth, 24,
				Component.translatable(this.creating ? "gui.creat-fonts.generate" : "gui.creat-fonts.apply"),
				button -> this.apply(),
				true
		));
		this.addRenderableWidget(new DarkButton(
				this.rightX + this.rightW - applyWidth - cancelWidth - 8, bottomY, cancelWidth, 24,
				Component.translatable("gui.creat-fonts.cancel"),
				button -> this.onClose(),
				false
		));
		this.addRenderableWidget(new DarkButton(
				this.rightX + this.rightW - applyWidth - cancelWidth - 8 - 84, bottomY, 76, 24,
				Component.translatable("gui.creat-fonts.history"),
				button -> {
					if (this.minecraft != null) {
						this.minecraft.setScreen(new FontHistoryScreen(this));
					}
				},
				false
		));

		this.syncCharScales();
		this.updateCharControls();
		this.updatePositionLabels();
		this.refreshWorldPreview();
		this.setInitialFocus(this.textBox);
	}

	private void addPositionControls() {
		int groupWidth = 68;
		int gap = 6;
		for (int axis = 0; axis < 3; axis++) {
			int groupX = this.rightX + axis * (groupWidth + gap);
			int selectedAxis = axis;
			this.addRenderableWidget(new DarkButton(
					groupX + 10, this.positionRowY, 14, 20,
					Component.literal("-"),
					button -> this.movePosition(selectedAxis, -1),
					false
			));
			this.addRenderableWidget(new DarkButton(
					groupX + 54, this.positionRowY, 14, 20,
					Component.literal("+"),
					button -> this.movePosition(selectedAxis, 1),
					false
			));
		}

		int usedWidth = 3 * (groupWidth + gap);
		this.addRenderableWidget(new DarkButton(
				this.rightX + usedWidth + 2, this.positionRowY, this.rightW - usedWidth - 8, 20,
				Component.translatable("gui.creat-fonts.position.pick"),
				button -> this.beginPlacement(),
				false
		));

		int facingWidth = Math.max(80, (this.rightW - 8) / 2);
		this.facingButton = new DarkButton(
				this.rightX, this.facingRowY, facingWidth, 20,
				this.facingLabel(),
				button -> this.cycleFacing(),
				false
		);
		this.addRenderableWidget(this.facingButton);
		this.attachButton = new DarkButton(
				this.rightX + facingWidth + 8, this.facingRowY,
				this.rightW - facingWidth - 8, 20,
				this.attachLabel(),
				button -> this.toggleAttached(),
				false
		);
		this.addRenderableWidget(this.attachButton);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.uiOffsetX, this.uiOffsetY);
		graphics.pose().scale(this.uiScale, this.uiScale);

		int virtualMouseX = (int) Math.round(this.toVirtualX(mouseX));
		int virtualMouseY = (int) Math.round(this.toVirtualY(mouseY));
		this.drawPanel(graphics, this.panelX, this.panelY, this.panelW, this.panelH);
		this.drawHeader(graphics);
		TextBitmap bitmap = this.currentBitmap();
		this.drawPreviewArea(graphics, bitmap);
		this.drawControlsBackground(graphics);
		this.drawPositionSection(graphics);
		super.render(graphics, virtualMouseX, virtualMouseY, delta);
		this.drawStatus(graphics);

		graphics.pose().popMatrix();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return super.mouseClicked(this.toVirtualEvent(event), doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return super.mouseReleased(this.toVirtualEvent(event));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		return super.mouseDragged(
				this.toVirtualEvent(event),
				dragX / this.uiScale,
				dragY / this.uiScale
		);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return super.mouseScrolled(this.toVirtualX(mouseX), this.toVirtualY(mouseY), scrollX, scrollY);
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		super.mouseMoved(this.toVirtualX(mouseX), this.toVirtualY(mouseY));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void added() {
		this.keepPreviewOnRemove = false;
		super.added();
	}

	@Override
	public void removed() {
		if (!this.keepPreviewOnRemove) {
			FontPreviewState.clear();
		}
		this.keepPreviewOnRemove = false;
		super.removed();
	}

	@Override
	public void onClose() {
		FontPreviewState.clear();
		super.onClose();
	}

	private double toVirtualX(double mouseX) {
		return (mouseX - this.uiOffsetX) / this.uiScale;
	}

	private double toVirtualY(double mouseY) {
		return (mouseY - this.uiOffsetY) / this.uiScale;
	}

	private double toRealX(double virtualX) {
		return virtualX * this.uiScale + this.uiOffsetX;
	}

	private double toRealY(double virtualY) {
		return virtualY * this.uiScale + this.uiOffsetY;
	}

	private MouseButtonEvent toVirtualEvent(MouseButtonEvent event) {
		return new MouseButtonEvent(this.toVirtualX(event.x()), this.toVirtualY(event.y()), event.buttonInfo());
	}

	private void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
		graphics.fill(x, y, x + width, y + height, GuiTheme.PANEL);
		graphics.fill(x, y, x + width, y + 1, GuiTheme.BORDER);
		graphics.fill(x, y + height - 1, x + width, y + height, GuiTheme.BORDER);
		graphics.fill(x, y, x + 1, y + height, GuiTheme.BORDER);
		graphics.fill(x + width - 1, y, x + width, y + height, GuiTheme.BORDER);
		graphics.fill(x + 1, y + 1, x + width - 1, y + 2, GuiTheme.HIGHLIGHT);
		graphics.fill(x, y, x + 3, y + height, GuiTheme.ACCENT);
		graphics.fill(x + 3, y, x + 4, y + height, GuiTheme.ACCENT_DARK);
	}

	private void drawHeader(GuiGraphics graphics) {
		graphics.drawString(this.font, Component.translatable("gui.creat-fonts.title"), this.leftX, this.leftY + 2, GuiTheme.TEXT, false);
		graphics.drawString(
				this.font,
				Component.translatable(this.creating ? "gui.creat-fonts.subtitle.create" : "gui.creat-fonts.subtitle.edit"),
				this.leftX,
				this.leftY + 15,
				GuiTheme.MUTED,
				false
		);
		graphics.fill(this.leftX, this.leftY + 27, this.panelX + this.panelW - 14, this.leftY + 28, GuiTheme.BORDER_SOFT);
		this.drawSectionLabel(graphics, Component.translatable("gui.creat-fonts.control_header"), this.rightX, this.rightY + 2);
	}

	private void drawSectionLabel(GuiGraphics graphics, Component label, int x, int y) {
		graphics.fill(x, y + 1, x + 2, y + 9, GuiTheme.ACCENT);
		graphics.drawString(this.font, label, x + 6, y, GuiTheme.MUTED, false);
	}

	private void drawPreviewArea(GuiGraphics graphics, TextBitmap bitmap) {
		graphics.drawString(this.font, Component.translatable("gui.creat-fonts.preview"), this.previewX, this.previewY - 13, GuiTheme.ACCENT, false);
		graphics.fill(this.previewX, this.previewY, this.previewX + this.previewW, this.previewY + this.previewH, GuiTheme.PANEL_ALT);
		graphics.fill(this.previewX, this.previewY, this.previewX + this.previewW, this.previewY + 1, GuiTheme.BORDER_SOFT);
		graphics.fill(this.previewX, this.previewY + this.previewH - 1, this.previewX + this.previewW, this.previewY + this.previewH, GuiTheme.BORDER_SOFT);
		graphics.fill(this.previewX, this.previewY, this.previewX + 1, this.previewY + this.previewH, GuiTheme.BORDER_SOFT);
		graphics.fill(this.previewX + this.previewW - 1, this.previewY, this.previewX + this.previewW, this.previewY + this.previewH, GuiTheme.BORDER_SOFT);


		graphics.enableScissor(
				this.previewX + 1,
				this.previewY + 1,
				this.previewX + this.previewW - 1,
				this.previewY + this.previewH - 1
		);
		for (int gridX = this.previewX + 8; gridX < this.previewX + this.previewW; gridX += 8) {
			graphics.fill(gridX, this.previewY + 2, gridX + 1, this.previewY + this.previewH - 2, GuiTheme.GRID);
		}
		for (int gridY = this.previewY + 8; gridY < this.previewY + this.previewH; gridY += 8) {
			graphics.fill(this.previewX + 2, gridY, this.previewX + this.previewW - 2, gridY + 1, GuiTheme.GRID);
		}

		if (bitmap.isEmpty()) {
			graphics.drawCenteredString(
					this.font,
					Component.translatable("gui.creat-fonts.empty"),
					this.previewX + this.previewW / 2,
					this.previewY + this.previewH / 2 - 4,
					GuiTheme.MUTED
			);
		} else {
			float fit = Math.min(
					(this.previewW - 24.0F) / bitmap.width(),
					(this.previewH - 24.0F) / bitmap.height()
			);
			float scale = Math.min(8.0F, Math.max(0.1F, fit));
			int drawWidth = Math.max(1, Math.round(bitmap.width() * scale));
			int drawHeight = Math.max(1, Math.round(bitmap.height() * scale));
			int originX = this.previewX + (this.previewW - drawWidth) / 2;
			int originY = this.previewY + (this.previewH - drawHeight) / 2;
			int previewColor = this.previewColor();
			boolean mergedMode = this.merge || bitmap.filledPixels() > TextRasterizer.AUTO_MERGE_THRESHOLD;
			boolean showGap = this.gap > 0.001F;

			if (mergedMode) {
				for (GlyphRect rect : bitmap.rectangles()) {
					this.fillScaled(graphics, originX, originY, rect, scale, previewColor, showGap);
				}
			} else {
				for (GlyphRect rect : bitmap.pixelRectangles()) {
					this.fillScaled(graphics, originX, originY, rect, scale, previewColor, showGap);
				}
			}
		}
		graphics.disableScissor();

		int statsY = this.previewY + this.previewH + 8;
		String widthBlocks = String.format(Locale.ROOT, "%.2f", bitmap.width() * this.pixelScale);
		String heightBlocks = String.format(Locale.ROOT, "%.2f", bitmap.height() * this.pixelScale);
		boolean autoMerged = !this.merge && bitmap.filledPixels() > TextRasterizer.AUTO_MERGE_THRESHOLD;
		int blockCount = this.merge || autoMerged ? bitmap.rectangles().size() : bitmap.filledPixels();
		String modeKey = this.merge
				? "gui.creat-fonts.mode.merged"
				: autoMerged ? "gui.creat-fonts.mode.auto" : "gui.creat-fonts.mode.separate";
		graphics.drawString(this.font, Component.translatable("gui.creat-fonts.stats.size", widthBlocks, heightBlocks), this.leftX, statsY, GuiTheme.MUTED, false);
		graphics.drawString(this.font, Component.translatable("gui.creat-fonts.stats.blocks", blockCount), this.leftX, statsY + 11, GuiTheme.MUTED, false);
		graphics.drawString(
				this.font,
				Component.translatable("gui.creat-fonts.stats.mode", Component.translatable(modeKey)),
				this.leftX,
				statsY + 22,
				GuiTheme.MUTED,
				false
		);
		graphics.drawString(
				this.font,
				Component.translatable(this.creating ? "gui.creat-fonts.footer.create" : "gui.creat-fonts.footer.edit"),
				this.leftX,
				this.panelY + this.panelH - 18,
				GuiTheme.MUTED,
				false
		);
	}

	private void fillScaled(
			GuiGraphics graphics,
			int originX,
			int originY,
			GlyphRect rect,
			float scale,
			int color,
			boolean showGap
	) {
		int left = originX + Math.round(rect.x() * scale);
		int top = originY + Math.round(rect.y() * scale);
		int right = originX + Math.round((rect.x() + rect.width()) * scale);
		int bottom = originY + Math.round((rect.y() + rect.height()) * scale);
		if (right <= left) {
			right = left + 1;
		}
		if (bottom <= top) {
			bottom = top + 1;
		}
		if (showGap && right - left >= 3) {
			left++;
			right--;
		}
		if (showGap && bottom - top >= 3) {
			top++;
			bottom--;
		}
		graphics.fill(left, top, right, bottom, color);
	}

	private void drawControlsBackground(GuiGraphics graphics) {
		int textY = this.rightY + 30;
		this.drawSectionLabel(graphics, Component.translatable("gui.creat-fonts.text_label"), this.rightX, textY - 12);
		graphics.fill(this.rightX, textY, this.rightX + this.rightW - 62, textY + 54, GuiTheme.PANEL_ALT);
		this.drawBox(graphics, this.rightX, textY, this.rightW - 62, 54);
		graphics.drawString(
				this.font,
				Component.translatable("gui.creat-fonts.character_count", visibleCharacterCount(this.currentText())),
				this.rightX,
				textY + 58,
				GuiTheme.MUTED,
				false
		);

		this.drawSectionLabel(graphics, Component.translatable("gui.creat-fonts.effects"), this.rightX, this.effectsLabelY);
		this.drawSectionLabel(graphics, Component.translatable("gui.creat-fonts.font"), this.rightX, this.fontLabelY);
		this.drawSectionLabel(graphics, Component.translatable("gui.creat-fonts.block"), this.rightX, this.blockLabelY);
	}

	private void drawBox(GuiGraphics graphics, int x, int y, int width, int height) {
		graphics.fill(x, y, x + width, y + 1, GuiTheme.BORDER);
		graphics.fill(x, y + height - 1, x + width, y + height, GuiTheme.BORDER);
		graphics.fill(x, y, x + 1, y + height, GuiTheme.BORDER);
		graphics.fill(x + width - 1, y, x + width, y + height, GuiTheme.BORDER);
	}

	private void drawPositionSection(GuiGraphics graphics) {
		this.drawSectionLabel(graphics, Component.translatable("gui.creat-fonts.position"), this.rightX, this.positionLabelY);
		if (!this.creating) {
			graphics.drawString(
					this.font,
					Component.translatable("gui.creat-fonts.position.readonly", this.pos.getX(), this.pos.getY(), this.pos.getZ()),
					this.rightX,
					this.positionRowY + 4,
					GuiTheme.MUTED,
					false
			);
			return;
		}

		int groupWidth = 68;
		int gap = 6;
		for (int axis = 0; axis < 3; axis++) {
			int groupX = this.rightX + axis * (groupWidth + gap);
			int value = switch (axis) {
				case 0 -> this.pos.getX();
				case 1 -> this.pos.getY();
				default -> this.pos.getZ();
			};
			graphics.drawString(this.font, AXIS_LABELS[axis], groupX, this.positionRowY + 6, GuiTheme.ACCENT, false);
			graphics.drawCenteredString(
					this.font,
					Component.literal(Integer.toString(value)),
					groupX + 39,
					this.positionRowY + 6,
					GuiTheme.TEXT
			);
		}
	}

	private void movePosition(int axis, int delta) {
		int x = this.pos.getX();
		int y = this.pos.getY();
		int z = this.pos.getZ();
		switch (axis) {
			case 0 -> x += delta;
			case 1 -> y += delta;
			default -> z += delta;
		}
		if (this.minecraft != null && this.minecraft.level != null) {
			y = Mth.clamp(y, this.minecraft.level.getMinY(), this.minecraft.level.getMaxY() - 1);
		}
		this.pos = new BlockPos(x, y, z);
		this.updatePositionLabels();
		this.refreshWorldPreview();
	}

	private void cycleFacing() {
		this.facing = this.facing.getClockWise();
		this.updatePositionLabels();
		this.refreshWorldPreview();
	}

	private void toggleAttached() {
		this.attached = !this.attached;
		this.updateAttachLabel();
		this.refreshWorldPreview();
	}

	private Component attachLabel() {
		return toggleMessage("gui.creat-fonts.attach", this.attached);
	}

	private void updateAttachLabel() {
		if (this.attachButton != null) {
			this.attachButton.setMessage(this.attachLabel());
		}
	}

	private Component facingName() {
		String key = switch (this.facing) {
			case SOUTH -> "gui.creat-fonts.direction.south";
			case WEST -> "gui.creat-fonts.direction.west";
			case EAST -> "gui.creat-fonts.direction.east";
			default -> "gui.creat-fonts.direction.north";
		};
		return Component.translatable(key);
	}

	private Component facingLabel() {
		return Component.translatable("gui.creat-fonts.position.facing", this.facingName());
	}

	private void updatePositionLabels() {
		if (this.facingButton != null) {
			this.facingButton.setMessage(this.facingLabel());
		}
	}

	void setPlacement(BlockPos pos, Direction facing) {
		this.pos = pos == null ? BlockPos.ZERO : pos.immutable();
		this.facing = facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing;
		this.updatePositionLabels();
		this.refreshWorldPreview();
	}

	private void beginPlacement() {
		if (!this.creating) {
			return;
		}
		if (!this.hasSelectedBlock()) {
			this.showStatus("gui.creat-fonts.status.select_block", GuiTheme.DANGER, 5000);
			return;
		}
		FontData data = this.buildFontData();
		if (data == null) {
			this.showStatus("gui.creat-fonts.status.empty_text", GuiTheme.DANGER, 5000);
			return;
		}
		this.draftText = this.currentText();
		FontPreviewState.show(data, this.pos, this.facing);
		this.keepPreviewOnRemove = true;
		FontPlacementController.begin(this, this.pos, this.facing, this.attached);
	}

	private void refreshWorldPreview() {
		if (!this.creating) {
			FontPreviewState.clear();
			return;
		}
		FontData data = this.buildFontData();
		if (data == null) {
			FontPreviewState.clear();
			return;
		}
		FontPreviewState.show(data, this.pos, this.facing);
	}

	private @Nullable FontData buildFontData() {
		String text = this.currentText();
		if (text.isBlank()) {
			return null;
		}
		TextBitmap bitmap = this.currentBitmap();
		if (bitmap.isEmpty()) {
			return null;
		}
		boolean mergedMode = this.merge || bitmap.filledPixels() > TextRasterizer.AUTO_MERGE_THRESHOLD;
		List<GlyphRect> rectangles = mergedMode ? bitmap.rectangles() : bitmap.pixelRectangles();
		if (rectangles.size() > FontData.MAX_RECTANGLES) {
			rectangles = bitmap.rectangles();
		}
		return new FontData(
				text,
				this.pixelHeight,
				this.pixelScale,
				this.thickness,
				this.gap,
				this.bold,
				this.italic,
				this.charScales,
				this.merge,
				this.attached,
				this.selectedBlockId,
				this.fontNameToStore(),
				bitmap.width(),
				bitmap.height(),
				rectangles
		);
	}

	private void drawStatus(GuiGraphics graphics) {
		Component message = null;
		int color = GuiTheme.MUTED;
		if (this.statusText != null && System.currentTimeMillis() < this.statusExpiresAt) {
			message = this.statusText;
			color = this.statusColor;
		} else {
			Component hint = this.hintMessage();
			if (hint != null) {
				message = hint;
				color = GuiTheme.WARNING;
			}
		}
		if (message == null) {
			return;
		}

		String text = message.getString();
		if (this.font.width(text) > this.rightW) {
			int end = text.length();
			while (end > 1 && this.font.width(text.substring(0, end) + "…") > this.rightW) {
				end--;
			}
			text = text.substring(0, end) + "…";
		}
		graphics.drawString(this.font, text, this.rightX, this.statusY, color, false);
	}

	private @Nullable Component hintMessage() {
		if (!this.hasSelectedBlock()) {
			return Component.translatable(
					this.creating && this.selectedBlockId.isEmpty()
							? "gui.creat-fonts.hint.select_block"
							: "gui.creat-fonts.hint.invalid_block"
			);
		}
		if (this.creating
				&& this.minecraft != null
				&& this.minecraft.level != null
				&& !this.minecraft.level.getBlockState(this.pos).canBeReplaced()) {
			return Component.translatable("gui.creat-fonts.hint.position_blocked");
		}
		if (this.hadStoredFont && this.externalFont == null && this.rasterInputsChanged(this.currentText())) {
			return Component.translatable("gui.creat-fonts.hint.font_missing");
		}
		return null;
	}

	private void applyDataToFields(FontData data) {
		this.draftText = data.text();
		this.pixelHeight = data.pixelHeight();
		this.pixelScale = data.pixelScale();
		this.thickness = data.thickness();
		this.gap = data.gap();
		this.bold = data.bold();
		this.italic = data.italic();
		this.merge = data.merge();
		this.attached = data.attached();
		this.charScales = new ArrayList<>(data.charScales());
		this.selectedCharIndex = 0;
		this.selectedBlockId = this.creating ? "" : data.blockId();
		this.externalFont = null;
		this.externalFontName = "";
		this.externalFontKey = "";
		this.refreshSelectedBlock();
	}

	private String currentText() {
		return FontData.sanitize(this.textBox == null ? this.draftText : this.textBox.getValue());
	}

	private boolean rasterInputsChanged(String text) {
		return !text.equals(this.initialData.text())
				|| this.pixelHeight != this.initialData.pixelHeight()
				|| this.bold != this.initialData.bold()
				|| this.italic != this.initialData.italic()
				|| this.charScalesDifferFromInitial()
				|| this.externalFont != null;
	}

	private boolean charScalesDifferFromInitial() {
		int count = visibleCharacterCount(this.currentText());
		for (int i = 0; i < count; i++) {
			float current = i < this.charScales.size()
					? FontData.normalizeCharScale(this.charScales.get(i))
					: FontData.DEFAULT_CHAR_SCALE;
			float initial = this.initialData.charScaleAt(i);
			if (Math.abs(current - initial) > 0.0001F) {
				return true;
			}
		}
		return false;
	}

	private TextBitmap currentBitmap() {
		String text = this.currentText();
		if (this.hasStoredArt && !this.rasterInputsChanged(text)) {
			return this.storedBitmap;
		}
		return TextRasterizer.rasterize(
				text,
				this.pixelHeight,
				this.bold,
				this.italic,
				this.charScales,
				this.externalFont,
				this.externalFontKey
		);
	}

	private String fontNameToStore() {
		if (this.externalFont != null) {
			return this.externalFontName;
		}
		if (this.hasStoredArt && !this.rasterInputsChanged(this.currentText())) {
			return this.initialData.fontName();
		}
		return "";
	}

	private void apply() {
		if (this.minecraft == null) {
			return;
		}

		if (this.currentText().isBlank()) {
			this.showStatus("gui.creat-fonts.status.empty_text", GuiTheme.DANGER, 5000);
			return;
		}
		if (!this.hasSelectedBlock()) {
			this.showStatus("gui.creat-fonts.status.select_block", GuiTheme.DANGER, 5000);
			return;
		}

		FontData data = this.buildFontData();
		if (data == null) {
			this.showStatus("gui.creat-fonts.status.empty_text", GuiTheme.DANGER, 5000);
			return;
		}

		if (!ClientPlayNetworking.canSend(ApplyFontTextPayload.TYPE)) {
			this.showStatus("gui.creat-fonts.status.not_supported", GuiTheme.DANGER, 6000);
			return;
		}
		FontHistory.add(data);
		ClientPlayNetworking.send(new ApplyFontTextPayload(this.pos, this.facing, this.creating, data));
		this.onClose();
	}


	void applyHistoryEntry(FontHistory.Entry entry) {
		if (entry == null) {
			return;
		}
		this.draftText = entry.text();
		if (this.textBox != null) {
			this.textBox.setValue(entry.text());
		}
		this.pixelHeight = entry.pixelHeight();
		this.pixelScale = entry.pixelScale();
		this.thickness = entry.thickness();
		this.gap = entry.gap();
		this.bold = entry.bold();
		this.italic = entry.italic();
		this.merge = entry.merge();
		this.attached = entry.attached();
		this.charScales = new ArrayList<>(entry.charScales());
		this.selectedCharIndex = 0;
		this.selectedBlockId = entry.blockId();
		this.externalFont = null;
		this.externalFontName = "";
		this.externalFontKey = "";
		this.refreshSelectedBlock();
		this.syncCharScales();
		this.updateToggleLabels();
		this.updateDynamicLabels();
		this.refreshWorldPreview();
	}

	private void pasteClipboard() {
		String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
		if (clipboard == null || clipboard.isEmpty()) {
			return;
		}
		String updated = FontData.sanitize(this.currentText() + clipboard);
		this.draftText = updated;
		this.syncCharScales();
		if (this.textBox != null) {
			this.textBox.setValue(updated);
		}
		this.updateDynamicLabels();
	}

	private void resetText() {
		this.draftText = "";
		this.charScales = new ArrayList<>();
		this.selectedCharIndex = 0;
		if (this.textBox != null) {
			this.textBox.setValue("");
		}
		this.updateDynamicLabels();
	}

	private void updateToggleLabels() {
		if (this.boldButton != null) {
			this.boldButton.setMessage(toggleMessage("gui.creat-fonts.bold", this.bold));
			this.italicButton.setMessage(toggleMessage("gui.creat-fonts.italic", this.italic));
			this.mergeButton.setMessage(toggleMessage("gui.creat-fonts.merge", this.merge));
		}
	}

	private void updateDynamicLabels() {
		if (this.fontButton != null) {
			this.fontButton.setMessage(this.fontLabel());
		}
		if (this.blockButton != null) {
			this.blockButton.setMessage(this.blockLabel());
		}
		this.updateAttachLabel();
		this.updateCharControls();
		this.refreshWorldPreview();
	}

	private void syncCharScales() {
		int count = visibleCharacterCount(this.currentText());
		if (this.charScales.size() > count) {
			this.charScales.subList(count, this.charScales.size()).clear();
		}
		while (this.charScales.size() < count) {
			this.charScales.add(FontData.DEFAULT_CHAR_SCALE);
		}
		for (int i = 0; i < this.charScales.size(); i++) {
			this.charScales.set(i, FontData.normalizeCharScale(this.charScales.get(i)));
		}
		if (count == 0) {
			this.selectedCharIndex = 0;
		} else {
			this.selectedCharIndex = Math.min(this.selectedCharIndex, count - 1);
		}
	}

	private static int visibleCharacterCount(String text) {
		String visible = text.replace("\n", "");
		return visible.codePointCount(0, visible.length());
	}

	private void selectCharacter(int delta) {
		this.syncCharScales();
		int count = visibleCharacterCount(this.currentText());
		if (count == 0) {
			return;
		}
		this.selectedCharIndex = Math.floorMod(this.selectedCharIndex + delta, count);
		this.updateCharControls();
	}

	private void nudgeCharacterScale(float delta) {
		this.syncCharScales();
		int count = visibleCharacterCount(this.currentText());
		if (count == 0) {
			return;
		}
		int index = Math.max(0, Math.min(this.selectedCharIndex, count - 1));
		float current = this.charScales.get(index);
		float next = FontData.normalizeCharScale(Math.round((current + delta) * 100.0F) / 100.0F);
		if (Math.abs(next - current) < 0.0001F) {
			return;
		}
		this.charScales.set(index, next);
		this.updateCharControls();
		this.refreshWorldPreview();
	}

	private void resetCharacterScale() {
		this.syncCharScales();
		int count = visibleCharacterCount(this.currentText());
		if (count == 0) {
			return;
		}
		int index = Math.max(0, Math.min(this.selectedCharIndex, count - 1));
		this.charScales.set(index, FontData.DEFAULT_CHAR_SCALE);
		this.updateCharControls();
		this.refreshWorldPreview();
	}

	private void updateCharControls() {
		if (this.charPrevButton == null) {
			return;
		}
		int count = visibleCharacterCount(this.currentText());
		boolean hasCharacters = count > 0;
		if (this.selectedCharIndex >= count) {
			this.selectedCharIndex = hasCharacters ? count - 1 : 0;
		}

		this.charPrevButton.active = hasCharacters;
		this.charMinusButton.active = hasCharacters;
		this.charPlusButton.active = hasCharacters;
		this.charNextButton.active = hasCharacters;
		this.charResetButton.active = hasCharacters;

		if (!hasCharacters) {
			this.charResetButton.setMessage(Component.translatable("gui.creat-fonts.char_none"));
			return;
		}

		float scale = this.selectedCharIndex < this.charScales.size()
				? this.charScales.get(this.selectedCharIndex)
				: FontData.DEFAULT_CHAR_SCALE;
		this.charResetButton.setMessage(Component.translatable(
				"gui.creat-fonts.char_scale",
				this.selectedCharIndex + 1,
				this.selectedCharDisplay(),
				String.format(Locale.ROOT, "%.2f", scale)
		));
	}

	private String selectedCharDisplay() {
		String visible = this.currentText().replace("\n", "");
		int count = visible.codePointCount(0, visible.length());
		if (count == 0) {
			return "·";
		}
		int index = Math.max(0, Math.min(this.selectedCharIndex, count - 1));
		int codePoint = visible.codePointAt(visible.offsetByCodePoints(0, index));
		return codePoint == ' ' ? "␠" : new String(Character.toChars(codePoint));
	}

	private Component fontLabel() {
		if (this.externalFont != null) {
			return Component.translatable("gui.creat-fonts.font.current", shorten(this.externalFontName, 16));
		}
		if (this.hasStoredArt && this.hadStoredFont && !this.rasterInputsChanged(this.currentText())) {
			return Component.translatable("gui.creat-fonts.font.saved", shorten(this.initialData.fontName(), 16));
		}
		return Component.translatable("gui.creat-fonts.font.system");
	}

	private Component blockLabel() {
		if (!this.hasSelectedBlock()) {
			return Component.translatable("gui.creat-fonts.block.none");
		}
		return Component.translatable("gui.creat-fonts.block_button", shorten(this.selectedBlockName.getString(), 20));
	}

	private int previewColor() {
		if (this.selectedBlockStack.isEmpty() || !(this.selectedBlockStack.getItem() instanceof BlockItem blockItem)) {
			return GuiTheme.ACCENT;
		}
		try {
			int color = blockItem.getBlock().defaultMapColor().col;
			return color == 0 ? GuiTheme.ACCENT : 0xFF000000 | color;
		} catch (RuntimeException exception) {
			return GuiTheme.ACCENT;
		}
	}

	private static Component toggleMessage(String labelKey, boolean enabled) {
		return Component.translatable(
				"gui.creat-fonts.toggle",
				Component.translatable(labelKey),
				Component.translatable(enabled ? "gui.creat-fonts.on" : "gui.creat-fonts.off")
		);
	}

	private static String shorten(String text, int max) {
		if (text == null) {
			return "";
		}
		if (text.length() <= max) {
			return text;
		}
		return text.substring(0, Math.max(1, max - 1)) + "…";
	}

	private void showStatus(String translationKey, int color, long millis) {
		this.showStatus(Component.translatable(translationKey), color, millis);
	}

	private void showStatus(Component text, int color, long millis) {
		this.statusText = text;
		this.statusColor = color;
		this.statusExpiresAt = System.currentTimeMillis() + millis;
	}

	private void chooseFontFile() {
		String title = Component.translatable("gui.creat-fonts.font.dialog").getString();
		this.showStatus("gui.creat-fonts.status.choosing_font", GuiTheme.MUTED, 30000);
		Thread thread = new Thread(() -> {
			File file = null;
			boolean handled = false;
			Exception failure = null;



			if (NativeFileDialog.isSupported()) {
				try {
					file = NativeFileDialog.chooseFontFile(title);
					handled = true;
				} catch (IOException | RuntimeException exception) {
					failure = exception;
					CreatFonts.LOGGER.warn("The native font chooser failed", exception);
				}
			}

			if (!handled && !GraphicsEnvironment.isHeadless()) {
				try {
					file = showFontDialog();
					handled = true;
					failure = null;
				} catch (RuntimeException exception) {
					failure = exception;
					CreatFonts.LOGGER.warn("The AWT font chooser failed", exception);
				}
			}

			if (!handled) {
				String key = failure == null
						? "gui.creat-fonts.status.font_headless"
						: "gui.creat-fonts.status.font_unavailable";
				Minecraft.getInstance().execute(() -> {
					if (Minecraft.getInstance().screen == this) {
						this.showStatus(key, GuiTheme.DANGER, 6000);
					}
				});
				return;
			}

			if (file == null) {
				Minecraft.getInstance().execute(() -> {
					if (Minecraft.getInstance().screen == this) {
						this.statusText = null;
					}
				});
				return;
			}

			try {
				ExternalFont loaded = ExternalFont.load(file.toPath());
				Minecraft.getInstance().execute(() -> {
					if (Minecraft.getInstance().screen != this) {
						return;
					}
					this.externalFont = loaded.font();
					this.externalFontName = loaded.displayName();
					this.externalFontKey = loaded.cacheKey();
					this.updateDynamicLabels();
					this.showStatus(
							Component.translatable("gui.creat-fonts.status.font_loaded", shorten(loaded.displayName(), 32)),
							GuiTheme.SUCCESS,
							5000
					);
				});
			} catch (IOException | FontFormatException | RuntimeException exception) {
				CreatFonts.LOGGER.warn("Failed to load external font {}", file, exception);
				Minecraft.getInstance().execute(() -> {
					if (Minecraft.getInstance().screen == this) {
						this.showStatus("gui.creat-fonts.status.font_error", GuiTheme.DANGER, 6000);
					}
				});
			}
		}, "creat-fonts-font-loader");
		thread.setDaemon(true);
		thread.start();
	}

	private static @Nullable File showFontDialog() {
		if (GraphicsEnvironment.isHeadless()) {
			return null;
		}

		Frame owner = new Frame();
		try {
			FileDialog dialog = new FileDialog(
					owner,
					Component.translatable("gui.creat-fonts.font.dialog").getString(),
					FileDialog.LOAD
			);
			dialog.setFilenameFilter((dir, name) -> {
				String lower = name.toLowerCase(Locale.ROOT);
				return lower.endsWith(".ttf")
						|| lower.endsWith(".otf")
						|| lower.endsWith(".ttc")
						|| lower.endsWith(".otc");
			});
			dialog.setAlwaysOnTop(true);
			dialog.setVisible(true);
			String fileName = dialog.getFile();
			if (fileName == null || fileName.isBlank()) {
				return null;
			}
			String directory = dialog.getDirectory();
			return directory == null ? new File(fileName) : new File(directory, fileName);
		} finally {
			owner.dispose();
		}
	}

	private void openBlockPicker() {
		if (this.minecraft == null) {
			return;
		}
		List<BlockPickerScreen.BlockChoice> choices = this.collectInventoryBlocks();
		this.keepPreviewOnRemove = true;
		this.minecraft.setScreen(new BlockPickerScreen(this, choices, this.selectedBlockId, choice -> {
			this.selectedBlockId = choice.id();
			this.refreshSelectedBlock();
			this.updateDynamicLabels();
		}));
	}

	private List<BlockPickerScreen.BlockChoice> collectInventoryBlocks() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return List.of();
		}
		Map<String, BlockPickerScreen.BlockChoice> byId = new LinkedHashMap<>();
		for (ItemStack stack : this.minecraft.player.getInventory()) {
			addBlockChoice(byId, stack);
		}
		addBlockChoice(byId, this.minecraft.player.getOffhandItem());
		return List.copyOf(byId.values());
	}

	private static void addBlockChoice(Map<String, BlockPickerScreen.BlockChoice> byId, ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
			return;
		}
		Block block = blockItem.getBlock();
		if (block == Blocks.AIR || block == com.creat.fonts.registry.ModRegistry.FONT_BLOCK) {
			return;
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		if (id == null) {
			return;
		}
		String key = id.toString();
		if (byId.containsKey(key)) {
			return;
		}
		ItemStack icon = stack.copyWithCount(1);
		byId.put(key, new BlockPickerScreen.BlockChoice(key, icon, icon.getHoverName()));
	}

	private void selectFirstInventoryBlock() {
		List<BlockPickerScreen.BlockChoice> choices = this.collectInventoryBlocks();
		if (choices.isEmpty()) {
			return;
		}
		BlockPickerScreen.BlockChoice first = choices.get(0);
		this.selectedBlockId = first.id();
		this.refreshSelectedBlock();
	}

	private void refreshSelectedBlock() {
		this.selectedBlockName = Component.translatable("gui.creat-fonts.block.none");
		this.selectedBlockStack = ItemStack.EMPTY;

		Identifier id = Identifier.tryParse(this.selectedBlockId);
		Block block = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
		if (block == null || block == Blocks.AIR) {
			return;
		}
		Item item = block.asItem();
		if (item == Items.AIR) {
			return;
		}
		ItemStack stack = new ItemStack(item);
		this.selectedBlockStack = stack;
		this.selectedBlockName = stack.getHoverName();
	}

	private boolean hasSelectedBlock() {
		Identifier id = Identifier.tryParse(this.selectedBlockId);
		Block block = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
		return block != null
				&& block != Blocks.AIR
				&& block != com.creat.fonts.registry.ModRegistry.FONT_BLOCK
				&& block.asItem() != Items.AIR;
	}



	private final class ItemIconWidget extends AbstractWidget {
		private ItemIconWidget(int x, int y) {
			super(x, y, 20, 20, Component.empty());
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			int x = this.getX();
			int y = this.getY();
			graphics.fill(x, y, x + 20, y + 20, GuiTheme.PANEL_ALT);
			int border = this.isHoveredOrFocused() ? GuiTheme.ACCENT : GuiTheme.BORDER;
			graphics.fill(x, y, x + 20, y + 1, border);
			graphics.fill(x, y + 19, x + 20, y + 20, border);
			graphics.fill(x, y, x + 1, y + 20, border);
			graphics.fill(x + 19, y, x + 20, y + 20, border);

			if (!FontEditorScreen.this.selectedBlockStack.isEmpty()) {
				graphics.renderItem(FontEditorScreen.this.selectedBlockStack, x + 2, y + 2);
			} else {
				graphics.drawCenteredString(FontEditorScreen.this.font, "?", x + 10, y + 6, GuiTheme.MUTED);
			}

			if (this.isHoveredOrFocused()) {
				int realX = (int) Math.round(FontEditorScreen.this.toRealX(mouseX));
				int realY = (int) Math.round(FontEditorScreen.this.toRealY(mouseY));
				Component tooltip = FontEditorScreen.this.selectedBlockStack.isEmpty()
						? Component.translatable("gui.creat-fonts.block.none")
						: FontEditorScreen.this.selectedBlockName;
				graphics.setTooltipForNextFrame(FontEditorScreen.this.font, tooltip, realX, realY);
			}
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			FontEditorScreen.this.openBlockPicker();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, FontEditorScreen.this.blockLabel());
		}
	}



	private static final class DarkSlider extends AbstractSliderButton {
		private final String labelKey;
		private final double min;
		private final double max;
		private final double step;
		private final DoubleConsumer onChange;
		private final Function<Double, String> valueFormatter;

		private DarkSlider(
				int x,
				int y,
				int width,
				int height,
				String labelKey,
				double min,
				double max,
				double step,
				double initial,
				DoubleConsumer onChange,
				Function<Double, String> valueFormatter
		) {
			super(x, y, width, height, Component.empty(), clamp01((initial - min) / (max - min)));
			this.labelKey = labelKey;
			this.min = min;
			this.max = max;
			this.step = step;
			this.onChange = onChange;
			this.valueFormatter = valueFormatter;
			this.updateMessage();
		}

		private double number() {
			double raw = this.min + this.value * (this.max - this.min);
			double snapped = this.step <= 0 ? raw : Math.round(raw / this.step) * this.step;
			return Math.max(this.min, Math.min(this.max, snapped));
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.translatable(this.labelKey, this.valueFormatter.apply(this.number())));
		}

		@Override
		protected void applyValue() {
			this.onChange.accept(this.number());
		}

		@Override
		public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			boolean hovered = this.isHoveredOrFocused();
			int x = this.getX();
			int y = this.getY();
			int width = this.getWidth();
			int height = this.getHeight();
			graphics.fill(x, y, x + width, y + height, hovered ? GuiTheme.BUTTON : GuiTheme.PANEL_ALT);
			graphics.fill(x, y, x + width, y + 1, hovered ? GuiTheme.BUTTON_BORDER_HOVER : GuiTheme.BORDER);
			graphics.fill(x, y + height - 1, x + width, y + height, 0xFF0C0805);

			int trackY = y + height - 7;
			graphics.fill(x + 4, trackY, x + width - 4, trackY + 3, 0xFF2A2018);
			int filled = x + 4 + Math.round((float) this.value * (width - 8));
			graphics.fill(x + 4, trackY, filled, trackY + 3, GuiTheme.ACCENT);
			graphics.fill(filled - 2, y + height - 10, filled + 2, y + height - 2, GuiTheme.TEXT);
			graphics.drawString(Minecraft.getInstance().font, this.getMessage(), x + 5, y + 3, GuiTheme.TEXT, false);
			this.handleCursor(graphics);
		}

		private static double clamp01(double value) {
			if (Double.isNaN(value)) {
				return 0.0;
			}
			return Math.max(0.0, Math.min(1.0, value));
		}
	}
}
