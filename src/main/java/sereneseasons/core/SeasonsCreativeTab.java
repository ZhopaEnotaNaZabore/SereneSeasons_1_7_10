package sereneseasons.core;

import java.util.List;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class SeasonsCreativeTab extends CreativeTabs {
    public SeasonsCreativeTab() { super(CreativeTabs.getNextID(), "tabSereneSeasons"); }
    @Override @SideOnly(Side.CLIENT)
    public Item getTabIconItem() { return SereneSeasons.calendar; }
    @Override @SideOnly(Side.CLIENT)
    public void displayAllReleventItems(List items) {
        items.add(new ItemStack(SereneSeasons.calendar));
        items.add(new ItemStack(SereneSeasons.seasonSensor));
    }
}
