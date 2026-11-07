package sereneseasons.item;
import java.util.List;
import net.minecraft.item.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.util.*;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import cpw.mods.fml.relauncher.*;
import sereneseasons.api.season.*;
import sereneseasons.core.SereneSeasons;
import java.util.Locale;

public class CalendarItem extends Item {
    @SideOnly(Side.CLIENT) private IIcon[] icons;
    @SideOnly(Side.CLIENT) private IIcon[] tropicalIcons;
    @SideOnly(Side.CLIENT) private IIcon disabledIcon;
    public CalendarItem() { setUnlocalizedName("sereneseasons.calendar"); setMaxStackSize(1); setCreativeTab(SereneSeasons.creativeTab); }
    @SideOnly(Side.CLIENT) public void registerIcons(IIconRegister r) {
        icons = new IIcon[12];
        for (int i = 0; i < 12; i++) icons[i] = r.registerIcon(String.format(Locale.ROOT, "sereneseasons:calendar_%02d", i));
        tropicalIcons = new IIcon[6];
        for (int i = 0; i < 6; i++) tropicalIcons[i] = r.registerIcon(String.format(Locale.ROOT, "sereneseasons:tropical_calendar_%02d", i));
        disabledIcon = r.registerIcon("sereneseasons:calendar_null");
        itemIcon = icons[0];
    }
    @SideOnly(Side.CLIENT) public IIcon getIconIndex(ItemStack stack) {
        World world = SereneSeasons.proxy.clientWorld();
        if (!SereneSeasons.proxy.enabled(world)) return disabledIcon;
        EntityPlayer player = net.minecraft.client.Minecraft.getMinecraft().thePlayer;
        if (player != null && SeasonHelper.usesTropicalSeasons(world.getBiomeGenForCoords((int)Math.floor(player.posX),(int)Math.floor(player.posZ))))
            return tropicalIcons[SeasonHelper.getSeasonState(world).getTropicalSeason().ordinal()];
        return icons[SeasonHelper.getSeasonState(world).getSubSeason().ordinal()];
    }
    @SideOnly(Side.CLIENT) public IIcon getIcon(ItemStack stack, int pass) { return getIconIndex(stack); }
    public static String description(World world) {
        ISeasonState state = SeasonHelper.getSeasonState(world);
        return StatCollector.translateToLocal("desc.sereneseasons." + state.getSubSeason().name().toLowerCase(Locale.ROOT))
            + " (" + (state.getDay() % (state.getSubSeasonDuration() / state.getDayDuration()) + 1) + ")";
    }
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) player.addChatMessage(new ChatComponentText(description(player)));
        return stack;
    }
    @SideOnly(Side.CLIENT) public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(description(player));
    }
    private static String description(EntityPlayer player) {
        World world = player.worldObj;
        if (!(world.isRemote ? SereneSeasons.proxy.enabled(world) : sereneseasons.init.ModConfig.seasons.enabled(world)))
            return StatCollector.translateToLocal("commands.sereneseasons.setseason.disabled");
        if (!SeasonHelper.usesTropicalSeasons(world.getBiomeGenForCoords((int)Math.floor(player.posX),(int)Math.floor(player.posZ)))) return description(world);
        return StatCollector.translateToLocal("desc.sereneseasons." + SeasonHelper.getSeasonState(world).getTropicalSeason().name().toLowerCase(Locale.ROOT));
    }
}
