package com.creat.fonts.client.gui;

import com.creat.fonts.client.history.FontHistory;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;



public class FontHistoryScreen extends Screen {
	private static final int VIRTUAL_WIDTH = 470;
	private static final int VIRTUAL_HEIGHT = 360;
	private static final int PAGE_SIZE = 7;
	private static final int ROW_HEIGHT = 34;
	private static final int ROW_GAP = 3;
	private static final int LIST_TOP = 48;
	private static final int DELETE_ZONE = 46;
	private static final int LOAD_ZONE = 54;
	private static final long CLEAR_CONFIRM_MS = 3500L;
	private static final DateTimeFormatter TIME_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private final FontEditorScreen editor;
	private float uiScale = 1.0F;
	private float uiOffsetX;
	private float uiOffsetY;
	private int page;
	private long clearArmedUntil;
	private DarkButton clearButton;

	public FontHistoryScreen(FontEditorScreen editor) {
		super(Component.translatable("gui.creat-fonts.history.title"));
		this.editor = editor;
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

		List<FontHistory.Entry> entries = FontHistory.entries();
		int pageCount = this.pageCount(entries);
		this.page = Math.min(this.page, pageCount - 1);

		int start = this.page * PAGE_SIZE;
		int end = Math.min(entries.size(), start + PAGE_SIZE);
		for (int index = start; index < end; index++) {
			int row = index - start;
			this.addRenderableWidget(new HistoryRow(
					entries.get(index),
					14,
					LIST_TOP + row * (ROW_HEIGHT + ROW_GAP),
					VIRTUAL_WIDTH - 28,
					ROW_HEIGHT
			));
		}

		if (pageCount > 1) {
			DarkButton previous = new DarkButton(
					14, VIRTUAL_HEIGHT - 32, 26, 22,
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
					VIRTUAL_WIDTH - 40, VIRTUAL_HEIGHT - 32, 26, 22,
					Component.literal(">"),
					button -> {
						if (this.page + 1 < this.pageCount(FontHistory.entries())) {
							this.page++;
							this.rebuildWidgets();
						}
					},
					false
			);
			next.active = this.page + 1 < pageCount;
			this.addRenderableWidget(next);
		}

		this.clearButton = new DarkButton(
				48, VIRTUAL_HEIGHT - 32, 82, 22,
				Component.translatable("gui.creat-fonts.history.clear"),
				button -> this.clearHistory(),
				false
		);
		this.clearButton.active = !entries.isEmpty();
		this.addRenderableWidget(this.clearButton);

		this.addRenderableWidget(new DarkButton(
				VIRTUAL_WIDTH - 14 - 90, VIRTUAL_HEIGHT - 32, 90, 22,
				Component.translatable("gui.creat-fonts.history.back"),
				button -> this.onClose(),
				true
		));
	}

	private void clearHistory() {
		long now = System.currentTimeMillis();
		if (now > this.clearArmedUntil) {
			this.clearArmedUntil = now + CLEAR_CONFIRM_MS;
			return;
		}
		FontHistory.clear();
		this.clearArmedUntil = 0L;
		this.page = 0;
		this.rebuildWidgets();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.uiOffsetX, this.uiOffsetY);
		graphics.pose().scale(this.uiScale, this.uiScale);

		int virtualMouseX = (int) Math.round(this.toVirtualX(mouseX));
		int virtualMouseY = (int) Math.round(this.toVirtualY(mouseY));

		this.drawPanel(graphics);
		graphics.drawString(this.font, Component.translatable("gui.creat-fonts.history.title"), 14, 14, GuiTheme.TEXT, false);
		graphics.drawString(
				this.font,
				Component.translatable("gui.creat-fonts.history.subtitle"),
				14,
				27,
				GuiTheme.MUTED,
				false
		);
		graphics.fill(14, 42, VIRTUAL_WIDTH - 14, 43, GuiTheme.BORDER);

		if (this.clearButton != null) {
			boolean armed = System.currentTimeMillis() < this.clearArmedUntil;
			this.clearButton.setMessage(Component.translatable(
					armed ? "gui.creat-fonts.history.clear_confirm" : "gui.creat-fonts.history.clear"
			));
		}

		super.render(graphics, virtualMouseX, virtualMouseY, delta);

