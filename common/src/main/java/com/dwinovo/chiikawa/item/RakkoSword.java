package com.dwinovo.chiikawa.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Rakko's sword, its hilt carved with a face as the top four of the subjugation ranking have theirs. The best blade among
 * the pets', as the top ranker's should be, over Usagi's iron stick. A wild Rakko does not carry one: it is made, an iron
 * sword in a ring of diamonds, see the recipes.
 *
 * <p>In anybody's hand it is a diamond sword's worth. In Rakko's it is more: that pet strikes with the damage and the
 * speed below, whatever else it has on, see {@code AbstractPet#doHurtTarget} and {@code AbstractPet#weaponAttackSpeed}.
 */
public class RakkoSword extends SwordItem {
    /** What Rakko strikes for with this in its hand: hit points of damage before enchantments, 11 hearts and a half. */
    public static final double PET_ATTACK_DAMAGE = 11.0D;
    /** How often Rakko strikes with this, in strikes a second, which is what a sword's own speed is called. */
    public static final double PET_ATTACK_SPEED = 1.6D;

    public RakkoSword() {
        super(Tiers.DIAMOND, 3, -2.4F, new Item.Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.chiikawa.rakko_sword.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
