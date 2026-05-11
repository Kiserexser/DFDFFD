package com.swill.killaura;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

public class KillAuraMod implements ModInitializer {
    
    private static boolean enabled = false;
    private static int delay = 0;
    
    @Override
    public void onInitialize() {
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "KillAura",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "Combat"
        ));
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            if (key.wasPressed()) {
                enabled = !enabled;
                System.out.println("KillAura: " + (enabled ? "ON" : "OFF"));
            }
            
            if (!enabled) return;
            
            if (delay > 0) {
                delay--;
                return;
            }
            
            Box box = client.player.getBoundingBox().expand(3.0);
            LivingEntity target = client.world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != client.player && e.isAlive() && client.player.distanceTo(e) <= 3.0
            ).stream().findFirst().orElse(null);
            
            if (target != null) {
                MinecraftClient.getInstance().interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
                delay = 5 + (int)(Math.random() * 3);
            }
        });
    }
}
