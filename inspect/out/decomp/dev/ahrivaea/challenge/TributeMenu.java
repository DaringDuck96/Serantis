package dev.ahrivaea.challenge;

import java.util.ArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public class TributeMenu extends ChestMenu {
   private final String challengeId;
   private final SimpleContainer offering;

   public TributeMenu(int id, Inventory inventory, SimpleContainer offering, String challengeId) {
      super(MenuType.GENERIC_9x3, id, inventory, offering, 3);
      this.offering = offering;
      this.challengeId = challengeId;
   }

   public void removed(Player player) {
      super.removed(player);
      if (player instanceof ServerPlayer sp) {
         ArrayList stacks = new ArrayList();

         for (int i = 0; i < this.offering.getContainerSize(); i++) {
            ItemStack s = this.offering.removeItemNoUpdate(i);
            if (!s.isEmpty()) {
               stacks.add(s);
            }
         }

         if (!stacks.isEmpty()) {
            Challenges.offer(sp, this.challengeId, stacks);
         }
      }
   }
}
