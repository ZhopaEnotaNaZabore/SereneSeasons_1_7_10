package sereneseasons.test;

import java.io.*;
import java.util.*;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.world.*;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.entity.player.*;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.inventory.*;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import sereneseasons.api.season.*;
import sereneseasons.block.SeasonSensorBlock;
import sereneseasons.core.SereneSeasons;
import sereneseasons.init.ModConfig;
import sereneseasons.season.*;

/** Isolated, opt-in item/command integration checks; no classes from here ship in the mod. */
public class ItemRuntimeChecks {
    private boolean launched, prepared;
    private volatile boolean finished;
    private int ticks, failures;
    private volatile int stage=-1;
    private volatile boolean clientDone;
    private int clientDelay;
    private final boolean[] seen=new boolean[12];
    private synchronized void check(boolean ok,String message) {
        if(!ok) failures++;
        log((ok?"PASS ":"FAIL ")+message);
    }
    private static synchronized void log(String message) {
        System.out.println("[SS-ITEMS] "+message);
        try(PrintWriter out=new PrintWriter(new FileWriter("item-checks.log",true))) {out.println(message);}
        catch(IOException e){throw new RuntimeException(e);}
    }
    @SubscribeEvent public void client(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getMinecraft();
        if(finished) {mc.shutdown();return;}
        try {
            if(!launched && mc.currentScreen instanceof GuiMainMenu) {
                launched=true; log("START "+new Date());
                mc.gameSettings.pauseOnLostFocus=false;
                mc.gameSettings.renderDistanceChunks=4;
                WorldSettings settings=new WorldSettings(12345L,WorldSettings.GameType.CREATIVE,false,false,WorldType.FLAT);
                settings.enableCommands();
                mc.launchIntegratedServer("ss-items-"+System.currentTimeMillis(),"Serene Seasons item checks",settings);
                return;
            }
            int s=stage;
            if(s<0 || s>=12 || mc.theWorld==null || mc.thePlayer==null || seen[s]) return;
            if(SeasonHelper.getSeasonState(mc.theWorld).getSubSeason().ordinal()!=s) {clientDelay=0;return;}
            if(++clientDelay<20 || !(mc.theWorld.getTileEntity(0,64,0) instanceof SeasonSensorBlock.SensorTile)) return;
            ItemStack calendar=new ItemStack(SereneSeasons.calendar);
            check(calendar.getIconIndex().getIconName().equals(String.format(Locale.ROOT,"sereneseasons:calendar_%02d",s)),"calendar inventory icon "+s);
            check(SereneSeasons.calendar.getIcon(calendar,0).getIconName().equals(calendar.getIconIndex().getIconName()),"held calendar icon "+s);
            List<String> tooltip=new ArrayList<String>();
            SereneSeasons.calendar.addInformation(calendar,mc.thePlayer,tooltip,false);
            check(!tooltip.isEmpty() && !tooltip.get(0).contains("desc.sereneseasons"),"calendar translated tooltip "+s);
            int expected=1+s%3*5;
            check(((SeasonSensorBlock.SensorTile)mc.theWorld.getTileEntity(0,64,0)).power==expected,"sensor client power "+s+" expected="+expected);
            check(SereneSeasons.seasonSensor.getIcon(1,s/3).getIconName().contains(Season.values()[s/3].name().toLowerCase(Locale.ROOT)),"sensor top texture "+s);
            // Exercise the six tropical icons using the player's actual chunk biome lookup.
            int x=(int)Math.floor(mc.thePlayer.posX),z=(int)Math.floor(mc.thePlayer.posZ);
            byte[] biomes=mc.theWorld.getChunkFromBlockCoords(x,z).getBiomeArray();
            int index=(z&15)*16+(x&15); byte old=biomes[index];
            try {
                biomes[index]=(byte)BiomeGenBase.jungle.biomeID;
                int tropical=SeasonHelper.getSeasonState(mc.theWorld).getTropicalSeason().ordinal();
                check(calendar.getIconIndex().getIconName().equals(String.format(Locale.ROOT,"sereneseasons:tropical_calendar_%02d",tropical)),"tropical calendar "+tropical);
            } finally {biomes[index]=old;}
            if(s==0) {
                check(!calendar.getDisplayName().startsWith("item."),"calendar localized item name");
                check(!new ItemStack(SereneSeasons.seasonSensor).getDisplayName().startsWith("tile."),"sensor localized item name");
            }
            seen[s]=true; clientDone=true; clientDelay=0;
        } catch(Throwable error) {check(false,"client exception "+error); finished=true;error.printStackTrace();}
    }
    @SubscribeEvent public void server(TickEvent.WorldTickEvent e) {
        World w=e.world;
        if(e.phase!=TickEvent.Phase.START || w.isRemote || w.provider.dimensionId!=0 || finished || w.playerEntities.isEmpty())return;
        try {
            EntityPlayerMP p=(EntityPlayerMP)w.playerEntities.get(0);
            if(!prepared) {
                prepared=true;
                w.getGameRules().setOrCreateGameRule("doMobSpawning","false");
                w.getGameRules().setOrCreateGameRule("doSeasonCycle","false");
                w.setWorldTime(6000);
                for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)w.setBlock(x,63,z,Blocks.stone);
                p.playerNetServerHandler.setPlayerLocation(0,65,5,180,20);
                recipes(w);
                ItemStack sensor=new ItemStack(SereneSeasons.seasonSensor);
                check(sensor.getItem().onItemUse(sensor,p,w,0,63,0,1,0.5F,1,0.5F),"sensor ItemBlock placement");
                check(w.getBlock(0,64,0)==SereneSeasons.seasonSensor && w.getTileEntity(0,64,0) instanceof SeasonSensorBlock.SensorTile,"sensor block and tile registered");
                w.setBlock(1,64,0,Blocks.redstone_wire);
                ItemStack calendar=new ItemStack(SereneSeasons.calendar);
                check(SereneSeasons.calendar.onItemRightClick(calendar,w,p)==calendar && calendar.stackSize==1,"calendar right click preserves item");
                check(calendar.getMaxStackSize()==1,"calendar stack limit");
                int before=SeasonSavedData.get(w).seasonCycleTicks;
                check(command(p,"season set invalid_season")==0 && SeasonSavedData.get(w).seasonCycleTicks==before,"invalid season rejected without mutation");
                check(command(p,"season get")>0,"season get command");
                int[] dimensions=ModConfig.seasons.dimensions;
                try {ModConfig.seasons.dimensions=new int[]{-1};check(command(p,"season set mid_winter")==0,"season set rejected in disabled dimension");}
                finally {ModConfig.seasons.dimensions=dimensions;}
                setStage(w,p,0); return;
            }
            if(++ticks%20!=0)return;
            if(!clientDone) {
                if(ticks>2400) {check(false,"client verification timeout stage "+stage);finish();} return;
            }
            if(stage<11) {setStage(w,p,stage+1);return;}
            SeasonSensorBlock.SensorTile tile=(SeasonSensorBlock.SensorTile)w.getTileEntity(0,64,0);
            NBTTagCompound tag=new NBTTagCompound(); tile.writeToNBT(tag);
            TileEntity copy=TileEntity.createAndLoadEntity(tag);
            check(copy instanceof SeasonSensorBlock.SensorTile && ((SeasonSensorBlock.SensorTile)copy).power==tile.power,"sensor power NBT roundtrip");
            int initial=w.getBlockMetadata(0,64,0);
            for(int i=0;i<4;i++) {
                check(SereneSeasons.seasonSensor.onBlockActivated(w,0,64,0,p,1,0,0,0),"sensor right click "+i);
                check(w.getBlockMetadata(0,64,0)==(initial+i+1)%4,"sensor selected season "+i);
            }
            p.capabilities.allowEdit=false;
            try {check(!SereneSeasons.seasonSensor.onBlockActivated(w,0,64,0,p,1,0,0,0) && w.getBlockMetadata(0,64,0)==initial,"sensor respects edit permission");}
            finally{p.capabilities.allowEdit=true;}
            ArrayList<ItemStack> drops=SereneSeasons.seasonSensor.getDrops(w,0,64,0,initial,0);
            check(drops.size()==1 && drops.get(0).getItem()==Item.getItemFromBlock(SereneSeasons.seasonSensor),"sensor survival drop");
            int[] dims=ModConfig.seasons.dimensions;
            try{ModConfig.seasons.dimensions=new int[]{-1};tile.refresh();check(tile.power==0,"sensor off in disabled dimension");}
            finally{ModConfig.seasons.dimensions=dims;tile.refresh();}
            w.setBlockToAir(0,64,0);
            check(w.getBlockMetadata(1,64,0)==0,"breaking sensor removes adjacent redstone power");
            finish();
        }catch(Throwable error){check(false,"server exception "+error);error.printStackTrace();finish();}
    }
    private void setStage(World w,EntityPlayerMP p,int s) {
        clientDone=false;
        check(command(p,"season set "+Season.SubSeason.VALUES[s].getSerializedName())>0,"set sub-season "+s);
        w.setBlockMetadataWithNotify(0,64,0,s/3,3);
        SeasonSensorBlock.SensorTile tile=(SeasonSensorBlock.SensorTile)w.getTileEntity(0,64,0);tile.refresh();
        check(tile.power==1+s%3*5,"sensor server power "+s);
        check(w.getBlockMetadata(1,64,0)==tile.power,"adjacent redstone strength "+s);
        // End-of-season reaches 15; selecting the next season switches output off.
        int saved=SeasonSavedData.get(w).seasonCycleTicks;
        SeasonHandler.setTime(w,(s/3+1)*new SeasonTime(0).getSeasonDuration()-1);tile.refresh();
        check(tile.power==15,"sensor season-end strength "+s);
        w.setBlockMetadataWithNotify(0,64,0,(s/3+1)%4,3);tile.refresh();check(tile.power==0,"sensor inactive season "+s);
        w.setBlockMetadataWithNotify(0,64,0,s/3,3);SeasonHandler.setTime(w,saved);tile.refresh();SeasonHandler.sync(w);
        stage=s;ticks=0;
    }
    private static int command(EntityPlayerMP p,String text){return MinecraftServer.getServer().getCommandManager().executeCommand(p,text);}
    private void recipes(World w) {
        Container container=new Container(){public boolean canInteractWith(EntityPlayer p){return true;}};
        InventoryCrafting grid=new InventoryCrafting(container,3,3);
        for(int i=0;i<9;i++)grid.setInventorySlotContents(i,new ItemStack(i==4?Items.clock:Items.paper));
        ItemStack result=CraftingManager.getInstance().findMatchingRecipe(grid,w);
        check(result!=null && result.getItem()==SereneSeasons.calendar,"calendar crafting recipe");
        for(int i=0;i<9;i++)grid.setInventorySlotContents(i,i<3?new ItemStack(Blocks.glass):i>5?new ItemStack(Blocks.stone_slab,1,3):i==4?new ItemStack(SereneSeasons.calendar):new ItemStack(Items.quartz));
        result=CraftingManager.getInstance().findMatchingRecipe(grid,w);
        check(result!=null && result.getItem()==Item.getItemFromBlock(SereneSeasons.seasonSensor),"sensor crafting recipe");
        grid.setInventorySlotContents(4,new ItemStack(Items.paper));
        check(CraftingManager.getInstance().findMatchingRecipe(grid,w)==null,"invalid sensor recipe rejected");
    }
    private void finish(){log(failures==0?"ALL ITEM CHECKS PASSED":"ITEM CHECKS FAILED: "+failures);finished=true;}
}
