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
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class KillAuraMod implements ModInitializer {

    private static boolean enabled = false;
    private static int attackCooldown = 0;
    private static int animationTick = 0;
    private static float angle = 0;

    @Override
    public void onInitialize() {
        KeyBinding toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Toggle KillAura (Flying Sword)",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "KillAura"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Включение/выключение по R
            if (toggleKey.wasPressed()) {
                enabled = !enabled;
                String status = enabled ? "§aENABLED" : "§cDISABLED";
                client.player.sendMessage(Text.literal("§7[§c⚔️§7] Flying Sword §f" + status), true);
            }

            if (!enabled) return;

            // Задержка между атаками (0.4 секунды = 8 тиков)
            if (attackCooldown > 0) {
                attackCooldown--;
                return;
            }

            // Поиск целей в радиусе 4 блока
            Box box = client.player.getBoundingBox().expand(4.0);
            List<LivingEntity> targets = client.world.getEntitiesByClass(LivingEntity.class, box, e -> {
                if (e == client.player) return false;
                if (!e.isAlive()) return false;
                if (e.isSpectator()) return false;
                if (e instanceof PlayerEntity p && p.isCreative()) return false;
                return client.player.distanceTo(e) <= 4.0;
            });

            if (!targets.isEmpty()) {
                // Атакуем первого в списке
                LivingEntity target = targets.get(0);
                
                // Анимация поворота к цели
                client.player.lookAt(target);
                
                // Удар мечом
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
                
                // Эффекты летающего меча
                for (int i = 0; i < 5; i++) {
                    double x = client.player.getX() + Math.cos(angle + i) * 2.5;
                    double z = client.player.getZ() + Math.sin(angle + i) * 2.5;
                    client.world.addParticle(ParticleTypes.SWEEP_ATTACK, 
                        x, client.player.getY() + 1.5, z, 0, 0, 0);
                }
                
                // Звук удара
                client.player.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
                
                // Задержка
                attackCooldown = 8;
                
                // Анимация угла
                angle += 0.5;
                
                // Сообщение в чат (по желанию)
                if (attackCooldown == 8) {
                    client.player.sendMessage(Text.literal("§7[§c⚔️§7] §fHit §c" + target.getName().getString()), true);
                }
            }
        });
    }
}
