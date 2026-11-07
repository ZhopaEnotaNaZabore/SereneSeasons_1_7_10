package sereneseasons.test;

import java.io.*;
import java.util.Random;
import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.world.*;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.BonemealEvent;
import sereneseasons.api.season.*;
import sereneseasons.core.SereneSeasons;
import sereneseasons.init.ModConfig;
import sereneseasons.season.*;

/** Runs only with -PruntimeChecks; creates its own disposable single-player world. */
@Mod(modid="ssruntimechecks", name="Serene Seasons runtime checks", version="1", dependencies="required-after:sereneseasons")
public class RuntimeChecks {
    private boolean launched, prepared;
    private volatile boolean failed;
    private volatile int stage;
    private int ticks, clientWait, captured;
    private int initialTicks;
    private int meltBefore;
    private final boolean[] clientVerified = new boolean[6];
    private final Random growth = new Random(1) { @Override public int nextInt(int bound) { return 0; } };
    private static final Season.SubSeason[] SEASONS = {null, Season.SubSeason.MID_SPRING, Season.SubSeason.MID_SUMMER,
        Season.SubSeason.MID_AUTUMN, Season.SubSeason.MID_WINTER, Season.SubSeason.MID_SUMMER};
    private static synchronized void log(String message) {
        System.out.println("[SS-RUNTIME] " + message);
        try (PrintWriter out = new PrintWriter(new FileWriter("runtime-checks.log",true))) { out.println(message); }
        catch (IOException e) { throw new RuntimeException(e); }
    }
    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        log("PASS " + message);
    }
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if(Boolean.getBoolean("sereneseasons.compatChecks")) {
            FMLCommonHandler.instance().bus().register(new CompatRuntimeChecks());return;
        }
        if(Boolean.getBoolean("sereneseasons.parityChecks")) {
            ParityRuntimeChecks test=new ParityRuntimeChecks();
            FMLCommonHandler.instance().bus().register(test);MinecraftForge.EVENT_BUS.register(test);return;
        }
        if (Boolean.getBoolean("sereneseasons.itemChecks")) {
            FMLCommonHandler.instance().bus().register(new ItemRuntimeChecks()); return;
        }
        FMLCommonHandler.instance().bus().register(this);
        log("START " + new java.util.Date());
    }
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        if(Boolean.getBoolean("sereneseasons.parityChecks"))ParityRuntimeChecks.registerProbe();
    }
    @SubscribeEvent public void client(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed) return;
        Minecraft mc = Minecraft.getMinecraft();
        if(stage==6){mc.shutdown();return;}
        try {
            if (!launched && mc.currentScreen instanceof GuiMainMenu) {
                launched = true;
                mc.gameSettings.pauseOnLostFocus = false;
                mc.gameSettings.renderDistanceChunks = 6;
                WorldSettings settings = new WorldSettings(12345L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT);
                settings.enableCommands();
                mc.launchIntegratedServer("ss-runtime-" + System.currentTimeMillis(), "Serene Seasons runtime verification", settings);
                return;
            }
            int current = stage;
            if (current < 1 || current > 5 || mc.theWorld == null || mc.thePlayer == null) return;
            // Keep automated evidence framing stable when the test window gains focus.
            if (mc.currentScreen != null) return; // Let the user pause or leave the test normally.
            mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=180;
            mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=6;
            if (SeasonHelper.getSeasonState(mc.theWorld).getSubSeason() != SEASONS[current]) { clientWait=0; return; }
            if (mc.theWorld.getBlock(-6,68,0) != Blocks.leaves) return;
            if (!clientVerified[current]) {
                require(Blocks.leaves.colorMultiplier(mc.theWorld,-6,68,0) == SEASONS[current].getBirchColor(), "client birch colour " + SEASONS[current]);
                require(Blocks.leaves.colorMultiplier(mc.theWorld,6,68,0) == net.minecraft.world.ColorizerFoliage.getFoliageColorPine(), "spruce unchanged " + SEASONS[current]);
                ModConfig.seasons.changeBirchColor = false;
                require(Blocks.leaves.colorMultiplier(mc.theWorld,-6,68,0) == net.minecraft.world.ColorizerFoliage.getFoliageColorBirch(), "birch config bypass " + SEASONS[current]);
                ModConfig.seasons.changeBirchColor = true;
                clientVerified[current] = true;
            }
            if (captured != current && ++clientWait > 60) {
                ScreenShotHelper.saveScreenshot(mc.mcDataDir, "ss-runtime-"+current+".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
                log("SCREENSHOT screenshots/ss-runtime-"+current+".png");
                captured=current; clientWait=0;
            }
        } catch (Throwable error) { fail(error); }
    }
    @SubscribeEvent public void server(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if (world.isRemote || world.provider.dimensionId != 0 || event.phase != TickEvent.Phase.START || failed) return;
        try {
            if (world.playerEntities.isEmpty()) return;
            EntityPlayerMP player=(EntityPlayerMP)world.playerEntities.get(0);
            if (!prepared) {
                prepared=true;
                world.getGameRules().setOrCreateGameRule("doMobSpawning","false");
                world.getGameRules().setOrCreateGameRule("doDaylightCycle","true");
                world.setWorldTime(6000);
                for (int cx=-2;cx<=2;cx++) for (int cz=-2;cz<=2;cz++) {
                    Chunk chunk=world.getChunkFromChunkCoords(cx,cz);
                    java.util.Arrays.fill(chunk.getBiomeArray(),(byte)BiomeGenBase.plains.biomeID);
                }
                for (int x=-32;x<32;x++) for (int z=-32;z<32;z++) world.setBlock(x,63,z,Blocks.grass,0,3);
                for (int x=12;x<24;x++) for (int z=-12;z<0;z++) world.setBlock(x,63,z,Blocks.water,0,3);
                tree(world,-6,2); tree(world,0,0); tree(world,6,1);
                player.playerNetServerHandler.setPlayerLocation(0,65,14,180,10);
                ModConfig.seasons.outOfSeasonCropBehavior=1;
                change(world,player,1);
                initialTicks=SeasonSavedData.get(world).seasonCycleTicks;
                crop(world,Blocks.potatoes,true,"spring potato"); crop(world,Blocks.wheat,false,"spring wheat");
                require(world.getBiomeGenForCoords(20,20).getFloatTemperature(20,64,20)>0.15F,"spring temperature above snow threshold");
                return;
            }
            ticks++;
            if (ticks==100) {
                require(SeasonSavedData.get(world).seasonCycleTicks>initialTicks,"season advances with world ticks");
                world.getGameRules().setOrCreateGameRule("doSeasonCycle","false");
                initialTicks=SeasonSavedData.get(world).seasonCycleTicks;
            }
            if (ticks==180) {
                require(SeasonSavedData.get(world).seasonCycleTicks==initialTicks,"doSeasonCycle false freezes season");
                change(world,player,2);
                crop(world,Blocks.wheat,true,"summer wheat"); crop(world,Blocks.potatoes,false,"summer potato");
            }
            if (ticks==360) change(world,player,3);
            if (ticks==540) {
                change(world,player,4);
                crop(world,Blocks.wheat,false,"winter wheat");
                world.setBlock(0,67,8,Blocks.glass);
                crop(world,Blocks.wheat,true,"winter greenhouse wheat");
                world.setBlockToAir(0,67,8);
                world.setBlock(0,63,8,Blocks.farmland,7,3); world.setBlock(0,64,8,Blocks.wheat,0,3);
                BonemealEvent bone = new BonemealEvent(player,world,Blocks.wheat,0,64,8);
                MinecraftForge.EVENT_BUS.post(bone);
                require(bone.isCanceled(),"winter bonemeal blocked");
                require(world.getBiomeGenForCoords(20,20).getFloatTemperature(20,64,20)<0.15F,"winter temperature below snow threshold");
                require(world.func_147478_e(20,64,20,true),"vanilla snow placement accepts winter plains");
                // Let vanilla ramp rain strength and send its normal weather packets to the client.
                MinecraftServer.getServer().getCommandManager().executeCommand(player,"weather rain 600");
                for(int x=-12;x<=12;x++) {
                    world.setBlock(x,63,10,Blocks.farmland,7,3); world.setBlock(x,64,10,Blocks.wheat,0,3);
                    world.setBlock(x,63,12,Blocks.farmland,7,3); world.setBlock(x,64,12,Blocks.wheat,0,3);
                    world.setBlock(x,67,12,Blocks.glass,0,3);
                }
            }
            if (ticks==1140) {
                int snow=0,ice=0;
                for(int x=-32;x<32;x++) for(int z=-32;z<32;z++) {
                    if(world.getBlock(x,64,z)==Blocks.snow_layer) snow++;
                    if(world.getBlock(x,63,z)==Blocks.ice) ice++;
                }
                require(snow>0,"natural winter snowfall: "+snow+" snow columns");
                require(ice>0,"natural winter freezing: "+ice+" ice blocks");
                int openGrowth=0, glassGrowth=0;
                for(int x=-12;x<=12;x++) {
                    if(world.getBlock(x,64,10)==Blocks.wheat && world.getBlockMetadata(x,64,10)>0) openGrowth++;
                    if(world.getBlock(x,64,12)==Blocks.wheat && world.getBlockMetadata(x,64,12)>0) glassGrowth++;
                }
                require(openGrowth==0,"natural random ticks: open winter wheat remains age zero");
                require(glassGrowth>0,"natural random ticks: "+glassGrowth+" greenhouse wheat plants grew");
                change(world,player,5);
                require(!world.func_147478_e(20,64,20,true),"summer disables snow placement");
                // A dense melt fixture makes the bounded random melt sampling observable in a short run.
                for(int x=-20;x<=20;x++) for(int z=-8;z<=28;z++) {
                    if(world.isAirBlock(x,64,z)) world.setBlock(x,64,z,Blocks.snow_layer,0,3);
                }
                meltBefore=0;
                for(int x=-20;x<=20;x++) for(int z=-8;z<=28;z++) if(world.getBlock(x,64,z)==Blocks.snow_layer) meltBefore++;
                MinecraftServer.getServer().getCommandManager().executeCommand(player,"weather clear 600");
            }
            if(ticks==1380) {
                int remaining=0;
                for(int x=-20;x<=20;x++) for(int z=-8;z<=28;z++) if(world.getBlock(x,64,z)==Blocks.snow_layer) remaining++;
                require(remaining<meltBefore,"summer snow melting: "+meltBefore+" -> "+remaining+" snow columns");
                for(int i=1;i<=5;i++) require(clientVerified[i],"client sync and rendered palette stage "+i);
                ModConfig.seasons.outOfSeasonCropBehavior=0;
                log("ALL RUNTIME CHECKS PASSED");
                stage=6;
            }
        } catch(Throwable error) { fail(error); }
    }
    private void change(World w, EntityPlayerMP p, int next) {
        int result=MinecraftServer.getServer().getCommandManager().executeCommand(p,"season set "+SEASONS[next].getSerializedName());
        require(result>0 && SeasonHelper.getSeasonState(w).getSubSeason()==SEASONS[next],"/season set "+SEASONS[next]);
        stage=next; log("STAGE "+next);
    }
    private void crop(World world, net.minecraft.block.Block crop, boolean expected, String label) {
        world.setBlock(0,63,8,Blocks.farmland,7,3);
        world.setBlock(0,64,8,crop,0,3);
        for(int i=0;i<16;i++) crop.updateTick(world,0,64,8,growth);
        int age=world.getBlockMetadata(0,64,8);
        require((age>0)==expected,label+" actual updateTick age="+age);
    }
    private static void tree(World w,int x,int kind) {
        for(int y=64;y<=67;y++) w.setBlock(x,y,0,Blocks.log,kind,3);
        for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) for(int y=67;y<=69;y++)
            if(y>67 || dx!=0 || dz!=0) w.setBlock(x+dx,y,dz,Blocks.leaves,kind|4,3);
    }
    private void fail(Throwable error) { failed=true; log("FAIL "+error); error.printStackTrace(); }
}

