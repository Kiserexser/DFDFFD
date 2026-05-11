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
import net.minecraft.entity.LivingEntity;
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

public class KillAuraMod implements ModInitializer {

    private enum Mode { OFF, RECORDING, REPLAYING }
    private static Mode mode = Mode.OFF;
    private static List<AttackData> recordedAttacks = new ArrayList<>();
    private static int replayIndex = 0;
    private static int tickCounter = 0;
    private static Entity fakeTarget = null;
    private static int messageCooldown = 0;
    private static int attackCooldown = 0;
    private static boolean wasLeftClickPressed = false;

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
            client.player.sendMessage(Text.literal("§7[§cKA§7] §f" + msg), true);
        }
    }

    private void spawnFakeTarget() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        if (fakeTarget != null) {
            fakeTarget.remove(Entity.RemovalReason.DISCARDED);
            fakeTarget = null;
        }

        net.minecraft.entity.mob.ZombieEntity dummy = new net.minecraft.entity.mob.ZombieEntity(net.minecraft.entity.EntityType.ZOMBIE, client.world);
        dummy.setCustomName(Text.literal("§c§lTRAINING DUMMY"));
        dummy.setCustomNameVisible(true);
        dummy.setInvulnerable(false);
        dummy.setHealth(20.0f);
        dummy.setNoGravity(true);
        dummy.setAiDisabled(true);
        dummy.setSilent(true);
        
        Vec3d pos = client.player.getPos().add(client.player.getRotationVector().multiply(2.5));
        dummy.setPosition(pos.x, pos.y, pos.z);
        
        client.world.addEntity(dummy);
        fakeTarget = dummy;
        sendMessage("§aTraining dummy spawned! Hit it to record/replay.");
    }

    private void removeFakeTarget() {
        if (fakeTarget != null) {
            fakeTarget.remove(Entity.RemovalReason.DISCARDED);
            fakeTarget = null;
            sendMessage("§cTraining dummy removed.");
        }
    }

    private LivingEntity getNearestTarget() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return null;

        // Сначала проверяем фейк-моба
        if (fakeTarget instanceof LivingEntity living && living.isAlive()) {
            double dist = client.player.distanceTo(living);
            if (dist <= 4.5) return living;
        }

        // Ищем ЛЮБЫХ живых существ в радиусе 4 блоков (мобы, животные, игроки)
        Box box = client.player.getBoundingBox().expand(4.0);
        LivingEntity closest = null;
        double closestDist = 4.5;

        for (LivingEntity entity : client.world.getEntitiesByClass(LivingEntity.class, box, e -> {
            if (e == client.player) return false;
            if (!e.isAlive()) return false;
            if (e.isSpectator()) return false;
            if (e instanceof PlayerEntity p && p.isCreative()) return false;
            // Убираем фильтр на игроков — атакуем ВСЕХ
            return true;
        })) {
            double dist = client.player.distanceTo(entity);
            if (dist < closestDist) {
                closestDist = dist;
                closest = entity;
            }
        }
        return closest;
    }

    @Override
    public void onInitialize() {
        KeyBinding recordKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Record", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "KillAura"));
        KeyBinding stopKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Stop", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "KillAura"));
        KeyBinding replayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Replay", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "KillAura"));
        KeyBinding spawnKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Dummy", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "KillAura"));

        loadPattern();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (attackCooldown > 0) attackCooldown--;
            if (messageCooldown > 0) messageCooldown--;

            // Клавиша P — спавн/удаление фейка
            if (spawnKey.wasPressed()) {
                if (fakeTarget == null) spawnFakeTarget();
                else removeFakeTarget();
            }

            // Запись
            if (recordKey.wasPressed() && mode != Mode.RECORDING) {
                mode = Mode.RECORDING;
                recordedAttacks.clear();
                sendMessage("§aRECORDING STARTED. Now hit any entity!");
            }
            if (stopKey.wasPressed() && mode == Mode.RECORDING) {
                mode = Mode.OFF;
                savePattern();
                sendMessage("§eRECORDING STOPPED (" + recordedAttacks.size() + " steps)");
            }

            // Воспроизведение
            if (replayKey.wasPressed()) {
                if (mode == Mode.REPLAYING) {
                    mode = Mode.OFF;
                    sendMessage("§cREPLAY STOPPED");
                } else if (!recordedAttacks.isEmpty()) {
                    mode = Mode.REPLAYING;
                    replayIndex = 0;
                    tickCounter = 0;
                    sendMessage("§aREPLAY STARTED");
                } else {
                    sendMessage("§cNothing recorded. Press Y first.");
                }
            }

            // ЗАПИСЬ АТАК (по нажатию ЛКМ)
            boolean isLeftClickPressed = client.options.attackKey.isPressed();
            
            if (mode == Mode.RECORDING && isLeftClickPressed && !wasLeftClickPressed && attackCooldown == 0) {
                AttackData data = new AttackData();
                data.delayTicks = tickCounter;
                data.yaw = client.player.getYaw();
                data.pitch = client.player.getPitch();
                recordedAttacks.add(data);
                tickCounter = 0;
                attackCooldown = 4;
                
                LivingEntity target = getNearestTarget();
                if (target != null) {
                    client.interactionManager.attackEntity(client.player, target);
                    client.player.swingHand(Hand.MAIN_HAND);
                }
                
                sendMessage("§7✧ Recorded attack #" + recordedAttacks.size());
            }
            wasLeftClickPressed = isLeftClickPressed;
            tickCounter++;

            // ВОСПРОИЗВЕДЕНИЕ
            if (mode == Mode.REPLAYING && replayIndex < recordedAttacks.size()) {
                AttackData data = recordedAttacks.get(replayIndex);
                if (tickCounter >= data.delayTicks) {
                    LivingEntity replayTarget = getNearestTarget();
                    if (replayTarget != null) {
                        client.player.setYaw(data.yaw);
                        client.player.setPitch(data.pitch);
                        client.interactionManager.attackEntity(client.player, replayTarget);
                        client.player.swingHand(Hand.MAIN_HAND);
                    }
                    replayIndex++;
                    tickCounter = 0;
                }
            }

            // Статусная строка
            if (messageCooldown == 0) {
                String status = switch (mode) {
                    case RECORDING -> "§a● REC";
                    case REPLAYING -> "§b● RPL";
                    default -> "§7● OFF";
                };
                String dummyStatus = fakeTarget != null ? "§a✔" : "§c✘";
                client.player.sendMessage(Text.literal("§7[KA] " + status + " §7Dummy:" + dummyStatus + " §7Attacks: " + recordedAttacks.size()), true);
                messageCooldown = 30;
            }
        });
    }

    private void savePattern() {
        try (FileWriter writer = new FileWriter(DATA_FILE)) {
            GSON.toJson(recordedAttacks, writer);
            System.out.println("[KillAura] Saved " + recordedAttacks.size() + " attacks");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadPattern() {
        if (!DATA_FILE.exists()) return;
        try (FileReader reader = new FileReader(DATA_FILE)) {
            Type type = new TypeToken<List<AttackData>>(){}.getType();
            recordedAttacks = GSON.fromJson(reader, type);
            if (recordedAttacks == null) recordedAttacks = new ArrayList<>();
            System.out.println("[KillAura] Loaded " + recordedAttacks.size() + " attacks");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
