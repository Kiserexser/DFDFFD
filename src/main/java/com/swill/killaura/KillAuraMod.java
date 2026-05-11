package com.swill.killaura;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class KillAuraMod implements ModInitializer {

    private static boolean killAuraEnabled = false;
    private static boolean freezeEnabled = false;
    private static int attackCooldown = 0;
    private static float angle = 0;
    private static Vec3d frozenPosition = null;

    @Override
    public void onInitialize() {
        KeyBinding toggleKillAura = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Toggle KillAura", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "KillAura"));
        KeyBinding toggleFreeze = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Toggle Freeze", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "KillAura"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // ========== FREEZE (ЗАМОРОЗКА) ==========
            if (toggleFreeze.wasPressed()) {
                freezeEnabled = !freezeEnabled;
                if (freezeEnabled) {
                    frozenPosition = client.player.getPos();
                    client.player.setVelocity(0, 0, 0);
                    client.player.sendMessage(Text.literal("§7[§b❄️§7] Freeze §aON"), true);
                } else {
                    frozenPosition = null;
                    client.player.sendMessage(Text.literal("§7[§b❄️§7] Freeze §cOFF"), true);
                }
            }

            if (freezeEnabled && frozenPosition != null) {
                // Замораживаем позицию — не двигаемся, не падаем
                client.player.setPosition(frozenPosition.x, frozenPosition.y, frozenPosition.z);
                client.player.setVelocity(0, 0, 0);
                client.player.setOnGround(true); // Обманываем игру, что на земле
                
                // Отключаем гравитацию и движение
                client.player.getAbilities().flying = true;
                client.player.getAbilities().allowFlying = true;
                client.player.getAbilities().setFlySpeed(0);
            } else if (!freezeEnabled && frozenPosition != null) {
                // Возвращаем нормальные способности
                client.player.getAbilities().flying = false;
                client.player.getAbilities().allowFlying = false;
                client.player.getAbilities().setFlySpeed(0.05f);
                frozenPosition = null;
            }

            // ========== KILLAURA ==========
            if (toggleKillAura.wasPressed()) {
                killAuraEnabled = !killAuraEnabled;
                String status = killAuraEnabled ? "§aON" : "§cOFF";
                client.player.sendMessage(Text.literal("§7[§c⚔️§7] KillAura " + status), true);
            }

            if (!killAuraEnabled) return;

            if (attackCooldown > 0) {
                attackCooldown--;
                return;
            }

            Box box = client.player.getBoundingBox().expand(4.0);
            List<LivingEntity> targets = client.world.getEntitiesByClass(LivingEntity.class, box, e -> {
                if (e == client.player) return false;
                if (!e.isAlive()) return false;
                if (e.isSpectator()) return false;
                if (e instanceof PlayerEntity p && p.isCreative()) return false;
                return client.player.distanceTo(e) <= 4.0;
            });

            if (!targets.isEmpty()) {
                LivingEntity target = targets.get(0);
                
                // Поворот к цели
                Vec3d targetPos = target.getPos();
                double dx = targetPos.x - client.player.getX();
                double dz = targetPos.z - client.player.getZ();
                double dy = targetPos.y - client.player.getEyeY();
                double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance));
                client.player.setYaw(yaw);
                client.player.setPitch(pitch);
                
                // Удар
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
                
                // Эффекты
                for (int i = 0; i < 5; i++) {
                    double x = client.player.getX() + Math.cos(angle + i) * 2.5;
                    double z = client.player.getZ() + Math.sin(angle + i) * 2.5;
                    client.world.addParticle(ParticleTypes.SWEEP_ATTACK, 
                        x, client.player.getY() + 1.0, z, 0, 0, 0);
                }
                
                client.player.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
                attackCooldown = 10;
                angle += 0.5;
            }
        });
    }
}
