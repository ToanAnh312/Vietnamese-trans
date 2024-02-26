package com.gxlg.librgetter.command;

import com.gxlg.librgetter.LibrGetter;
import com.gxlg.librgetter.Worker;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.VillagerProfession;

import java.util.ArrayList;
import java.util.List;

public class LibrGetCommand {

    public static int runRemove(CommandContext<?> context) {
        return enchanter(context, true);
    }

    public static int runAdd(CommandContext<?> context) {
        return enchanter(context, false);
    }

    public static int runList(CommandContext<?> context) {
        Worker.setSource(context.getSource());
        Worker.list();
        return 0;
    }

    public static int runNotify(CommandContext<?> context) {
        boolean toggle = context.getArgument("toggle", Boolean.class);
        LibrGetter.MULTI.sendFeedback(context.getSource(), "Notification config was set to " + toggle);
        LibrGetter.config.notify = toggle;
        LibrGetter.saveConfigs();
        return 0;
    }

    public static int runTool(CommandContext<?> context) {
        boolean toggle = context.getArgument("toggle", Boolean.class);
        LibrGetter.MULTI.sendFeedback(context.getSource(), "AutoTool config was set to " + toggle);
        LibrGetter.config.autoTool = toggle;
        LibrGetter.saveConfigs();
        return 0;
    }

    public static int runActionBar(CommandContext<?> context) {
        boolean toggle = context.getArgument("toggle", Boolean.class);
        LibrGetter.MULTI.sendFeedback(context.getSource(), "ActionBar config was set to " + toggle);
        LibrGetter.config.actionBar = toggle;
        LibrGetter.saveConfigs();
        return 0;
    }

    public static int runLock(CommandContext<?> context) {
        boolean toggle = context.getArgument("toggle", Boolean.class);
        LibrGetter.MULTI.sendFeedback(context.getSource(), "Lock config was set to " + toggle);
        LibrGetter.config.lock = toggle;
        LibrGetter.saveConfigs();
        return 0;
    }

    public static int runRemoveGoal(CommandContext<?> context) {
        boolean toggle = context.getArgument("toggle", Boolean.class);
        LibrGetter.MULTI.sendFeedback(context.getSource(), "RemoveGoal config was set to " + toggle);
        LibrGetter.config.removeGoal = toggle;
        LibrGetter.saveConfigs();
        return 0;
    }

    public static int runCheckUpdate(CommandContext<?> context) {
        boolean toggle = context.getArgument("toggle", Boolean.class);
        LibrGetter.MULTI.sendFeedback(context.getSource(), "CheckUpdate config was set to " + toggle);
        LibrGetter.config.checkUpdate = toggle;
        LibrGetter.saveConfigs();
        return 0;
    }


    public static int runAutostart(CommandContext<?> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            LibrGetter.MULTI.sendError(context.getSource(), "InternalError: player == null");
            return 1;
        }
        ClientWorld world = client.world;
        if (world == null) {
            LibrGetter.MULTI.sendError(context.getSource(), "InternalError: world == null");
            return 1;
        }

        BlockPos lec = null;
        for (int dis = 1; dis < 5; dis++) {
            for (int dx = -dis; dx <= dis; dx++) {
                for (int dy = -dis; dy <= dis; dy++) {
                    for (int dz = -dis; dz <= dis; dz++) {
                        if (dis != Math.abs(dx) && dis != Math.abs(dy) && dis != Math.abs(dz)) continue;

                        BlockPos pos = player.getBlockPos().add(dx, dy, dz);
                        if (world.getBlockState(pos).isOf(Blocks.LECTERN)) {
                            lec = pos;
                            break;
                        }
                    }
                    if (lec != null) break;
                }
                if (lec != null) break;
            }
            if (lec != null) break;
        }
        if (lec == null) {
            LibrGetter.MULTI.sendError(context.getSource(), "Could not find a lectern near you!");
            return 1;
        }
        Iterable<Entity> all = world.getEntities();
        VillagerEntity vi = null;
        float d = -1;
        for (Entity e : all) {
            if (e instanceof VillagerEntity) {
                VillagerEntity v = (VillagerEntity) e;
                if (v.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN) {
                    float dd = v.distanceTo(player);
                    if ((d == -1 || dd < d) && dd < 10) {
                        vi = v;
                        d = dd;
                    }
                }
            }
        }
        if (vi == null) {
            LibrGetter.MULTI.sendError(context, "Could not find a Librarian near you!");
            return 1;
        }

