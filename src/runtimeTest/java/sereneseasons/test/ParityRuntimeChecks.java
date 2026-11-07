package sereneseasons.test;

import java.io.*;
import java.util.*;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.world.*;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.terraingen.BiomeEvent;
import sereneseasons.api.season.*;
import sereneseasons.core.SereneSeasons;
import sereneseasons.init.ModConfig;
import sereneseasons.network.SyncSeasonPacket;
import sereneseasons.season.*;

public final class ParityRuntimeChecks {
    private static Block probe;
    private volatile boolean done;
    private volatile int stage=-1;
    private volatile int clientStage=-1;
    private boolean launched,prepared;
    private int ticks,failures,standardEvents,tropicalEvents,clientDelay;
    private ModConfig original;
    public static void registerProbe() {
        probe=new Block(Material.plants) {
            {setTickRandomly(true);setBlockName("parity_probe");setBlockTextureName("minecraft:wheat_stage_0");}
            @Override public void updateTick(World w,int x,int y,int z,Random r) {
                w.setBlockMetadataWithNotify(x,y,z,Math.min(15,w.getBlockMetadata(x,y,z)+1),3);
            }
        };
        GameRegistry.registerBlock(probe,"parity_probe");
        FertilityRegistry.registerCrop("ssruntimechecks:parity_probe",2);
        FertilityRegistry.registerSeed("ssruntimechecks:parity_probe",2);
    }
    private static synchronized void log(String text) {
        System.out.println("[SS-PARITY] "+text);
        try(PrintWriter out=new PrintWriter(new FileWriter("parity-checks.log",true))){out.println(text);}
        catch(IOException e){throw new RuntimeException(e);}
    }
    private synchronized void check(boolean pass,String text){if(!pass)failures++;log((pass?"PASS ":"FAIL ")+text);}
    @SubscribeEvent public void standard(SeasonChangedEvent.Standard e){standardEvents++;}
    @SubscribeEvent public void tropical(SeasonChangedEvent.Tropical e){tropicalEvents++;}
    @SubscribeEvent public void client(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getMinecraft();if(done){mc.shutdown();return;}
        try {
            if(!launched && mc.currentScreen instanceof GuiMainMenu) {
                launched=true;log("START "+new Date());mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.renderDistanceChunks=4;
                WorldSettings s=new WorldSettings(12345L,WorldSettings.GameType.CREATIVE,false,false,WorldType.FLAT);s.enableCommands();
                mc.launchIntegratedServer("ss-parity-"+System.currentTimeMillis(),"Serene Seasons parity checks",s);return;
            }
            int current=stage;
            if(current<0 || clientStage==current || mc.theWorld==null || mc.thePlayer==null)return;
            Season.SubSeason expected=current==0?Season.SubSeason.LATE_WINTER:current==1?Season.SubSeason.LATE_SUMMER:current==2?Season.SubSeason.LATE_SUMMER:Season.SubSeason.MID_WINTER;
            if(SeasonHelper.getSeasonState(mc.theWorld).getSubSeason()!=expected){clientDelay=0;return;}
            if(++clientDelay<20)return;
            ModConfig rules=SereneSeasons.proxy.rules();
            check(rules!=ModConfig.seasons,"independent client rules snapshot stage "+current);
            check(FertilityRegistry.seedMask(rules,new ItemStack(probe))==2,"custom seed rule synced stage "+current);
            if(current<2) {
                boolean wet=current==0;
                check(BiomeGenBase.desert.canSpawnLightningBolt()==wet,"client tropical precipitation "+(wet?"wet":"dry"));
                check(BiomeGenBase.jungle.canSpawnLightningBolt()==wet,"client jungle precipitation "+(wet?"wet":"dry"));
                BiomeEvent.GetGrassColor black=new BiomeEvent.GetGrassColor(BiomeGenBase.ocean,0x509040);
                MinecraftForge.EVENT_BUS.post(black);check(black.newColor==0x509040,"blacklisted biome colour unchanged");
                List<String> tooltip=new ArrayList<String>();
                MinecraftForge.EVENT_BUS.post(new ItemTooltipEvent(new ItemStack(Items.wheat_seeds),mc.thePlayer,tooltip,false));
                check(tooltip.size()==3,"wheat seed fertile-season tooltip (heading plus summer/autumn)");
                tooltip.clear();MinecraftForge.EVENT_BUS.post(new ItemTooltipEvent(new ItemStack(probe),mc.thePlayer,tooltip,false));
                check(tooltip.size()==2,"modded seed tooltip");
                SeasonColorRegistry.Override override=(type,original,seasonal,result,biome,state)->0x123456;
                SeasonColorRegistry.register(override);
                BiomeEvent.GetFoliageColor foliage=new BiomeEvent.GetFoliageColor(BiomeGenBase.plains,0x509040);
                MinecraftForge.EVENT_BUS.post(foliage);check(foliage.newColor==0x123456,"colour override API");
                SeasonColorRegistry.unregister(override);
                int plain=colour(BiomeGenBase.plains),swamp=colour(BiomeGenBase.swampland);
                check(current==1 || plain!=swamp,"swamp reduced colour change");
            } else if(current==3) {
                check(Math.abs(BiomeGenBase.plains.getFloatTemperature(0,64,0)-0.38F)<0.001F,"server temperature override applied by client");
                check(rules.undergroundFertilityLevel==37,"non-default underground level synced");
            }
            clientStage=current;clientDelay=0;
        }catch(Throwable error){check(false,"client exception "+error);error.printStackTrace();done=true;}
    }
    private int colour(BiomeGenBase biome){BiomeEvent.GetGrassColor e=new BiomeEvent.GetGrassColor(biome,0x509040);MinecraftForge.EVENT_BUS.post(e);return e.newColor;}
    @SubscribeEvent public void server(TickEvent.WorldTickEvent e) {
        World w=e.world;
        if(done || w.isRemote || w.provider.dimensionId!=0 || e.phase!=TickEvent.Phase.START || w.playerEntities.isEmpty())return;
        try {
            EntityPlayerMP player=(EntityPlayerMP)w.playerEntities.get(0);
            ModConfig c=ModConfig.seasons;
            if(!prepared) {
                prepared=true;original=ModConfig.readRules(c.writeRules());
                w.getGameRules().setOrCreateGameRule("doSeasonCycle","false");w.getGameRules().setOrCreateGameRule("doMobSpawning","false");w.setWorldTime(6000);
                for(int x=-8;x<=24;x++)for(int z=-8;z<=24;z++)w.setBlock(x,63,z,Blocks.grass);
                player.playerNetServerHandler.setPlayerLocation(0,65,5,180,10);
                biome(w,0,0,BiomeGenBase.plains);c.outOfSeasonCropBehavior=1;
                set(w,Season.SubSeason.MID_WINTER);
                check(standardEvents>0 && tropicalEvents>0,"standard and tropical season events");
                check(Math.abs(SeasonHooks.temperatureAt(w,BiomeGenBase.plains,0,64,0))<0.001F,"winter plains temperature adjustment");
                check(SeasonHooks.temperatureAt(w,BiomeGenBase.ocean,0,64,0)==BiomeGenBase.ocean.temperature,"blacklisted ocean temperature unchanged");
                check(SeasonHooks.temperatureAt(w,BiomeGenBase.desert,0,64,0)==BiomeGenBase.desert.temperature,"tropical temperature unchanged");
                crop(w,Blocks.potatoes,64);check(!FertilityRegistry.isFertile(w,0,64,0),"winter surface potato infertile");
                crop(w,Blocks.potatoes,40);w.setBlock(0,43,0,Blocks.stone);check(FertilityRegistry.isFertile(w,0,40,0),"underground fertility below 48");
                crop(w,Blocks.potatoes,48);w.setBlock(0,51,0,Blocks.stone);check(!FertilityRegistry.isFertile(w,0,48,0),"underground boundary is exclusive");
                w.setBlockToAir(0,51,0);w.setBlockToAir(0,43,0);
                biome(w,0,0,BiomeGenBase.jungle);crop(w,Blocks.wheat,64);
                check(FertilityRegistry.isFertile(w,0,64,0),"summer crop fertile in tropical winter");
                crop(w,Blocks.potatoes,64);check(!FertilityRegistry.isFertile(w,0,64,0),"non-summer crop infertile in tropics");
                biome(w,0,0,BiomeGenBase.ocean);check(FertilityRegistry.isFertile(w,0,64,0),"blacklisted biome bypasses fertility");
                biome(w,0,0,BiomeGenBase.plains);set(w,Season.SubSeason.MID_SPRING);
                c.infertileBiomes=new int[]{BiomeGenBase.plains.biomeID};check(!FertilityRegistry.isFertile(w,0,64,0),"infertile biome overrides fertile season");c.infertileBiomes=new int[0];
                biome(w,0,0,BiomeGenBase.icePlains);check(!FertilityRegistry.isFertile(w,0,64,0),"cold biome uses winter fertility in spring");biome(w,0,0,BiomeGenBase.plains);
                set(w,Season.SubSeason.MID_WINTER);crop(w,Blocks.wheat,64);
                c.greenhouseGlass=new String[]{"minecraft:diamond_block"};w.setBlock(0,80,0,Blocks.diamond_block);
                check(SeasonHooks.canGrow(w,0,64,0),"custom greenhouse block at +16");w.setBlockToAir(0,80,0);w.setBlock(0,81,0,Blocks.diamond_block);
                check(!SeasonHooks.canGrow(w,0,64,0),"greenhouse range excludes +17");w.setBlockToAir(0,81,0);c.greenhouseGlass=original.greenhouseGlass;
                c.outOfSeasonCropBehavior=0;int grows=0;
                for(int i=0;i<600;i++)if(SeasonHooks.canGrow(w,0,64,0))grows++;
                check(grows>40 && grows<170,"slow growth probability: "+grows+"/600");
                c.outOfSeasonCropBehavior=2;SeasonHooks.canGrow(w,0,64,0);check(w.isAirBlock(0,64,0),"out-of-season destruction mode");
                w.setBlock(0,64,0,Blocks.sapling,2,3);SeasonHooks.canGrow(w,0,64,0);check(w.getBlock(0,64,0)==Blocks.sapling,"protected crop is not destroyed");
                c.outOfSeasonCropBehavior=1;crop(w,Blocks.wheat,64);
                player.capabilities.isCreativeMode=false;player.inventory.setInventorySlotContents(player.inventory.currentItem,new ItemStack(Items.dye,2,15));
                BonemealEvent bone=new BonemealEvent(player,w,Blocks.wheat,0,64,0);MinecraftForge.EVENT_BUS.post(bone);
                check(bone.isCanceled() && player.getCurrentEquippedItem().stackSize==1,"failed bonemeal consumes one item in survival");player.capabilities.isCreativeMode=true;
                w.setBlock(4,64,4,probe,0,3);w.scheduleBlockUpdate(4,64,4,probe,1);
                SeasonProperties weather=c.properties[10];int min=weather.minRain,max=weather.maxRain;
                weather.minRain=weather.maxRain=200;w.getWorldInfo().setRaining(false);w.getWorldInfo().setRainTime(99999);SeasonHooks.updateWeather(w);
                check(w.getWorldInfo().getRainTime()==200,"custom seasonal rain interval");
                weather.minRain=weather.maxRain=-1;w.getWorldInfo().setRaining(true);SeasonHooks.updateWeather(w);check(!w.isRaining(),"disable rain in season");
                weather.minRain=min;weather.maxRain=max;
                w.getWorldInfo().setThundering(true);SeasonHooks.updateWeather(w);check(!w.isThundering(),"disabled winter thunderstorms");
                set(w,Season.SubSeason.LATE_WINTER);
                check(SeasonHelper.getSeasonState(w).getTropicalSeason()==Season.TropicalSeason.MID_WET,"wet fixture uses MID_WET");
                check(BiomeGenBase.desert.canSpawnLightningBolt() && BiomeGenBase.jungle.canSpawnLightningBolt(),"wet tropical phase enables precipitation");
                biome(w,20,20,BiomeGenBase.desert);w.getWorldInfo().setRaining(true);w.setRainStrength(1);
                check(w.canLightningStrikeAt(20,64,20),"world precipitation check enables wet desert rain");
                w.setBlock(6,64,6,Blocks.cauldron,0,3);
                for(int i=0;i<200;i++)SeasonHooks.fillWithRain(Blocks.cauldron,w,6,64,6);
                check(w.getBlockMetadata(6,64,6)==0,"winter snow does not fill cauldrons as rain");
                sync(w,0);return;
            }
            ticks++;
            if(stage==0 && clientStage==0 && ticks>=60) {
                check(w.getBlockMetadata(4,64,4)==0,"modded block override blocked by real scheduled/random ticks");
                set(w,Season.SubSeason.LATE_SUMMER);
                check(SeasonHelper.getSeasonState(w).getTropicalSeason()==Season.TropicalSeason.MID_DRY,"dry fixture uses MID_DRY");
                check(!BiomeGenBase.desert.canSpawnLightningBolt() && !BiomeGenBase.jungle.canSpawnLightningBolt(),"dry tropical phase disables precipitation");
                check(!w.canLightningStrikeAt(20,64,20),"world precipitation check suppresses dry desert rain");
                for(int i=0;i<200;i++)SeasonHooks.fillWithRain(Blocks.cauldron,w,6,64,6);
                check(w.getBlockMetadata(6,64,6)>0,"summer rain fills cauldron");
                w.scheduleBlockUpdate(4,64,4,probe,1);sync(w,1);ticks=0;
            } else if(stage==1 && clientStage==1 && ticks>=60) {
                check(w.getBlockMetadata(4,64,4)>0,"modded block override grows in summer");
                SeasonProperties summer=c.properties[5];summer.meltPercent=100;summer.meltRolls=64;
                for(int x=8;x<24;x++)for(int z=8;z<24;z++){w.setBlock(x,63,z,Blocks.ice);w.setBlock(x,64,z,Blocks.snow_layer);}
                sync(w,2);ticks=0;
            } else if(stage==2 && clientStage==2 && ticks>=80) {
                int snow=0,ice=0;for(int x=8;x<24;x++)for(int z=8;z<24;z++){if(w.getBlock(x,64,z)==Blocks.snow_layer)snow++;if(w.getBlock(x,63,z)==Blocks.ice)ice++;}
                check(snow<256 && ice<256,"configured melt rolls and chance: snow="+snow+", ice="+ice);
                c.properties[10].temperature=-0.42F;c.undergroundFertilityLevel=37;set(w,Season.SubSeason.MID_WINTER);sync(w,3);ticks=0;
            } else if(stage==3 && clientStage==3 && ticks>=40) {
                check(Math.abs(SeasonHooks.temperatureAt(w,BiomeGenBase.plains,0,64,0)-0.38F)<0.001F,"server custom temperature");
                log(failures==0?"ALL PARITY CHECKS PASSED":"PARITY CHECKS FAILED: "+failures);done=true;
            }
            if(ticks>2400){check(false,"client stage timeout "+stage);done=true;}
        }catch(Throwable error){check(false,"server exception "+error);error.printStackTrace();done=true;}
    }
    private static void biome(World w,int x,int z,BiomeGenBase b){w.getChunkFromBlockCoords(x,z).getBiomeArray()[(z&15)*16+(x&15)]=(byte)b.biomeID;}
    private static void crop(World w,Block b,int y){w.setBlock(0,y-1,0,Blocks.farmland,7,3);w.setBlock(0,y,0,b,0,3);}
    private static void set(World w,Season.SubSeason s){SeasonHandler.setTime(w,s.ordinal()*new SeasonTime(0).getSubSeasonDuration());}
    private void sync(World w,int next){SereneSeasons.network.sendToDimension(new SyncSeasonPacket(w,true),w.provider.dimensionId);stage=next;}
}
