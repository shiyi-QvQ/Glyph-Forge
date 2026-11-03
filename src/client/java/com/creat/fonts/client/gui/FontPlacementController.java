package com.creat.fonts.client.gui;

import com.creat.fonts.block.FontBlock;
import com.creat.fonts.client.render.FontPreviewState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;



public final class FontPlacementController {
	private static final double REACH = 6.0;

	private static @Nullable FontEditorScreen editor;
	private static BlockPos pos = BlockPos.ZERO;
	private static Direction facing = Direction.NORTH;
	private static BlockPos previousPos = BlockPos.ZERO;
	private static Direction previousFacing = Direction.NORTH;
	private static boolean attachMode;
	private static long cancelGuardUntil;

	private FontPlacementController() {
	}

	public static boolean isActive() {
		return editor != null;
	}

	public static void begin(FontEditorScreen screen, BlockPos startPos, Direction startFacing, boolean attach) {
		if (screen == null) {
			return;
		}
		editor = screen;
		attachMode = attach;
		pos = startPos == null ? BlockPos.ZERO : startPos.immutable();
		facing = normalizeFacing(startFacing);
		previousPos = pos;
		previousFacing = facing;

		Minecraft client = Minecraft.getInstance();
		if (attachMode) {
			SurfaceTarget target = findSurfaceTarget(client);
			if (target != null) {
				pos = target.pos();
				facing = target.facing();
			}
		}

		cancelGuardUntil = 0;
		FontPreviewState.setPosition(pos, facing);

		if (client.screen == screen) {
			client.setScreen(null);
		}
		if (client.player != null) {
			client.player.displayClientMessage(Component.translatable(
					attachMode
							? "message.creat-fonts.placement.attach_start"
							: "message.creat-fonts.placement.start"
			), true);
		}
	}

	public static void tick(Minecraft client) {
		if (editor == null) {
			return;
		}
		if (client.player == null || client.level == null) {
			editor = null;
			FontPreviewState.clear();
			return;
		}
		if (client.screen != null || client.player.isSpectator()) {


			cancel(client);
			return;
		}

		if (attachMode) {
			SurfaceTarget target = findSurfaceTarget(client);
			if (target != null && (!target.pos().equals(pos) || target.facing() != facing)) {
				pos = target.pos();
				facing = target.facing();
				FontPreviewState.setPosition(pos, facing);
			}
			return;
		}

		BlockPos target = findTarget(client);
		if (target != null && !target.equals(pos)) {
			pos = target;
			FontPreviewState.setPosition(pos, facing);
		}
	}

	public static boolean onAttack(Minecraft client, Player player, int clickCount) {
		if (editor == null) {
			return false;
		}
		if (clickCount != 0) {
			confirm(client);
		}
		return true;
	}

	public static InteractionResult onUse(Player player, Level level, InteractionHand hand) {
		if (level == null || !level.isClientSide()) {
			return InteractionResult.PASS;
		}
		long now = level.getGameTime();
		if (now <= cancelGuardUntil) {

			return InteractionResult.FAIL;
		}
		if (editor == null) {
			return InteractionResult.PASS;
		}
		cancelGuardUntil = now + 1;
		cancel(Minecraft.getInstance());
		return InteractionResult.FAIL;
	}



	public static @Nullable BlockPos findTarget(Minecraft client) {
		Player player = client.player;
		Level level = client.level;
		if (player == null || level == null) {
			return null;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 reachEnd = eye.add(look.scale(REACH));
		BlockHitResult hit = level.clip(new ClipContext(
				eye,
				reachEnd,
				ClipContext.Block.OUTLINE,
				ClipContext.Fluid.NONE,
				player
		));
		if (hit.getType() != HitResult.Type.MISS) {
			BlockPos hitPos = hit.getBlockPos();
			if (level.getBlockState(hitPos).canBeReplaced()) {
				return hitPos;
			}
			BlockPos adjacent = hitPos.relative(hit.getDirection());
			if (level.getBlockState(adjacent).canBeReplaced()) {
				return adjacent;
			}
			return null;
		}



		for (double distance = 2.0; distance <= 5.0; distance += 0.25) {
			BlockPos candidate = BlockPos.containing(eye.add(look.scale(distance)));
			if (level.getBlockState(candidate).canBeReplaced()) {
				return candidate;
			}
		}
		return null;
	}



	private static @Nullable SurfaceTarget findSurfaceTarget(Minecraft client) {
		Player player = client.player;
		Level level = client.level;
		if (player == null || level == null) {
			return null;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		BlockHitResult hit = level.clip(new ClipContext(
				eye,
				eye.add(look.scale(REACH)),
				ClipContext.Block.OUTLINE,
				ClipContext.Fluid.NONE,
				player
		));
		if (hit.getType() == HitResult.Type.MISS) {
			return null;
		}

		Direction face = hit.getDirection();
		if (face.getAxis().isVertical()) {

			return null;
		}
		BlockPos hitPos = hit.getBlockPos();
		BlockState state = level.getBlockState(hitPos);
		if (state.canBeReplaced() || state.getBlock() instanceof FontBlock) {
			return null;
		}
		if (!state.isFaceSturdy(level, hitPos, face)) {
			return null;
		}
		BlockPos anchor = hitPos.relative(face);
		if (!level.getBlockState(anchor).canBeReplaced()) {
			return null;
		}
		return new SurfaceTarget(anchor.immutable(), face);
	}

	private record SurfaceTarget(BlockPos pos, Direction facing) {
	}

	private static void confirm(Minecraft client) {
		FontEditorScreen screen = editor;
		if (screen == null) {
			return;
		}
		BlockPos chosenPos = pos;
		Direction chosenFacing = facing;
		editor = null;
		FontPreviewState.setPosition(chosenPos, chosenFacing);
		screen.setPlacement(chosenPos, chosenFacing);
		client.setScreen(screen);
		if (client.player != null) {
			client.player.displayClientMessage(Component.translatable("message.creat-fonts.placement.confirmed"), true);
		}
	}

	private static void cancel(Minecraft client) {
		FontEditorScreen screen = editor;
		if (screen == null) {
			return;
		}
		BlockPos oldPos = previousPos;
		Direction oldFacing = previousFacing;
		editor = null;
		FontPreviewState.setPosition(oldPos, oldFacing);
		screen.setPlacement(oldPos, oldFacing);
		client.setScreen(screen);
		if (client.player != null) {
			client.player.displayClientMessage(Component.translatable("message.creat-fonts.placement.cancelled"), true);
		}
	}

	private static Direction normalizeFacing(Direction direction) {
		return direction == null || direction.getAxis().isVertical() ? Direction.NORTH : direction;
	}
}
