package sereneseasons.test;

import java.io.*;
import java.util.*;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.*;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.util.ScreenShotHelper;
import sereneseasons.core.SereneSeasons;
import sereneseasons.api.season.*;
import sereneseasons.init.ModConfig;
import sereneseasons.season.SeasonSavedData;
import sereneseasons.network.SyncSeasonPacket;

/** No TAN types in this class: also runs with TAN entirely absent from the classpath. */
public final class CompatRuntimeChecks {
    private boolean launched,prepared,opened;
    private volatile boolean ready,done;
    private int wait;
    private static int failures,checks;
    public static synchronized void check(boolean pass,String text) {
        checks++;if(!pass)failures++;log((pass?"PASS ":"FAIL ")+text);
    }
    public static synchronized void log(String text) {
        System.out.println("[SS-COMPAT] "+text);
        try(PrintWriter out=new PrintWriter(new FileWriter("compat-checks.log",true))){out.println(text);}
        catch(IOException e){throw new RuntimeException(e);}
    }
    @SubscribeEvent public void client(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getMinecraft();if(done){mc.shutdown();return;}
        try {
            if(!launched && mc.currentScreen instanceof GuiMainMenu) {
                launched=true;log("START "+new Date()+" expectTan="+Boolean.getBoolean("sereneseasons.expectTan"));
                mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.renderDistanceChunks=4;
                WorldSettings settings=new WorldSettings(12345L,WorldSettings.GameType.CREATIVE,false,false,WorldType.FLAT);
                settings.enableCommands();mc.launchIntegratedServer("ss-compat-"+System.currentTimeMillis(),"SS optional TAN checks",settings);return;
            }
            if(!ready || mc.theWorld==null || mc.thePlayer==null)return;
            if(++wait>600)throw new AssertionError("Client synchronization timeout");
            if(SeasonHelper.getSeasonState(mc.theWorld).getSubSeason()!=Season.SubSeason.MID_WINTER)return;
            if(!opened) {
                if(Loader.isModLoaded("toughasnails"))TanRuntimeChecks.client(mc.theWorld);
                CreativeTabs tab=SereneSeasons.creativeTab;
                check(CreativeTabs.creativeTabArray[tab.getTabIndex()]==tab,"creative tab registered in Forge");
                check(tab.getTabIconItem()==SereneSeasons.calendar,"creative tab calendar icon");
                check(SereneSeasons.calendar.getCreativeTab()==tab,"calendar assigned to SS tab");
                check(SereneSeasons.seasonSensor.getCreativeTabToDisplayOn()==tab,"sensor assigned to SS tab");
                List<ItemStack> items=new ArrayList<ItemStack>();tab.displayAllReleventItems(items);
                check(items.size()==2 && items.get(0).getItem()==SereneSeasons.calendar
                    && items.get(1).getItem()==net.minecraft.item.Item.getItemFromBlock(SereneSeasons.seasonSensor),"tab contains calendar and season sensor");
                check(!net.minecraft.util.StatCollector.translateToLocal(tab.getTranslatedTabLabel()).equals(tab.getTranslatedTabLabel()),"creative tab name translated");
                GuiContainerCreative gui=new GuiContainerCreative(mc.thePlayer);mc.displayGuiScreen(gui);
                java.lang.reflect.Method select;
                try {select=GuiContainerCreative.class.getDeclaredMethod("setCurrentCreativeTab",CreativeTabs.class);}
                catch(NoSuchMethodException production){select=GuiContainerCreative.class.getDeclaredMethod("func_147050_b",CreativeTabs.class);}
                select.setAccessible(true);select.invoke(gui,tab);
                java.lang.reflect.Field page=GuiContainerCreative.class.getDeclaredField("tabPage");page.setAccessible(true);page.setInt(gui,tab.getTabPage());
                java.lang.reflect.Field selected=cpw.mods.fml.relauncher.ReflectionHelper.findField(GuiContainerCreative.class,"selectedTabIndex","field_147058_w");selected.setAccessible(true);
                check(selected.getInt(null)==tab.getTabIndex(),"actual creative GUI selects SS tab");
                opened=true;wait=0;return;
            }
            if(wait>=40) {
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"ss-creative-tab.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                log("SCREENSHOT screenshots/ss-creative-tab.png");
                log((failures==0?"ALL COMPAT CHECKS PASSED":"FAILED")+" checks="+checks+" failures="+failures);done=true;
            }
        } catch(Throwable error){check(false,"client exception "+error);error.printStackTrace();done=true;}
    }
    @SubscribeEvent public void server(TickEvent.WorldTickEvent e) {
        World w=e.world;
        if(prepared || done || w.isRemote || w.provider.dimensionId!=0 || e.phase!=TickEvent.Phase.END || w.playerEntities.isEmpty())return;
        prepared=true;
        try {
            check(Loader.isModLoaded("toughasnails")==Boolean.getBoolean("sereneseasons.expectTan"),"optional TAN presence matches test mode");
            if(Boolean.getBoolean("sereneseasons.productionChecks")) {
                check(!Boolean.TRUE.equals(net.minecraft.launchwrapper.Launch.blackboard.get("fml.deobfuscatedEnvironment")),"production obfuscated Minecraft environment");
                String source=SereneSeasons.class.getProtectionDomain().getCodeSource().getLocation().toString();
                check(source.contains("SereneSeasons-1.7.10-1.2.0-legacy.jar"),"SS loaded from release JAR: "+source);
            }
            w.getGameRules().setOrCreateGameRule("doSeasonCycle","false");w.getGameRules().setOrCreateGameRule("doMobSpawning","false");
            w.getGameRules().setOrCreateGameRule("doDaylightCycle","false");w.setWorldTime(6000);
            w.getWorldInfo().setRaining(false);w.getWorldInfo().setThundering(false);
            for(int cx=-2;cx<=2;cx++)for(int cz=-2;cz<=2;cz++)Arrays.fill(w.getChunkFromChunkCoords(cx,cz).getBiomeArray(),(byte)BiomeGenBase.plains.biomeID);
            for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++)w.setBlock(x,63,z,Blocks.grass);
            EntityPlayerMP p=(EntityPlayerMP)w.playerEntities.get(0);p.playerNetServerHandler.setPlayerLocation(0,64,0,180,10);
            if(Loader.isModLoaded("toughasnails"))TanRuntimeChecks.server(w,p);
            else {
                boolean absent=false;try{Class.forName("toughasnails.api.temperature.TemperatureHelper");}catch(ClassNotFoundException expected){absent=true;}
                check(absent,"TAN classes absent; SS initialized independently");
                set(w,Season.SubSeason.MID_WINTER);
                check(sereneseasons.season.SeasonHooks.temperatureAt(w,BiomeGenBase.plains,0,64,0)<0.15F,"standalone seasonal winter temperature works");
            }
            set(w,Season.SubSeason.MID_WINTER);
            SereneSeasons.network.sendTo(new SyncSeasonPacket(w,true),p);ready=true;
        }catch(Throwable error){check(false,"server exception "+error);error.printStackTrace();done=true;}
    }
    public static void set(World w,Season.SubSeason season) {
        SeasonSavedData.get(w).seasonCycleTicks=season.ordinal()*ModConfig.seasons.dayDuration*ModConfig.seasons.subSeasonDuration;
    }
}
