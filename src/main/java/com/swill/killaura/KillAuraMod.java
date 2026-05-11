package com.swill.killaura;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KillAuraMod implements ModInitializer {

    private enum Mode { OFF, RECORDING, REPLAYING }
    private static Mode mode = Mode.OFF;
    private static List<AttackData> recordedAttacks = new ArrayList<>();
    private static int replayIndex = 0;
    private static int tickCounter = 0;
    private static Entity fakeTarget = null;
    private static int messageCooldown = 0;

    private static class AttackData {
        int delayTicks;
        float yaw;
        float pitch;
    }

    private static final File DATA_FILE = new File("config/killaura_pattern.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private void sendMessage(String msg) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§7[§cKillAura§7] §f" + msg), true);
        }
    }

    private void spawnFakePlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        if (fakeTarget != null) {
            fakeTarget.remove(Entity.RemovalReason.DISCARDED);
            fakeTarget = null;
        }

        ZombieEntity dummy = new ZombieEntity(EntityType.ZOMBIE, client.world);
        dummy.setInvulnerable(false);
        dummy.setHealth(20.0f);
        dummy.setCustomName(Text.literal("§c§lTRAINING DUMMY"));
        dummy.setCustomNameVisible(true);
        dummy.setNoGravity(true);
        dummy.setAiDisabled(true);
        dummy.setSilent(true);
        
        Vec3d pos = client.player.getPos().add(client.player.getRotationVector().multiply(2.5));
        dummy.setPosition(pos.x, pos.y, pos.z);
        
        client.world.addEntity(dummy);
        fakeTarget = dummy;
        sendMessage("§aTraining dummy spawned! You can hit it and record/replay.");
    }

    private void removeFakePlayer() {
        if (fakeTarget != null) {
            fakeTarget.remove(Entity.RemovalReason.DISCARDED);
            fakeTarget = null;
            sendMessage("§cTraining dummy removed.");
        }
    }

    private LivingEntity getTarget() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return null;

        if (fakeTarget instanceof LivingEntity living && living.isAlive() && client.player.distanceTo(living) <= 4.0) {
            return living;
        }

        Box box = client.player.getBoundingBox().expand(3.0);
        return client.world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != client.player && e.isAlive() && !e.isSpectator() && client.player.distanceTo(e) <= 3.0
        ).stream().findFirst().orElse(null);
    }

    @Override
    public void onInitialize() {
        KeyBinding recordKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Record Aura", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "KillAura"));
        KeyBinding stopKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Stop", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "KillAura"));
        KeyBinding replayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Replay Aura", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "KillAura"));
        KeyBinding spawnKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Spawn Dummy", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "KillAura"));

        loadPattern();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (messageCooldown > 0) messageCooldown--;

            if (spawnKey.wasPressed()) {
                if (fakeTarget == null) spawnFakePlayer();
                else removeFakePlayer();
            }

            if (recordKey.wasPressed() && mode != Mode.RECORDING) {
                mode = Mode.RECORDING;
                recordedAttacks.clear();
                sendMessage("§a[RECORDING STARTED] Hit the dummy or players!");
            }
            if (stopKey.wasPressed() && mode == Mode.RECORDING) {
                mode = Mode.OFF;
                savePattern();
                sendMessage("§e[RECORDING STOPPED] Saved " + recordedAttacks.size() + " steps.");
            }
            if (replayKey.wasPressed()) {
                if (mode == Mode.REPLAYING) {
                    mode = Mode.OFF;
                    sendMessage("§c[REPLAY STOPPED]");
                } else if (!recordedAttacks.isEmpty()) {
                    mode = Mode.REPLAYING;
                    replayIndex = 0;
                    tickCounter = 0;
                    sendMessage("§a[REPLAY STARTED]");
                } else {
                    sendMessage("§cNo recorded pattern. Press Y first.");
                }
            }

            if (mode == Mode.RECORDING && client.player != null && getTarget() != null) {
                AttackData data = new AttackData();
                data.delayTicks = tickCounter;
                data.yaw = client.player.getYaw();
                data.pitch = client.player.getPitch();
                recordedAttacks.add(data);
                tickCounter = 0;
                if (messageCooldown == 0 && recordedAttacks.size() % 10 == 0) {
                    sendMessage("§7Recorded " + recordedAttacks.size() + " attacks...");
                    messageCooldown = 20;
                }
            }
            tickCounter++;

            if (mode == Mode.REPLAYING && client.player != null && replayIndex < recordedAttacks.size()) {
                AttackData data = recordedAttacks.get(replayIndex);
                if (tickCounter >= data.delayTicks) {
                    LivingEntity target = getTarget();
                    if (target != null) {
                        client.player.setYaw(data.yaw);
                        client.player.setPitch(data.pitch);
                        client.interactionManager.attackEntity(client.player, target);
                        client.player.swingHand(Hand.MAIN_HAND);
                    }
                    replayIndex++;
                    tickCounter = 0;
                }
            }

            if (messageCooldown == 0) {
                String status = "";
                if (mode == Mode.RECORDING) status = "§a● RECORDING";
                else if (mode == Mode.REPLAYING) status = "§b● REPLAYING";
                else status = "§7● OFF";
                client.player.sendMessage(Text.literal("§7[§cKA§7] " + status + " §7| Dummy: " + (fakeTarget != null ? "§a✔" : "§c✘")), true);
                messageCooldown = 30;
            }
        });
    }

    private void savePattern() {
        try (FileWriter writer = new FileWriter(DATA_FILE)) {
            GSON.toJson(recordedAttacks, writer);
        } catch (Exception e) {
            System.err.println("[KillAura] Failed to save: " + e.getMessage());
        }
    }

    private void loadPattern() {
        if (!DATA_FILE.exists()) return;
        try (FileReader reader = new FileReader(DATA_FILE)) {
            Type type = new TypeToken<List<AttackData>>(){}.getType();
            recordedAttacks = GSON.fromJson(reader, type);
            if (recordedAttacks == null) recordedAttacks = new ArrayList<>();
            System.out.println("[KillAura] Loaded " + recordedAttacks.size() + " steps.");
        } catch (Exception e) {
            System.err.println("[KillAura] Failed to load: " + e.getMessage());
        }
    }
}
