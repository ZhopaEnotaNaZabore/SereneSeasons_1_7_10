package sereneseasons.season;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.entity.player.EntityPlayer;
import sereneseasons.api.season.*;
import sereneseasons.core.SereneSeasons;
import sereneseasons.init.ModConfig;

public final class SeasonHooks {
    public static final ThreadLocal<World> tickingWorld = new ThreadLocal<World>();
    private static final ThreadLocal<Boolean> rawTemperature = new ThreadLocal<Boolean>();
    private static final ThreadLocal<Boolean> growthChecked = new ThreadLocal<Boolean>();
    public static World contextWorld() {
        World w=tickingWorld.get();
        if(w==null && SereneSeasons.proxy!=null && FMLCommonHandler.instance().getEffectiveSide().isClient()) w=SereneSeasons.proxy.clientWorld();
        return w;
    }
    public static int birchColour(int original, net.minecraft.world.IBlockAccess access, int x, int y, int z) {
        if ((access.getBlockMetadata(x,y,z) & 3) != 2 || SereneSeasons.proxy == null) return original;
        return SereneSeasons.proxy.birchColour(original, access, x, y, z);
    }
    public static float temperature(float original, BiomeGenBase biome) {
        if(Boolean.TRUE.equals(rawTemperature.get()))return original;
        return adjustedTemperature(contextWorld(),biome,original);
    }
    public static float adjustedTemperature(World w,BiomeGenBase biome,float original) {
        if(w==null)return original;
        ModConfig c=ModConfig.forWorld(w);
        if(!c.enabled(w) || !c.generateSnowAndIce || ModConfig.contains(c.blacklistedBiomes,biome.biomeID)
            || ModConfig.contains(c.tropicalBiomes,biome.biomeID) || biome.temperature>0.8F)return original;
        float delta=c.properties[SeasonHelper.getSeasonState(w).getSubSeason().ordinal()].temperature;
        return Math.max(-0.5F,Math.min(2F,original+delta));
    }
    public static float temperatureAt(World w,BiomeGenBase biome,int x,int y,int z) {
        Boolean old=rawTemperature.get();rawTemperature.set(true);
        float original;
        try{original=biome.getFloatTemperature(x,y,z);}finally{if(old==null)rawTemperature.remove();else rawTemperature.set(old);}
        return adjustedTemperature(w,biome,original);
    }
    public static boolean precipitation(boolean original,BiomeGenBase biome) {
        World w=contextWorld();if(w==null)return original;
        ModConfig c=ModConfig.forWorld(w);
        if(!c.enabled(w) || ModConfig.contains(c.blacklistedBiomes,biome.biomeID) || !ModConfig.contains(c.tropicalBiomes,biome.biomeID))return original;
        Season.TropicalSeason phase=SeasonHelper.getSeasonState(w).getTropicalSeason();
        if(phase==Season.TropicalSeason.MID_DRY)return false;
        if(phase==Season.TropicalSeason.MID_WET)return true;
        return original;
    }
    public static boolean canGrow(World world,int x,int y,int z) {
        if(Boolean.TRUE.equals(growthChecked.get()))return true;
        ModConfig c=ModConfig.forWorld(world);
        if(world.isRemote || !c.seasonalCrops || FertilityRegistry.cropMask(world,x,y,z)<0)return true;
        if(FertilityRegistry.isFertile(world,x,y,z) || FertilityRegistry.greenhouse(world,x,y,z))return true;
        if(c.outOfSeasonCropBehavior==0)return world.rand.nextInt(6)==0;
        if(c.outOfSeasonCropBehavior==2 && !FertilityRegistry.listed(c.unbreakableCrops,world.getBlock(x,y,z),world.getBlockMetadata(x,y,z)))
            world.setBlockToAir(x,y,z);
        return false;
    }
    /** Central dispatch covers registered mod blocks, including overrides that do not call super. */
    public static void randomTick(Block block,World world,int x,int y,int z,java.util.Random random) {
        if(!canGrow(world,x,y,z))return;
        Boolean old=growthChecked.get();growthChecked.set(true);
        try{block.updateTick(world,x,y,z,random);}finally{if(old==null)growthChecked.remove();else growthChecked.set(old);}
    }
    public static void fillWithRain(Block block,World world,int x,int y,int z) {
        if(temperatureAt(world,world.getBiomeGenForCoords(x,z),x,y,z)>=0.15F)block.fillWithRain(world,x,y,z);
    }
    public static void updateWeather(World world) {
        ModConfig c=ModConfig.forWorld(world);
        if(!c.enabled(world))return;
        SeasonProperties p=c.properties[SeasonHelper.getSeasonState(world).getSubSeason().ordinal()];
        if(c.changeWeatherFrequency) {
            if(p.minRain<0 || p.maxRain<0)world.getWorldInfo().setRaining(false);
            else if(!world.isRaining() && world.getWorldInfo().getRainTime()>p.maxRain)
                world.getWorldInfo().setRainTime(interval(world,p.minRain,p.maxRain));
            if(p.minThunder<0 || p.maxThunder<0)world.getWorldInfo().setThundering(false);
            else if(!world.isThundering() && world.getWorldInfo().getThunderTime()>p.maxThunder)
                world.getWorldInfo().setThunderTime(interval(world,p.minThunder,p.maxThunder));
        }
        if(!(world instanceof WorldServer) || !c.generateSnowAndIce || p.meltRolls==0 || p.meltPercent<=0)return;
        // Work only on loaded chunks near players. Never request a new chunk.
        for(Object object:((WorldServer)world).theChunkProviderServer.loadedChunks) {
            Chunk chunk=(Chunk)object;boolean near=false;
            for(Object playerObject:world.playerEntities) {
                EntityPlayer player=(EntityPlayer)playerObject;
                double dx=chunk.xPosition*16+8-player.posX,dz=chunk.zPosition*16+8-player.posZ;
                if(dx*dx+dz*dz<128*128){near=true;break;}
            }
            if(!near)continue;
            for(int roll=0;roll<p.meltRolls;roll++)if(world.rand.nextFloat()*100<p.meltPercent) {
                int x=chunk.xPosition*16+world.rand.nextInt(16),z=chunk.zPosition*16+world.rand.nextInt(16);
                int y=world.getPrecipitationHeight(x,z);if(y<1 || y>255)continue;
                BiomeGenBase biome=world.getBiomeGenForCoords(x,z);
                if(ModConfig.contains(c.blacklistedBiomes,biome.biomeID) || temperatureAt(world,biome,x,y,z)<0.15F)continue;
                if(world.getBlock(x,y,z)==Blocks.snow_layer)world.setBlockToAir(x,y,z);
                if(world.getBlock(x,y-1,z)==Blocks.snow_layer)world.setBlockToAir(x,y-1,z);
                else if(world.getBlock(x,y-1,z)==Blocks.ice) {
                    if(world.provider.isHellWorld)world.setBlockToAir(x,y-1,z);
                    else {world.setBlock(x,y-1,z,Blocks.flowing_water,0,3);world.notifyBlocksOfNeighborChange(x,y-1,z,Blocks.flowing_water);}
                }
            }
        }
    }
    private static int interval(World world,int min,int max){return min==max?min:min+world.rand.nextInt(max-min);}
}
