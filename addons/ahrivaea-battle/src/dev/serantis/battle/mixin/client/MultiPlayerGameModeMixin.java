package dev.serantis.battle.mixin.client;

import com.google.gson.JsonObject;
import dev.serantis.battle.client.TradeClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Right-clicking another player offers to trade creatures with them. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void serantis$trade(Player player, Entity target, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.MAIN_HAND && !player.isShiftKeyDown() && dev.serantis.battle.Tutor.isTutor(target)) {
            dev.serantis.battle.client.TutorScreen.open(target.getId());
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (hand == InteractionHand.MAIN_HAND && dev.serantis.battle.client.RaidScreen.isRaider(target)) {   // goblins raiding your base
            TradeClient.send("serantis_raid_open", "entity", target.getId());
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (hand == InteractionHand.MAIN_HAND && !player.isShiftKeyDown() && dev.serantis.battle.Honing.isSmith(target)
                && dev.serantis.battle.Honing.improvable(player.getMainHandItem())) {      // a Smith, holding a weapon: improve it
            dev.serantis.battle.client.SmithScreen.open(target.getId());
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (hand == InteractionHand.MAIN_HAND && dev.serantis.battle.Riddles.isRiddleDragon(target)) {   // an End dragon: its riddle
            JsonObject o = new JsonObject();
            o.addProperty("kind", "serantis_riddle_ask");
            o.addProperty("entity", target.getId());
            dev.ahrivaea.client.battle.BattleClient.send(o);
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        String master = dev.serantis.battle.Masters.influenceOf(target);
        if (master != null && !player.isShiftKeyDown()) {           // talk to a Creature Master (see DialogueScreen)
            if (hand == InteractionHand.MAIN_HAND && net.minecraft.client.Minecraft.getInstance().gui.screen() == null)
                dev.serantis.battle.client.DialogueScreen.open(target, master);
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (hand == InteractionHand.MAIN_HAND && dev.serantis.battle.Wanderer.isWanderer(target)) {
            dev.serantis.battle.client.WandererScreen.open(target.getId());
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (hand == InteractionHand.MAIN_HAND && dev.serantis.battle.Market.isBlackMarket(target)) {
            dev.serantis.battle.client.BlackMarketScreen.open(target.getId());
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (!player.isShiftKeyDown() && target instanceof dev.ahrivaea.creature.CreatureEntity c && !c.isTame()
                && dev.ahrivaea.villager.Villagers.SPECIES.equals(c.getSpeciesId())) {   // a villager: talk (and maybe fight), see VillagerTalk
            if (hand == InteractionHand.MAIN_HAND && net.minecraft.client.Minecraft.getInstance().gui.screen() == null) {
                JsonObject o = new JsonObject();
                o.addProperty("kind", "serantis_villager_talk");
                o.addProperty("entity", target.getId());
                dev.ahrivaea.client.battle.BattleClient.send(o);
            }
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (hand != InteractionHand.MAIN_HAND || !(target instanceof Player other) || other == player || player.isSpectator()) return;
        if (TradeClient.askToTrade(other)) cir.setReturnValue(InteractionResult.SUCCESS);
    }

    // Placing a block on a tower's protected land: stopped here, with a message (see TowerZones), instead of the block
    // appearing and then vanishing when the server refuses it.
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void serantis$towerLand(net.minecraft.client.player.LocalPlayer player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit,
                                    CallbackInfoReturnable<InteractionResult> cir) {
        if (!(player.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.BlockItem) || player.isCreative()) return;
        var st = dev.serantis.battle.client.TradeClient.state();
        if (st == null || !st.has("serantis_tower_zones")) return;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        net.minecraft.core.BlockPos at = level.getBlockState(hit.getBlockPos()).canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
        if (hit.getBlockPos().equals(at) && !level.getBlockState(at).canBeReplaced()) return;
        for (var el : st.getAsJsonArray("serantis_tower_zones")) {
            var z = el.getAsJsonObject();
            if (at.getX() < z.get("x0").getAsInt() || at.getX() > z.get("x1").getAsInt() || at.getZ() < z.get("z0").getAsInt()
                    || at.getZ() > z.get("z1").getAsInt() || at.getY() < z.get("y0").getAsInt()) continue;
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("You can't place that here: " + z.get("name").getAsString()
                    + " and the land around it are protected until it's rebuilt (then it's its claimer's).").withStyle(net.minecraft.ChatFormatting.GOLD));
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
    }

    // Right-clicking an enchanting table with a Magic Staff that has no spell yet: choose its spell (see Staffs).
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void serantis$linkStaff(net.minecraft.client.player.LocalPlayer player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit,
                                    CallbackInfoReturnable<InteractionResult> cir) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null || player.isShiftKeyDown()) return;
        if (level.getBlockState(hit.getBlockPos()).is(net.minecraft.world.level.block.Blocks.BELL)) {   // a bell: the bounty board (sneak to ring it)
            if (hand == InteractionHand.MAIN_HAND) dev.serantis.battle.client.BountyScreen.open();
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (!level.getBlockState(hit.getBlockPos()).is(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE)) return;
        boolean blank = false;
        for (var stack : java.util.List.of(player.getMainHandItem(), player.getOffhandItem()))
            if (dev.serantis.battle.Staffs.isStaff(stack) && dev.serantis.battle.Staffs.spellOf(stack) == null) blank = true;
        if (!blank) return;
        if (hand == InteractionHand.MAIN_HAND) dev.serantis.battle.client.StaffScreen.open(hit.getBlockPos());
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
