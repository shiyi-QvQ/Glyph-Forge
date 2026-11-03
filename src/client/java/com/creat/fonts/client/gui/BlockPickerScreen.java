package com.creat.fonts.client.gui;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;



public class BlockPickerScreen extends Screen {
	private static final int VIRTUAL_WIDTH = 380;
	private static final int VIRTUAL_HEIGHT = 300;
	private static final int COLUMNS = 9;
	private static final int ROWS = 5;
	private static final int PAGE_SIZE = COLUMNS * ROWS;
	private static final int CELL = 32;
	private static final int SLOT = 30;
	private static final int GRID_X = (VIRTUAL_WIDTH - COLUMNS * CELL) / 2;
	private static final int GRID_Y = 52;

	private final Screen parent;
	private final List<BlockChoice> choices;
	private final Consumer<BlockChoice> onSelect;
	private String currentBlockId;
	private int page;

	private float uiScale = 1.0F;
	private float uiOffsetX;
	private float uiOffsetY;
	private int pageLabelY;
	private int pageButtonY;
	private int cancelY;

	public BlockPickerScreen(
			Screen parent,
			List<BlockChoice> choices,
			String currentBlockId,
			Consumer<BlockChoice> onSelect
	) {
		super(Component.translatable("gui.creat-fonts.picker.title"));
		this.parent = parent;
		this.choices = choices == null ? List.of() : List.copyOf(choices);
		this.currentBlockId = currentBlockId == null ? "" : currentBlockId;
		this.onSelect = onSelect == null ? choice -> { } : onSelect;
		this.page = this.initialPage();
	}

	private int initialPage() {
		for (int index = 0; index < this.choices.size(); index++) {
			if (this.choices.get(index).id().equals(this.currentBlockId)) {
				return index / PAGE_SIZE;
			}
		}
		return 0;
	}

	@Override
	protected void init() {
		super.init();

		this.uiScale = Math.min(1.0F, Math.min(
				(this.width - 12.0F) / VIRTUAL_WIDTH,
				(this.height - 12.0F) / VIRTUAL_HEIGHT
		));
		this.uiScale = Math.max(0.05F, this.uiScale);
		this.uiOffsetX = (this.width - VIRTUAL_WIDTH * this.uiScale) * 0.5F;
		this.uiOffsetY = (this.height - VIRTUAL_HEIGHT * this.uiScale) * 0.5F;

		this.pageLabelY = GRID_Y + ROWS * CELL + 6;
		this.pageButtonY = this.pageLabelY + 14;
		this.cancelY = VIRTUAL_HEIGHT - 38;

		int start = this.page * PAGE_SIZE;
		int end = Math.min(this.choices.size(), start + PAGE_SIZE);
		for (int index = start; index < end; index++) {
			int local = index - start;
			int column = local % COLUMNS;
			int row = local / COLUMNS;
			int x = GRID_X + column * CELL + 1;
			int y = GRID_Y + row * CELL + 1;
			this.addRenderableWidget(new BlockCell(this.choices.get(index), x, y));
		}

		if (this.pageCount() > 1) {
			DarkButton previous = new DarkButton(
					GRID_X, this.pageButtonY, 28, 20,
					Component.literal("<"),
					button -> {
						if (this.page > 0) {
							this.page--;
							this.rebuildWidgets();
						}
					},
					false
			);
			previous.active = this.page > 0;
			this.addRenderableWidget(previous);

			DarkButton next = new DarkButton(
					GRID_X + COLUMNS * CELL - 28, this.pageButtonY, 28, 20,
					Component.literal(">"),
					button -> {
						if (this.page + 1 < this.pageCount()) {
							this.page++;
							this.rebuildWidgets();
						}
					},
					false
			);
			next.active = this.page + 1 < this.pageCount();
			this.addRenderableWidget(next);
		}

		DarkButton cancel = new DarkButton(
				(VIRTUAL_WIDTH - 80) / 2, this.cancelY, 80, 24,
				Component.translatable("gui.creat-fonts.cancel"),
				button -> this.onClose(),
				false
		);
		this.addRenderableWidget(cancel);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.uiOffsetX, this.uiOffsetY);
		graphics.pose().scale(this.uiScale, this.uiScale);

		int virtualMouseX = (int) Math.round(this.toVirtualX(mouseX));
		int virtualMouseY = (int) Math.round(this.toVirtualY(mouseY));

