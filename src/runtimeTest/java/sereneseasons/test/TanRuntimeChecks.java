package sereneseasons.test;

import net.minecraft.world.*;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import cpw.mods.fml.common.gameevent.TickEvent;
import sereneseasons.api.season.*;
import sereneseasons.compat.ToughAsNailsCompat;
import sereneseasons.init.ModConfig;
import toughasnails.api.temperature.*;
import toughasnails.temperature.TemperatureHandler;
import static sereneseasons.test.CompatRuntimeChecks.*;

public final class TanRuntimeChecks {
    public static void server(World w,EntityPlayerMP p) {
        ModConfig c=ModConfig.seasons;
        check(TemperatureHelper.getTemperatureModifiers().containsKey("sereneseasons"),"SS registered TAN API modifier");
        check(TemperatureHelper.getTemperatureModifiers().containsKey("biome"),"TAN native biome modifier remains registered");
        int[] expected={-3,0,2,3,5,3,2,0,-3,-7,-14,-7};
        for(Season.SubSeason season:Season.SubSeason.VALUES) {
            set(w,season);
            check(c.tanSeasonModifiers[season.ordinal()]==expected[season.ordinal()],"TAN config imported "+season);
            int with=TemperatureHelper.getTargetAtPosUnclamped(w,0,64,0,null).getRawValue();
            c.toughAsNailsIntegration=false;
            int without=TemperatureHelper.getTargetAtPosUnclamped(w,0,64,0,null).getRawValue();c.toughAsNailsIntegration=true;
            check(with-without==expected[season.ordinal()],"TAN full target seasonal delta "+season+" = "+(with-without));
        }
        set(w,Season.SubSeason.MID_WINTER);
        c.toughAsNailsIntegration=false;check(ToughAsNailsCompat.offset(w,0,64,0)==0,"integration config bypass");c.toughAsNailsIntegration=true;
        int[] dims=c.dimensions;c.dimensions=new int[]{-1};check(ToughAsNailsCompat.offset(w,0,64,0)==0,"disabled dimension bypass");c.dimensions=dims;
        for(BiomeGenBase b:new BiomeGenBase[]{BiomeGenBase.desert,BiomeGenBase.jungle,BiomeGenBase.ocean}) {
            w.getChunkFromChunkCoords(0,0).getBiomeArray()[0]=(byte)b.biomeID;
            check(ToughAsNailsCompat.offset(w,0,64,0)==0,"tropical or blacklisted biome bypass "+b.biomeName);
        }
        w.getChunkFromChunkCoords(0,0).getBiomeArray()[0]=(byte)BiomeGenBase.plains.biomeID;
        boolean snow=c.generateSnowAndIce;c.generateSnowAndIce=false;
        check(ToughAsNailsCompat.offset(w,0,64,0)==-14,"body temperature independent of snow generation toggle");c.generateSnowAndIce=snow;
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)w.setBlock(x,110,z,Blocks.stone);
        check(ToughAsNailsCompat.offset(w,0,64,0)==0,"deep shelter neutralizes season temperature");
        c.tanUndergroundEffect=false;check(ToughAsNailsCompat.offset(w,0,64,0)==-14,"underground effect switch respected");c.tanUndergroundEffect=true;
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)w.setBlockToAir(x,110,z);
        TemperatureHandler handler=(TemperatureHandler)TemperatureHelper.getTemperatureData(p);
        check(handler!=null,"real player's TAN temperature stat available");
        p.capabilities.isCreativeMode=false;w.difficultySetting=EnumDifficulty.NORMAL;
        int midpoint=TemperatureScale.getScaleMidpoint();
        set(w,Season.SubSeason.MID_WINTER);handler.setTemperature(new Temperature(midpoint));handler.setChangeTime(10000);
        int winter=handler.getPlayerTarget(p);handler.update(p,w,TickEvent.Phase.END);
        check(winter<midpoint && handler.getTemperature().getRawValue()<midpoint,"TAN player cools in winter target="+winter);
        set(w,Season.SubSeason.MID_SUMMER);handler.setTemperature(new Temperature(midpoint));handler.setChangeTime(10000);
        int summer=handler.getPlayerTarget(p);handler.update(p,w,TickEvent.Phase.END);
        check(summer>midpoint && handler.getTemperature().getRawValue()>midpoint,"TAN player warms in summer target="+summer);
        p.capabilities.isCreativeMode=true;p.sendPlayerAbilities();
        // Non-default server value must arrive on the client, not be read from shared TAN statics.
        c.tanSeasonModifiers[10]=-6;set(w,Season.SubSeason.MID_WINTER);
    }
    public static void client(World w) {
        check(ModConfig.forWorld(w)!=ModConfig.seasons,"TAN integration uses independent client snapshot");
        check(ModConfig.forWorld(w).tanSeasonModifiers[10]==-6,"custom TAN season offset synchronized");
        check(ToughAsNailsCompat.offset(w,0,64,0)==-6,"client TAN modifier follows server season and rules");
    }
}
