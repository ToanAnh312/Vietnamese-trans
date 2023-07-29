package com.gxlg.librgetter;

import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;

import org.jetbrains.annotations.Nullable;

public class Worker {
    @Nullable
    private static BlockPos block;
    @Nullable
    private static ItemStack defaultAxe;
    @Nullable
    private static TradeOfferList trades;
    @Nullable
    private static VillagerEntity villager;
    private static State state = State.STANDBY;
    public static State getState(){ return state; }
    private static FabricClientCommandSource source;
    private static int counter;
    public static void tick(){

        if(state == State.STANDBY) return;
        if(block == null || villager == null){
            source.sendError(new LiteralText("Block or villager are not specified!"));
            state = State.STANDBY;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if(player == null){
            source.sendError(new LiteralText("InternalError: player == null"));
            state = State.STANDBY;
            return;
        }
        if(!block.isWithinDistance(player.getPos(), 3.4f) || villager.distanceTo(player) > 3.4f){
            source.sendError(new LiteralText("Too far away!"));
            state = State.STANDBY;
            return;
        }

        if(state == State.START){
            counter ++;

            PlayerInventory inventory = player.getInventory();
            if(inventory == null){
                source.sendError(new LiteralText("InternalError: inventory == null"));
                state = State.STANDBY;
                return;
            }
            int slot = -1;

            if(LibrGetter.config.autoTool) {
                float max = -1;
                for (int i = 0; i < inventory.main.size(); i++) {
                    ItemStack stack = inventory.getStack(i);
                    if (stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() < 10)
                        continue;
                    float f = stack.getMiningSpeedMultiplier(Blocks.LECTERN.getDefaultState());
                    int ef = EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, stack);
                    if (stack.getItem() instanceof AxeItem) f += (float) (ef * ef + 1);
                    if (f > max) {
                        max = f;
                        slot = i;
                    }
                }
            } else {
                if(defaultAxe == null || !defaultAxe.isDamageable()){
                    state = State.BREAK;
                    return;
                }
                for(int i = 0; i < inventory.main.size(); i++){
                    ItemStack stack = inventory.getStack(i);
                    if(stack.isItemEqualIgnoreDamage(defaultAxe)){
                        slot = i;
                        break;
                    }
                }
            }
            ClientPlayerInteractionManager manager = client.interactionManager;
            if(manager == null){
                source.sendError(new LiteralText("InternalError: manager == null"));
                state = State.STANDBY;
                return;
            }
            ClientPlayNetworkHandler handler = client.getNetworkHandler();
            if(handler == null){
                source.sendError(new LiteralText("InternalError: handler == null"));
                state = State.STANDBY;
                return;
            }
            if(slot != -1){
                if (PlayerInventory.isValidHotbarIndex(slot))
                    inventory.selectedSlot = slot;
                else
                    manager.pickFromInventory(slot);
                UpdateSelectedSlotC2SPacket packetSelect = new UpdateSelectedSlotC2SPacket(inventory.selectedSlot);
                handler.sendPacket(packetSelect);
            }
            state = State.BREAK;
        } else if(state == State.BREAK){

            ClientWorld world = client.world;
            if(world == null){
                source.sendError(new LiteralText("InternalError: world == null"));
                state = State.STANDBY;
                return;
            }
            BlockState targetBlock = world.getBlockState(block);
            if(targetBlock.isAir()){
                state = State.LOSE;
                return;
            }
            ClientPlayerInteractionManager manager = client.interactionManager;
            if(manager == null){
                source.sendError(new LiteralText("InternalError: manager == null"));
                state = State.STANDBY;
                return;
            }
            manager.updateBlockBreakingProgress(block, Direction.UP);
        } else if(state == State.LOSE){
            if(villager.getVillagerData().getProfession() != VillagerProfession.NONE) return;
            state = State.PLACE;
        } else if(state == State.PLACE){

            PlayerInventory inventory = player.getInventory();
            if(inventory == null){
                source.sendError(new LiteralText("InternalError: inventory == null"));
                state = State.STANDBY;
                return;
            }
            int slot = inventory.getSlotWithStack(new ItemStack(Items.LECTERN));
            if(slot == -1) return;

            ClientPlayerInteractionManager manager = client.interactionManager;
            if(manager == null){
                source.sendError(new LiteralText("InternalError: manager == null"));
                state = State.STANDBY;
                return;
            }
            ClientPlayNetworkHandler handler = client.getNetworkHandler();
            if(handler == null){
                source.sendError(new LiteralText("InternalError: handler == null"));
                state = State.STANDBY;
                return;
            }
            if(PlayerInventory.isValidHotbarIndex(slot))
                inventory.selectedSlot = slot;
            else
                manager.pickFromInventory(slot);
            UpdateSelectedSlotC2SPacket packetSelect = new UpdateSelectedSlotC2SPacket(inventory.selectedSlot);
            handler.sendPacket(packetSelect);

            Vec3d lowBlockPos = new Vec3d(block.getX(), block.getY() - 1, block.getZ());
            BlockHitResult lowBlock = new BlockHitResult(lowBlockPos, Direction.UP, block, false);
            PlayerInteractBlockC2SPacket packetSet = new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, lowBlock);

            handler.sendPacket(packetSet);
            state = State.GET;
        } else if(state == State.GET){
            if(villager.getVillagerData().getProfession() == VillagerProfession.NONE) return;
            if(villager.getVillagerData().getProfession() != VillagerProfession.LIBRARIAN){
                source.sendError(new LiteralText("Villager received other profession!"));
                state = State.STANDBY;
                return;
            }

            ClientPlayNetworkHandler handler = client.getNetworkHandler();
            if(handler == null){
                source.sendError(new LiteralText("InternalError: handler == null"));
                state = State.STANDBY;
                return;
            }
            PlayerInteractEntityC2SPacket packet = PlayerInteractEntityC2SPacket.interact(villager, false, Hand.MAIN_HAND);
            handler.sendPacket(packet);
            trades = null;
            state = State.GETTING;
        } else if(state == State.GETTING){
            if(trades == null) return;

            int trade;
            if(trades.get(0).getSellItem().getItem() == Items.ENCHANTED_BOOK)
                trade = 0;
            else if(trades.get(1).getSellItem().getItem() == Items.ENCHANTED_BOOK)
                trade = 1;
            else
                trade = -1;

            Config.Enchantment enchant = null;
            if(trade != -1){
                NbtCompound tag = trades.get(trade).getSellItem().getTag();
                if(tag == null){
                    source.sendError(new LiteralText("InternalError: tag == null"));
                    state = State.STANDBY;
                    return;
                }
                NbtCompound element = (NbtCompound)tag.getList("StoredEnchantments", 10).get(0);

                NbtElement id = element.get("id");
                NbtElement lvl = element.get("lvl");

                ItemStack f = trades.get(trade).getAdjustedFirstBuyItem();
                ItemStack s = trades.get(trade).getSecondBuyItem();
                if(f.getItem() != Items.EMERALD) f = null;
                if(s.getItem() == Items.EMERALD) f = s;

                if(id == null || lvl == null || f == null){
                    source.sendError(new LiteralText("InternalError: id == null or lvl == null or f == null"));
                    state = State.STANDBY;
                    return;
                }
                enchant = new Config.Enchantment(id.asString(), ((NbtShort) lvl).intValue(), f.getCount());
            }

            source.sendFeedback(new LiteralText("Enchantment offered: " + enchant));
            if(enchant != null){
                for (Config.Enchantment l: LibrGetter.config.goals){
                    if (l.meets(enchant)){
                        source.sendFeedback(new LiteralText("Successfully found " + enchant + " after " + counter + " tries for a price of " + enchant.price + " emeralds!").formatted(Formatting.GREEN));
                        state = State.STANDBY;
                        if(LibrGetter.config.notify){
                            if(client.world == null){
                                source.sendError(new LiteralText("InternalError: world == null"));
                            } else {
                                client.world.playSound(
                                        player, player.getX(), player.getY(), player.getZ(),
                                        SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 10.0F, 0.7F
                                );
                            }
                        }
                        break;
                    }
                }
            }
            if(state != State.STANDBY)
                state = State.START;
        }
    }

    public static void begin(){
        if(state != State.STANDBY){
            source.sendError(new LiteralText("LibrGetter is already running!"));
            return;
        }
        if(block == null){
            source.sendError(new LiteralText("The lectern is not been set!"));
            return;
        }
        if(villager == null){
            source.sendError(new LiteralText("The villager is not been set!"));
            return;
        }
        if(LibrGetter.config.goals.isEmpty()){
            source.sendError(new LiteralText("There are no entries in the goals list!"));
            return;
        }

        if(!LibrGetter.config.autoTool){
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;
            if(player== null){
                source.sendError(new LiteralText("InternalError: player == null"));
                return;
            }
            defaultAxe = player.getMainHandStack();
        }

        source.sendFeedback(new LiteralText("LibrGetter process started").formatted(Formatting.GREEN));
        counter = 0;
        state = State.START;
    }
    public static void add(String name, int level, int price){
        Config.Enchantment newLooking = new Config.Enchantment(name, level, price);
        Config.Enchantment already = null;
        for(Config.Enchantment l: LibrGetter.config.goals){
            if(l.same(newLooking)){
                already = l;
                break;
            }
        }
        if(already != null){
            source.sendFeedback(new LiteralText(already + " max price was changed to " + price).formatted(Formatting.GREEN));
            already.price = price;
        } else {
            LibrGetter.config.goals.add(newLooking);
            source.sendFeedback(new LiteralText("Added " + newLooking + " with max price " + newLooking.price).formatted(Formatting.GREEN));
        }
        LibrGetter.saveConfigs();
    }
    public static void remove(String name, int level){
        Config.Enchantment newLooking = new Config.Enchantment(name, level, 64);
        Config.Enchantment already = null;
        for(Config.Enchantment l: LibrGetter.config.goals){
            if(l.same(newLooking)){
                already = l;
                break;
            }
        }
        if(already == null){
            source.sendError(new LiteralText(newLooking + " is not in the goals list!"));
            return;
        }
        LibrGetter.config.goals.remove(already);
        LibrGetter.saveConfigs();
        source.sendFeedback(new LiteralText("Removed " + newLooking).formatted(Formatting.YELLOW));
    }
    public static void list(){
        MutableText output = new LiteralText("Goals list:");
        for(Config.Enchantment l: LibrGetter.config.goals){
            output = output.append("\n- " + l + " (" + l.price + ") ").append(new LiteralText("(remove)").setStyle(
                Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/librget remove " + l))
            ));

        }
        source.sendFeedback(output);
    }
    public static void clear(){
        LibrGetter.config.goals.clear();
        LibrGetter.saveConfigs();
        source.sendFeedback(new LiteralText("Cleared the goals list").formatted(Formatting.YELLOW));
    }
    public static void stop(){
        if(state == State.STANDBY){
            source.sendError(new LiteralText("LibrGetter isn't running!"));
            return;
        }
        source.sendFeedback(new LiteralText("Successfully stopped the process").formatted(Formatting.YELLOW));
        state = State.STANDBY;
    }

    public static void setBlock(@Nullable BlockPos newBlock){
        block = newBlock;
    }
    public static void setTrades(@Nullable TradeOfferList newTrades){
        trades = newTrades;
    }

    public static void setVillager(@Nullable VillagerEntity newVillager){
        villager = newVillager;
    }

    public static void setSource(FabricClientCommandSource newSource){
        source = newSource;
    }

    public static void noRefresh(){
        source.sendError(new LiteralText("The villager trades can not be updated!"));
        state = State.STANDBY;
    }

    public enum State {
        STANDBY,
        START,
        BREAK,
        LOSE,
        PLACE,
        GET,
        GETTING
    }
}
