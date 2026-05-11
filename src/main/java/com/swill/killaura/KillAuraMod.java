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
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class KillAuraMod implements ModInitializer {

    private enum Mode { OFF, RECORDING, REPLAYING }
    private static Mode mode = Mode.OFF;
    private static List<AttackData> recordedAttacks = new ArrayList<>();
    private static int replayIndex = 0;
    private static int tickCounter = 0;

    private static class AttackData {
        int delayTicks;
        float yaw;
        float pitch;
    }

    private static final File DATA_FILE = new File("config/killaura_pattern.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void onInitialize() {
        KeyBinding recordKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Record Aura", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "KillAura"));
        KeyBinding stopKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Stop", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "KillAura"));
        KeyBinding replayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Replay Aura", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "KillAura"));

        loadPattern();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Управление режимами
            if (recordKey.wasPressed() && mode != Mode.RECORDING) {
                mode = Mode.RECORDING;
                recordedAttacks.clear();
                System.out.println("§a[KillAura] Recording started...");
            }
            if (stopKey.wasPressed() && mode == Mode.RECORDING) {
                mode = Mode.OFF;
                savePattern();
                System.out.println("§e[KillAura] Recording stopped. Pattern saved.");
            }
            if (replayKey.wasPressed()) {
                if (mode == Mode.REPLAYING) {
                    mode = Mode.OFF;
                    System.out.println("§c[KillAura] Replay stopped.");
                } else if (!recordedAttacks.isEmpty()) {
                    mode = Mode.REPLAYING;
                    replayIndex = 0;
                    tickCounter = 0;
                    System.out.println("§a[KillAura] Replay started.");
                }
            }

            // Запись
            if (mode == Mode.RECORDING && client.player != null) {
                AttackData data = new AttackData();
                data.delayTicks = tickCounter;
                data.yaw = client.player.getYaw();
                data.pitch = client.player.getPitch();
                recordedAttacks.add(data);
                tickCounter = 0;
            }
            tickCounter++;

            // Воспроизведение
            if (mode == Mode.REPLAYING && client.player != null && replayIndex < recordedAttacks.size()) {
                AttackData data = recordedAttacks.get(replayIndex);
                if (tickCounter >= data.delayTicks) {
                    // Поиск цели на 3 блока
                    LivingEntity target = client.world.getEntitiesByClass(LivingEntity.class,
                        client.player.getBoundingBox().expand(3.0),
                        e -> e != client.player && e.isAlive() && client.player.distanceTo(e) <= 3.0
                    ).stream().findFirst().orElse(null);

                    if (target != null) {
                        // Повторяем поворот камеры (если нужно)
                        client.player.setYaw(data.yaw);
                        client.player.setPitch(data.pitch);
                        // Атакуем
                        MinecraftClient.getInstance().interactionManager.attackEntity(client.player, target);
                        client.player.swingHand(Hand.MAIN_HAND);
                    }
                    replayIndex++;
                    tickCounter = 0;
                }
            }
        });
    }

    private void savePattern() {
        try (FileWriter writer = new FileWriter(DATA_FILE)) {
            GSON.toJson(recordedAttacks, writer);
        } catch (Exception e) {
            System.err.println("[KillAura] Failed to save pattern: " + e.getMessage());
        }
    }

    private void loadPattern() {
        if (!DATA_FILE.exists()) return;
        try (FileReader reader = new FileReader(DATA_FILE)) {
            Type type = new TypeToken<List<AttackData>>(){}.getType();
            recordedAttacks = GSON.fromJson(reader, type);
            if (recordedAttacks == null) recordedAttacks = new ArrayList<>();
            System.out.println("[KillAura] Loaded " + recordedAttacks.size() + " attack steps.");
        } catch (Exception e) {
            System.err.println("[KillAura] Failed to load pattern: " + e.getMessage());
        }
    }
}
