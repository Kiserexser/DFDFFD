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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class KillAuraMod implements ModInitializer {

    private static boolean hitboxEnabled = false;
    private static boolean outlineEnabled = false;
    private static final double MULTIPLIER = 2.8;

    @Override
    public void onInitialize() {
        KeyBinding hitboxKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Hitbox 2.8x", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "Hitbox"));
        KeyBinding outlineKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "ESP Outline", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "Hitbox"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            if (hitboxKey.wasPressed()) {
                hitboxEnabled = !hitboxEnabled;
                client.player.sendMessage(Text.literal("§7[§cH§7] " + (hitboxEnabled ? "§a2.8x ON" : "§cOFF")), true);
            }
            if (outlineKey.wasPressed()) {
                outlineEnabled = !outlineEnabled;
                client.player.sendMessage(Text.literal("§7[§cL§7] " + (outlineEnabled ? "§aESP ON" : "§cOFF")), true);
            }

            // ===== HITBOX 2.8x (исправленный расчёт) =====
            if (hitboxEnabled) {
                for (Entity e : client.world.getEntities()) {
                    if (e == client.player) continue;
                    if (!(e instanceof LivingEntity)) continue;
                    if (e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())) continue;

                    Box orig = e.getBoundingBox();
                    double width = (orig.maxX - orig.minX) * MULTIPLIER;
                    double height = (orig.maxY - orig.minY) * MULTIPLIER;
                    double cx = (orig.minX + orig.maxX) / 2;
                    double cy = (orig.minY + orig.maxY) / 2;
                    double cz = (orig.minZ + orig.maxZ) / 2;
                    
                    Box newBox = new Box(
                        cx - width / 2,
                        cy - height / 2,
                        cz - width / 2,
                        cx + width / 2,
                        cy + height / 2,
                        cz + width / 2
                    );
                    e.setBoundingBox(newBox);
                }
            }

            // ===== ESP OUTLINE (без setGlowingColor) =====
            if (outlineEnabled) {
                for (Entity e : client.world.getEntities()) {
                    if (e == client.player) continue;
                    if (!(e instanceof LivingEntity)) continue;
                    if (e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())) continue;
                    if (client.player.distanceTo(e) <= 9.0) {
                        e.setGlowing(true);
                    }
                }
            } else {
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity && e.isGlowing()) {
                        e.setGlowing(false);
                    }
                }
            }
        });
    }
}
