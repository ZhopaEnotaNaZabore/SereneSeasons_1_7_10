package sereneseasons.core;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.block.Block;
import net.minecraftforge.common.MinecraftForge;
import sereneseasons.init.ModConfig;
import sereneseasons.season.SeasonHandler;
import sereneseasons.item.CalendarItem;
import sereneseasons.block.SeasonSensorBlock;
import sereneseasons.network.SyncSeasonPacket;
import sereneseasons.command.SeasonCommand;

@Mod(modid = SereneSeasons.MOD_ID, name = "Serene Seasons Legacy", version = "1.2.0-legacy", acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "[1.2.0-legacy]", dependencies = "after:toughasnails")
public class SereneSeasons {
    public static final String MOD_ID = "sereneseasons";
    @SidedProxy(clientSide = "sereneseasons.client.ClientProxy", serverSide = "sereneseasons.core.CommonProxy")
    public static CommonProxy proxy;
    public static SimpleNetworkWrapper network;
    public static Item calendar;
    public static Block seasonSensor;
    public static final net.minecraft.creativetab.CreativeTabs creativeTab=new SeasonsCreativeTab();
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        ModConfig.seasons.load(event.getSuggestedConfigurationFile());
        network = NetworkRegistry.INSTANCE.newSimpleChannel(MOD_ID);
        network.registerMessage(SyncSeasonPacket.Handler.class, SyncSeasonPacket.class, 0, Side.CLIENT);
        calendar = new CalendarItem();
        seasonSensor = new SeasonSensorBlock();
        calendar.setCreativeTab(creativeTab);seasonSensor.setCreativeTab(creativeTab);
        sereneseasons.api.SSItems.CALENDAR=calendar;sereneseasons.api.SSBlocks.SEASON_SENSOR=seasonSensor;
        GameRegistry.registerItem(calendar, "calendar");
        GameRegistry.registerBlock(seasonSensor, "season_sensor");
        GameRegistry.registerTileEntity(SeasonSensorBlock.SensorTile.class, "sereneseasons:season_sensor");
        SeasonHandler handler = new SeasonHandler();
        FMLCommonHandler.instance().bus().register(handler);
        MinecraftForge.EVENT_BUS.register(handler);
        proxy.init();
    }
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        GameRegistry.addRecipe(new ItemStack(calendar), "PPP", "PIP", "PPP", 'P', Items.paper, 'I', Items.clock);
        GameRegistry.addRecipe(new ItemStack(seasonSensor), "GGG", "QCQ", "SSS", 'G', Blocks.glass,
            'Q', Items.quartz, 'C', calendar, 'S', new ItemStack(Blocks.stone_slab, 1, 3));
    }
    @Mod.EventHandler public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new SeasonCommand());
    }
    @Mod.EventHandler public void postInit(FMLPostInitializationEvent event) {
        if (Loader.isModLoaded("toughasnails")) sereneseasons.compat.ToughAsNailsCompat.init();
    }
}
