package com.creat.fonts.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;



class DarkButton extends Button {
	private final boolean accent;

	DarkButton(int x, int y, int width, int height, Component message, Button.OnPress onPress, boolean accent) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.accent = accent;
	}

	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		boolean hovered = this.isHoveredOrFocused();
		int background;
		int border;
		int textColor;
		if (!this.active) {
			background = GuiTheme.BUTTON_DISABLED;
			border = GuiTheme.BUTTON_DISABLED_BORDER;
			textColor = GuiTheme.TEXT_DISABLED;
		} else if (this.accent) {
			background = hovered ? GuiTheme.ACCENT_HOVER : GuiTheme.ACCENT;
			border = hovered ? 0xFFFBE3A0 : GuiTheme.ACCENT_DARK;
			textColor = GuiTheme.ON_ACCENT;
		} else {
			background = hovered ? GuiTheme.BUTTON_HOVER : GuiTheme.BUTTON;
			border = hovered ? GuiTheme.BUTTON_BORDER_HOVER : GuiTheme.BUTTON_BORDER;
			textColor = GuiTheme.TEXT;
		}

		int x = this.getX();
		int y = this.getY();
		int width = this.getWidth();
		int height = this.getHeight();
		graphics.fill(x, y, x + width, y + height, background);
		this.drawBorder(graphics, border);
		if (this.active) {
			graphics.fill(x + 1, y + 1, x + width - 1, y + 2, this.accent ? 0x33FFF6DC : GuiTheme.HIGHLIGHT);
			graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, GuiTheme.SHADOW);
		}
		graphics.drawCenteredString(
				Minecraft.getInstance().font,
				this.getMessage(),
				x + width / 2,
				y + (height - 8) / 2,
				textColor
		);
	}

	private void drawBorder(GuiGraphics graphics, int border) {
		int x = this.getX();
		int y = this.getY();
		int width = this.getWidth();
		int height = this.getHeight();
		graphics.fill(x, y, x + width, y + 1, border);
		graphics.fill(x, y + height - 1, x + width, y + height, border);
		graphics.fill(x, y, x + 1, y + height, border);
		graphics.fill(x + width - 1, y, x + width, y + height, border);
	}
}
