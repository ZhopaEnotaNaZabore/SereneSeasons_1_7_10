package sereneseasons.compat;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.init.ModConfig;
import toughasnails.api.temperature.IModifierMonitor;
import toughasnails.api.temperature.Temperature;
import toughasnails.api.temperature.TemperatureHelper;
import toughasnails.config.TemperatureConfig;
import toughasnails.temperature.modifier.TemperatureModifier;
import toughasnails.util.TerrainUtils;

/** Loaded exclusively behind Loader.isModLoaded; no TAN classes leak into SS's public API. */
public final class ToughAsNailsCompat extends TemperatureModifier {
    public static final String ID = "sereneseasons";
    private ToughAsNailsCompat() { super(ID); }

    public static void init() {
        TemperatureConfig t=toughasnails.init.ModConfig.temperature;
        ModConfig c=ModConfig.seasons;
        c.tanSeasonModifiers=new int[]{t.earlySpringModifier,t.midSpringModifier,t.lateSpringModifier,
            t.earlySummerModifier,t.midSummerModifier,t.lateSummerModifier,
            t.earlyAutumnModifier,t.midAutumnModifier,t.lateAutumnModifier,
            t.earlyWinterModifier,t.midWinterModifier,t.lateWinterModifier};
        c.tanUndergroundEffect=t.enableUndergroundEffect;
        c.tanEquilibriumDepth=Math.max(1,t.equilibriumDepth);
        if (!TemperatureHelper.registerTemperatureModifier(new ToughAsNailsCompat()))
            throw new IllegalStateException("Duplicate Serene Seasons TAN temperature modifier");
        FMLLog.info("[Serene Seasons] Optional Tough As Nails seasonal temperature integration registered");
    }

    @Override public boolean isPlayerSpecific() { return false; }

    @Override public Temperature applyEnvironmentModifiers(World world,int x,int y,int z,
            Temperature original,IModifierMonitor monitor) {
        int offset=offset(world,x,y,z);
        // TAN permits arbitrary integer config offsets. Saturate rather than wrap around.
        long raw=(long)original.getRawValue()+offset;
        Temperature result=new Temperature((int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,raw)));
        if(monitor!=null)monitor.addEntry(new IModifierMonitor.Context(ID,"Serene Seasons",original,result));
        return result;
    }

    public static int offset(World world,int x,int y,int z) {
        ModConfig c=ModConfig.forWorld(world);
        if(!c.toughAsNailsIntegration || !c.enabled(world))return 0;
        BiomeGenBase biome=world.getBiomeGenForCoords(x,z);
        if(ModConfig.contains(c.blacklistedBiomes,biome.biomeID)
                || ModConfig.contains(c.tropicalBiomes,biome.biomeID))return 0;
        int amount=c.tanSeasonModifiers[SeasonHelper.getSeasonState(world).getSubSeason().ordinal()];
        if(amount==0 || !c.tanUndergroundEffect || !world.provider.isSurfaceWorld())return amount;
        // Same surrounding-column shelter sampling as TAN; use SS's server-synced depth.
        float sum=0;int samples=0;
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
            float factor=1;
            if(TerrainUtils.isUnderground(world,x+dx,y,z+dz))
                factor=Math.max(0,1-(world.getPrecipitationHeight(x+dx,z+dz)-y)/(float)c.tanEquilibriumDepth);
            sum+=factor;samples++;
        }
        return Math.round(amount*(sum/samples));
    }
}