        Worker.setSource(context.getSource());
        Worker.setBlock(lec);
        Worker.setVillager(vi);
        Worker.begin();

        return 0;
    }

    private static int enchanter(CommandContext<?> context, boolean remove) {
        List<Enchantment> list = new ArrayList<>();

        if (!LibrGetter.MULTI.getEnchantments(list, context)) return 1;

        int lvl = -1;
        try {
            lvl = context.getArgument("level", Integer.class);
        } catch (IllegalArgumentException ignored) { }

        int price = 64;
        try {
            price = context.getArgument("maxprice", Integer.class);
        } catch (IllegalArgumentException ignored) {
        }

        for(Enchantment enchantment : list) {
            Identifier id = LibrGetter.MULTI.enchantmentId(enchantment);

            if (lvl > enchantment.getMaxLevel()) {
                LibrGetter.MULTI.sendError(context.getSource(), "Level for " + id + " over the max! Max level: " + enchantment.getMaxLevel());
                continue;
            }
            int level = lvl;
            if(lvl == -1) level = enchantment.getMaxLevel();


            if (!enchantment.isAvailableForEnchantedBookOffer()) {
                LibrGetter.MULTI.sendError(context.getSource(), id + " can not be traded by villagers!");
                continue;
            }

            if (id == null) {
                LibrGetter.MULTI.sendError(context.getSource(), "InternalError: id == null");
                return 1;
            }

            Worker.setSource(context.getSource());
            if (remove)
                Worker.remove(id.toString(), level);
            else
                Worker.add(id.toString(), level, price);
        }

        return 0;
    }

    public static int runClear(CommandContext<?> context) {
        Worker.setSource(context.getSource());
        Worker.clear();
        return 0;
    }

    public static int runStop(CommandContext<?> context) {
        Worker.setSource(context.getSource());
        Worker.stop();
        return 0;
    }

    public static int runStart(CommandContext<?> context) {
        Worker.setSource(context.getSource());
        Worker.begin();
        return 0;
    }

    public static int runSelector(CommandContext<?> context) {

        Worker.setSource(context.getSource());
        if (Worker.getState() != Worker.State.STANDBY) {
            LibrGetter.MULTI.sendError(context.getSource(), "LibrGetter is running!");
            return 1;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) {
            LibrGetter.MULTI.sendError(context.getSource(), "InternalError: world == null");
            return 1;
        }
        ClientPlayerEntity player = client.player;
        if (player == null) {
            LibrGetter.MULTI.sendError(context.getSource(), "InternalError: player == null");
            return 1;
        }
        HitResult hit = client.crosshairTarget;
        if (hit == null) {
            LibrGetter.MULTI.sendError(context.getSource(), "InternalError: hit == null");
            return 1;
        }
        HitResult.Type hitType = hit.getType();
        if (hitType == HitResult.Type.MISS) {
            LibrGetter.MULTI.sendError(context.getSource(), "You are not targeting anything!");
            return 1;
        }

        if (hitType == HitResult.Type.BLOCK) {
            BlockPos blockPos = ((BlockHitResult) hit).getBlockPos();
            if (!world.getBlockState(blockPos).isOf(Blocks.LECTERN)) {
                LibrGetter.MULTI.sendError(context.getSource(), "Block is not a lectern!");
                return 1;
            }

            Worker.setBlock(blockPos);
            LibrGetter.MULTI.sendFeedback(context.getSource(), "Block selected");

        } else if (hitType == HitResult.Type.ENTITY) {
            EntityHitResult entityHitResult = (EntityHitResult) hit;
            Entity entity = entityHitResult.getEntity();
            if (!(entity instanceof VillagerEntity)) {
                LibrGetter.MULTI.sendError(context.getSource(), "Entity is not a villager!");
                return 1;
            }
            VillagerEntity villager = (VillagerEntity) entity;
            if (villager.getVillagerData().getProfession() != VillagerProfession.LIBRARIAN) {
                LibrGetter.MULTI.sendError(context.getSource(), "Villager is not a librarian!");
                return 1;
            }
            LibrGetter.MULTI.sendFeedback(context.getSource(), "Villager selected");
            Worker.setVillager(villager);

        }

        return 0;
    }
}