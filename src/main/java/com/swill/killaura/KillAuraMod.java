package com.swill.killaura;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class KillAuraMod implements ModInitializer {

    private static boolean hitboxEnabled = false;
    private static final double HITBOX_MULTIPLIER = 3.0;

    @Override
    public void onInitialize() {
        KeyBinding toggleHitbox = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Toggle Hitbox (3x)", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "Hitbox Mod"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (toggleHitbox.wasPressed()) {
                hitboxEnabled = !hitboxEnabled;
                String status = hitboxEnabled ? "§aON (3x)" : "§cOFF";
                client.player.sendMessage(Text.literal("§7[§c§lH§7] Hitbox " + status), true);
            }

            if (!hitboxEnabled) return;

            // Обработка левого клика (атака)
            if (client.options.attackKey.isPressed()) {
                // Ищем ближайшую цель в радиусе (оригинальный радиус * HITBOX_MULTIPLIER)
                double range = 3.0 * HITBOX_MULTIPLIER;
                Entity target = null;
                double closestDistance = range;

                for (Entity entity : client.world.getEntities()) {
                    if (entity == client.player) continue;
                    if (!(entity instanceof LivingEntity)) continue;
                    if (entity instanceof PlayerEntity p && p.isCreative()) continue;
                    if (entity.isSpectator()) continue;

                    double distance = client.player.distanceTo(entity);
                    if (distance <= closestDistance) {
                        closestDistance = distance;
                        target = entity;
                    }
                }

                if (target != null) {
                    // Имитация удара по реальной цели (сервер видит как обычный удар)
                    client.interactionManager.attackEntity(client.player, target);
                    client.player.swingHand(Hand.MAIN_HAND);
                    
                    // F3 покажет нормальный урон и хиты
                    // Обход: мы атакуем реальную сущность, а не воздух
                }
            }
        });
    }
}