		this.drawPanel(graphics);
		graphics.drawString(this.font, Component.translatable("gui.creat-fonts.picker.title"), 14, 14, GuiTheme.TEXT, false);
		graphics.drawString(
				this.font,
				Component.translatable("gui.creat-fonts.picker.subtitle", this.choices.size()),
				14,
				27,
				GuiTheme.MUTED,
				false
		);
		graphics.fill(14, 40, VIRTUAL_WIDTH - 14, 41, GuiTheme.BORDER);

		super.render(graphics, virtualMouseX, virtualMouseY, delta);

		if (this.choices.isEmpty()) {
			graphics.drawCenteredString(
					this.font,
					Component.translatable("gui.creat-fonts.picker.empty"),
					VIRTUAL_WIDTH / 2,
					GRID_Y + ROWS * CELL / 2,
					GuiTheme.MUTED
			);
		} else {
			graphics.drawCenteredString(
					this.font,
					Component.translatable("gui.creat-fonts.picker.page", this.page + 1, this.pageCount()),
					VIRTUAL_WIDTH / 2,
					this.pageLabelY,
					GuiTheme.MUTED
			);
		}

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
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreen(this.parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int pageCount() {
		if (this.choices.isEmpty()) {
			return 1;
		}
		return (this.choices.size() + PAGE_SIZE - 1) / PAGE_SIZE;
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

	private void drawPanel(GuiGraphics graphics) {
		graphics.fill(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, GuiTheme.PANEL);
		graphics.fill(0, 0, VIRTUAL_WIDTH, 1, GuiTheme.BORDER);
		graphics.fill(0, VIRTUAL_HEIGHT - 1, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, GuiTheme.BORDER);
		graphics.fill(0, 0, 1, VIRTUAL_HEIGHT, GuiTheme.BORDER);
		graphics.fill(VIRTUAL_WIDTH - 1, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, GuiTheme.BORDER);
		graphics.fill(0, 0, 3, VIRTUAL_HEIGHT, GuiTheme.ACCENT);
	}

	private final class BlockCell extends AbstractWidget {
		private final BlockChoice choice;

		private BlockCell(BlockChoice choice, int x, int y) {
			super(x, y, SLOT, SLOT, choice.name());
			this.choice = choice;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			int x = this.getX();
			int y = this.getY();
			boolean selected = this.choice.id().equals(BlockPickerScreen.this.currentBlockId);
			int background = selected ? 0xFF3B2C1D : this.isHoveredOrFocused() ? GuiTheme.BUTTON_HOVER : GuiTheme.PANEL_ALT;
			int border = selected ? GuiTheme.ACCENT : this.isHoveredOrFocused() ? GuiTheme.BUTTON_BORDER_HOVER : GuiTheme.BORDER_SOFT;
			graphics.fill(x, y, x + SLOT, y + SLOT, background);
			graphics.fill(x, y, x + SLOT, y + 1, border);
			graphics.fill(x, y + SLOT - 1, x + SLOT, y + SLOT, border);
			graphics.fill(x, y, x + 1, y + SLOT, border);
			graphics.fill(x + SLOT - 1, y, x + SLOT, y + SLOT, border);
			graphics.renderItem(this.choice.stack(), x + 7, y + 7);

			if (this.isHoveredOrFocused()) {
				int realX = (int) Math.round(BlockPickerScreen.this.toRealX(mouseX));
				int realY = (int) Math.round(BlockPickerScreen.this.toRealY(mouseY));
				graphics.setTooltipForNextFrame(BlockPickerScreen.this.font, this.choice.name(), realX, realY);
			}
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			BlockPickerScreen.this.currentBlockId = this.choice.id();
			BlockPickerScreen.this.onSelect.accept(this.choice);
			BlockPickerScreen.this.onClose();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, this.choice.name());
			if (this.choice.id().equals(BlockPickerScreen.this.currentBlockId)) {
				output.add(NarratedElementType.HINT, Component.translatable("gui.creat-fonts.picker.selected"));
			}
		}
	}

	public record BlockChoice(String id, ItemStack stack, Component name) {
		public BlockChoice {
			id = id == null ? "" : id;
			stack = stack == null ? ItemStack.EMPTY : stack;
			name = name == null ? Component.empty() : name;
		}
	}
}
