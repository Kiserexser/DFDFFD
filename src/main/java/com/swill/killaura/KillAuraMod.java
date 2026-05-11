package com.swill.killaura;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class KillAuraMod implements ClientModInitializer {

    private static boolean enabled = false;
    private static boolean targetInCircle = false;
    private static int radiusIndex = 2;
    private static final int[] RADIUS_CM = {2, 4, 6, 8, 10};
    private static int circleRadiusPx = 20; // 2см = 20px

    @Override
    public void onInitializeClient() {
        KeyBinding toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Circle Aim (R)", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "CircleAim"));
        KeyBinding cycleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Cycle Radius (X)", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, "CircleAim"));

        // Обновление радиуса в пикселях при смене см
        Runnable updateRadius = () -> {
            circleRadiusPx = RADIUS_CM[radiusIndex] * 10;
        };
        updateRadius.run();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            if (toggleKey.wasPressed()) {
                enabled = !enabled;
                client.player.sendMessage(Text.literal("§7[§c◉§7] " + (enabled ? "§aON" : "§cOFF") + " §7Radius: " + RADIUS_CM[radiusIndex] + "cm"), true);
            }
            if (cycleKey.wasPressed() && enabled) {
                radiusIndex = (radiusIndex + 1) % RADIUS_CM.length;
                circleRadiusPx = RADIUS_CM[radiusIndex] * 10;
                client.player.sendMessage(Text.literal("§7[§c◉§7] Radius: §e" + RADIUS_CM[radiusIndex] + "cm"), true);
            }

            targetInCircle = false;

            if (!enabled) return;

            // Проверка: есть ли цель в круге
            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();
            int centerX = width / 2;
            int centerY = height / 2;

            for (Entity entity : client.world.getEntities()) {
                if (entity == client.player) continue;
                if (!(entity instanceof LivingEntity)) continue;
                if (entity instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())) continue;

                Vec3d screenPos = getScreenPosition(client, entity);
                if (screenPos == null) continue;

                double dx = screenPos.x - centerX;
                double dy = screenPos.y - centerY;
                double dist = Math.sqrt(dx * dx + dy * dy);

                if (dist <= circleRadiusPx) {
                    targetInCircle = true;
                    break;
                }
            }
        });

        // Отрисовка круга на экране
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!enabled) return;

            MinecraftClient client = MinecraftClient.getInstance();
            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();
            int centerX = width / 2;
            int centerY = height / 2;

            // Рисуем белый тонкий круг
            drawCircle(drawContext, centerX, centerY, circleRadiusPx);

            // Если цель в круге — лёгкая подсветка (опционально)
            if (targetInCircle) {
                drawCircle(drawContext, centerX, centerY, circleRadiusPx + 1);
            }
        });
    }

    private void drawCircle(DrawContext context, int cx, int cy, int radius) {
        int color = 0xFFFFFFFF; // Белый
        for (int i = 0; i <= 360; i += 5) {
            double angle = Math.toRadians(i);
            int x1 = (int) (cx + radius * Math.cos(angle));
            int y1 = (int) (cy + radius * Math.sin(angle));
            int x2 = (int) (cx + radius * Math.cos(Math.toRadians(i + 5)));
            int y2 = (int) (cy + radius * Math.sin(Math.toRadians(i + 5)));
            context.drawLine(x1, y1, x2, y2, color);
        }
    }

    private Vec3d getScreenPosition(MinecraftClient client, Entity entity) {
        if (client.cameraEntity == null) return null;
        
        Vec3d entityPos = entity.getBoundingBox().getCenter();
        Vec3d cameraPos = client.cameraEntity.getPos();
        Vec3d dir = entityPos.subtract(cameraPos).normalize();
        
        double yaw = Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90;
        double pitch = -Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
        
        double screenX = (yaw / 90) * client.getWindow().getScaledWidth() / 2;
        double screenY = (pitch / 90) * client.getWindow().getScaledHeight() / 2;
        
        return new Vec3d(screenX + client.getWindow().getScaledWidth() / 2, 
                         client.getWindow().getScaledHeight() / 2 - screenY, 0);
    }
}