		List<FontHistory.Entry> entries = FontHistory.entries();
		if (entries.isEmpty()) {
			graphics.drawCenteredString(
					this.font,
					Component.translatable("gui.creat-fonts.history.empty"),
					VIRTUAL_WIDTH / 2,
					LIST_TOP + PAGE_SIZE * (ROW_HEIGHT + ROW_GAP) / 2,
					GuiTheme.MUTED
			);
		} else if (this.pageCount(entries) > 1) {
			graphics.drawCenteredString(
					this.font,
					Component.translatable("gui.creat-fonts.history.page", this.page + 1, this.pageCount(entries)),
					VIRTUAL_WIDTH / 2,
					VIRTUAL_HEIGHT - 26,
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
		if (scrollY != 0.0) {
			List<FontHistory.Entry> entries = FontHistory.entries();
			int target = this.page - (int) Math.signum(scrollY);
			if (target >= 0 && target < this.pageCount(entries)) {
				this.page = target;
				this.rebuildWidgets();
				return true;
			}
		}
		return super.mouseScrolled(this.toVirtualX(mouseX), this.toVirtualY(mouseY), scrollX, scrollY);
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		super.mouseMoved(this.toVirtualX(mouseX), this.toVirtualY(mouseY));
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreen(this.editor);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void loadEntry(FontHistory.Entry entry) {
		if (this.editor != null) {
			this.editor.applyHistoryEntry(entry);
		}
		if (this.minecraft != null) {
			this.minecraft.setScreen(this.editor);
		}
	}

	private void deleteEntry(FontHistory.Entry entry) {
		FontHistory.remove(entry);
		List<FontHistory.Entry> entries = FontHistory.entries();
		int pageCount = this.pageCount(entries);
		if (this.page >= pageCount) {
			this.page = Math.max(0, pageCount - 1);
		}
		this.rebuildWidgets();
	}

	private int pageCount(List<FontHistory.Entry> entries) {
		if (entries.isEmpty()) {
			return 1;
		}
		return (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE;
	}

	private String metaText(FontHistory.Entry entry) {
		Component blockName = blockName(entry.blockId());
		String time = TIME_FORMAT.format(Instant.ofEpochMilli(entry.createdAt()));
		return Component.translatable(
				"gui.creat-fonts.history.meta",
				entry.visibleCharacterCount(),
				blockName,
				time
		).getString();
	}

	private static Component blockName(String blockId) {
		Identifier id = Identifier.tryParse(blockId);
		Block block = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
		if (block == null || block == Blocks.AIR) {
			return Component.literal(blockId);
		}
		return block.getName();
	}

	private double toVirtualX(double mouseX) {
		return (mouseX - this.uiOffsetX) / this.uiScale;
	}

	private double toVirtualY(double mouseY) {
		return (mouseY - this.uiOffsetY) / this.uiScale;
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

	private final class HistoryRow extends AbstractWidget {
		private final FontHistory.Entry entry;

		private HistoryRow(FontHistory.Entry entry, int x, int y, int width, int height) {
			super(x, y, width, height, Component.literal(entry.summary()));
			this.entry = entry;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			int x = this.getX();
			int y = this.getY();
			int width = this.getWidth();
			int height = this.getHeight();
			boolean hovered = this.isHoveredOrFocused();
			graphics.fill(x, y, x + width, y + height, hovered ? GuiTheme.BUTTON_HOVER : GuiTheme.PANEL_ALT);
			graphics.fill(x, y, x + width, y + 1, hovered ? GuiTheme.BUTTON_BORDER_HOVER : GuiTheme.BORDER_SOFT);
			graphics.fill(x, y + height - 1, x + width, y + height, GuiTheme.BORDER_SOFT);

			graphics.drawString(FontHistoryScreen.this.font, this.entry.summary(), x + 8, y + 5, GuiTheme.TEXT, false);
			graphics.drawString(
					FontHistoryScreen.this.font,
					FontHistoryScreen.this.metaText(this.entry),
					x + 8,
					y + 18,
					GuiTheme.MUTED,
					false
			);

			int deleteX = x + width - 8 - DELETE_ZONE;
			int loadX = deleteX - 6 - LOAD_ZONE;
			this.drawRowButton(graphics, loadX, y + 8, LOAD_ZONE, 18, GuiTheme.ACCENT, "gui.creat-fonts.history.load");
			this.drawRowButton(graphics, deleteX, y + 8, DELETE_ZONE, 18, GuiTheme.DANGER, "gui.creat-fonts.history.delete");
		}

		private void drawRowButton(GuiGraphics graphics, int x, int y, int width, int height, int border, String key) {
			graphics.fill(x, y, x + width, y + height, GuiTheme.BUTTON);
			graphics.fill(x, y, x + width, y + 1, border);
			graphics.fill(x, y + height - 1, x + width, y + height, border);
			graphics.fill(x, y, x + 1, y + height, border);
			graphics.fill(x + width - 1, y, x + width, y + height, border);
			graphics.drawCenteredString(
					FontHistoryScreen.this.font,
					Component.translatable(key),
					x + width / 2,
					y + (height - 8) / 2,
					GuiTheme.TEXT
			);
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			double localX = event.x() - this.getX();
			if (localX >= this.getWidth() - 8 - DELETE_ZONE) {
				FontHistoryScreen.this.deleteEntry(this.entry);
			} else {
				FontHistoryScreen.this.loadEntry(this.entry);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, Component.literal(this.entry.summary()));
			output.add(NarratedElementType.HINT, Component.translatable("gui.creat-fonts.history.load"));
		}
	}
}
