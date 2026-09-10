package me.matl114.versioned.impl;

import static net.minecraft.component.DataComponentTypes.*;

import java.util.Objects;
import javax.annotation.Nullable;
import me.matl114.utils.ItemStackUtils;
import me.matl114.versioned.api.VHideFlag;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.ItemStack;

public enum TooltipHideFlag_v1_21_11 implements VHideFlag {
    HIDE_ALL("All", null),
    HIDE_ADDITIONAL("Additional", LORE),
    HIDE_ENCHANT("Enchantments", ENCHANTMENTS),
    HIDE_ATTRIBUTE("Attributes", ATTRIBUTE_MODIFIERS),
    HIDE_UNBREAKABLE("Unbreakable", UNBREAKABLE),
    HIDE_DESTROYS("Breakable", CAN_BREAK),
    HIDE_PLACED_ON("Placeable", CAN_PLACE_ON),
    HIDE_DYE("Dye", DYED_COLOR),
    HIDE_ARMOR_TRIM("Armor Trim", TRIM),
    HIDE_STORED_ENCHANTS("Enchanted Book", STORED_ENCHANTMENTS);
    String name;

    @Nullable
    ComponentType type;

    TooltipHideFlag_v1_21_11(String displayName, ComponentType<?> type) {
        this.name = displayName;
        this.type = type;
    }

    @Override
    public boolean isHide(ItemStack stack) {
        var component = ItemStackUtils.getInPatch(stack, TOOLTIP_DISPLAY);
        if (component == null) return false;
        if (this.type == null) {
            return component.hideTooltip();
        } else {
            return component.hiddenComponents().contains(this.type);
        }
    }

    @Override
    public void setHideFlag(ItemStack stack, boolean hide) {
        var component = ItemStackUtils.getInPatch(stack, TOOLTIP_DISPLAY);
        if (component == null) component = TooltipDisplayComponent.DEFAULT;
        if (this.type == null) {
            component = new TooltipDisplayComponent(hide, component.hiddenComponents());
        } else {
            component = component.with(this.type, hide);
        }
        if (Objects.equals(component, TooltipDisplayComponent.DEFAULT)) {
            ItemStackUtils.setOrRemoveChange(stack, TOOLTIP_DISPLAY, null);
        } else {
            ItemStackUtils.setOrRemoveChange(stack, TOOLTIP_DISPLAY, component);
        }
    }

    @Override
    public String displayName() {
        return name;
    }
}
