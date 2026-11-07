package sereneseasons.init;

import java.io.File;
import net.minecraftforge.common.config.Configuration;
import net.minecraft.world.World;

public final class ModConfig {
    public static final ModConfig seasons = new ModConfig();
    public int dayDuration = 24000, subSeasonDuration = 8, startingSubSeason = 1;
    public boolean progressSeasonWhileOffline = true, changeGrassColor = true,
        changeFoliageColor = true, generateSnowAndIce = true, seasonalCrops = true;
    public int[] dimensions = {0};
    public int outOfSeasonCropBehavior = 0;
    public boolean changeWeatherFrequency = true;
    public boolean changeBirchColor = true;
    public boolean cropTooltips = true;
    public boolean toughAsNailsIntegration = true;
    // Populated from TAN's existing settings only when TAN is present, then synced by SS.
    public int[] tanSeasonModifiers = new int[12];
    public boolean tanUndergroundEffect = true;
    public int tanEquilibriumDepth = 32;
    public int undergroundFertilityLevel = 48;
    public int[] blacklistedBiomes={0,7,10,16,24,25};
    public int[] tropicalBiomes={2,14,15,17,21,22,23,35,36,37,38,39,130,149,151,163,164,165,166,167};
    public int[] lesserColorBiomes={6,134};
    public int[] infertileBiomes={};
    public String[] cropSeasons={"minecraft:wheat=6","minecraft:carrots=5","minecraft:potatoes=1",
        "minecraft:pumpkin_stem=4","minecraft:melon_stem=2","minecraft:reeds=2","minecraft:cactus=2","minecraft:cocoa=2",
        "minecraft:sapling@0=15","minecraft:sapling@1=13","minecraft:sapling@2=5","minecraft:sapling@3=2",
        "minecraft:sapling@4=2","minecraft:sapling@5=4","minecraft:red_mushroom=15","minecraft:brown_mushroom=15","minecraft:nether_wart=15"};
    public String[] seedSeasons={"minecraft:wheat_seeds=6","minecraft:wheat=6","minecraft:carrot=5","minecraft:potato=1",
        "minecraft:pumpkin_seeds=4","minecraft:melon_seeds=2","minecraft:reeds=2","minecraft:cactus=2","minecraft:dye@3=2",
        "minecraft:sapling@0=15","minecraft:sapling@1=13","minecraft:sapling@2=5","minecraft:sapling@3=2",
        "minecraft:sapling@4=2","minecraft:sapling@5=4","minecraft:red_mushroom=15","minecraft:brown_mushroom=15","minecraft:nether_wart=15"};
    public String[] greenhouseGlass={"minecraft:glass","minecraft:stained_glass"};
    public String[] unbreakableCrops={"minecraft:sapling","minecraft:reeds","minecraft:cactus","minecraft:red_mushroom","minecraft:brown_mushroom","minecraft:nether_wart"};
    public final sereneseasons.season.SeasonProperties[] properties=new sereneseasons.season.SeasonProperties[12];
    public ModConfig() {for(int i=0;i<12;i++)properties[i]=new sereneseasons.season.SeasonProperties(i);}
    public static ModConfig forWorld(World world) {
        return world!=null && world.isRemote ? sereneseasons.core.SereneSeasons.proxy.rules() : seasons;
    }
    public static boolean contains(int[] ids,int id) {for(int v:ids)if(v==id)return true;return false;}
    public void load(File file) {
        Configuration c = new Configuration(file);
        c.load();
        toughAsNailsIntegration=c.getBoolean("toughAsNailsIntegration","compatibility",true,"Apply seasonal body-temperature modifiers only when Tough As Nails is installed. Uses TAN's twelve season settings.");
        dayDuration = c.getInt("dayDuration", "time", 24000, 20, 1000000, "Ticks per season day.");
        subSeasonDuration = c.getInt("subSeasonDuration", "time", 8, 1, 178, "Days per sub-season; twelve sub-seasons per year.");
        startingSubSeason = c.getInt("startingSubSeason", "time", 1, 0, 12, "0 random; 1..12 early spring through late winter.");
        progressSeasonWhileOffline = c.getBoolean("progressSeasonWhileOffline", "time", true, "Advance on empty servers.");
        changeGrassColor = c.getBoolean("changeGrassColor", "visual", true, "Seasonal grass colours.");
        changeFoliageColor = c.getBoolean("changeFoliageColor", "visual", true, "Seasonal foliage colours.");
        changeBirchColor = c.getBoolean("changeBirchColor", "visual", true, "Seasonal birch colours; spruce is unchanged.");
        generateSnowAndIce = c.getBoolean("generateSnowAndIce", "weather", true, "Seasonal temperature, snow and ice.");
        changeWeatherFrequency = c.getBoolean("changeWeatherFrequency", "weather", true, "Seasonal rain intervals and no winter thunderstorms.");
        seasonalCrops = c.getBoolean("seasonalCrops", "fertility", true, "Restrict vanilla crop growth; glass roofs bypass restrictions.");
        outOfSeasonCropBehavior = c.getInt("outOfSeasonCropBehavior", "fertility", 0, 0, 2, "0: grow at 1/6 speed; 1: stop; 2: break.");
        dimensions = c.get("dimensions", "whitelist", new int[]{0}).getIntList();
        cropTooltips=c.getBoolean("cropTooltips","fertility",true,"Display fertile seasons on seed and crop items.");
        undergroundFertilityLevel=c.getInt("undergroundFertilityLevel","fertility",48,0,256,"Crops below this Y are fertile when the sky is obstructed.");
        cropSeasons=c.get("fertility","cropSeasons",cropSeasons,"registry:name[@metadata]=mask; spring=1 summer=2 autumn=4 winter=8; add bits.").getStringList();
        seedSeasons=c.get("fertility","seedSeasons",seedSeasons,"Item fertility tooltips; same format as cropSeasons.").getStringList();
        greenhouseGlass=c.get("fertility","greenhouseGlass",greenhouseGlass,"Registry block names, optional @metadata.").getStringList();
        unbreakableCrops=c.get("fertility","unbreakableCrops",unbreakableCrops,"Plants protected from out-of-season destruction.").getStringList();
        blacklistedBiomes=c.get("biomes","blacklisted",blacklistedBiomes).getIntList();
        tropicalBiomes=c.get("biomes","tropical",tropicalBiomes).getIntList();
        lesserColorBiomes=c.get("biomes","lesserColorChange",lesserColorBiomes).getIntList();
        infertileBiomes=c.get("biomes","infertile",infertileBiomes).getIntList();
        for(int i=0;i<12;i++)properties[i].load(c,i);
        sereneseasons.api.season.FertilityRegistry.validate(cropSeasons);
        sereneseasons.api.season.FertilityRegistry.validate(seedSeasons);
        c.save();
    }
    public boolean enabled(World world) {
        if (world == null) return false;
        for (int id : dimensions) if (id == world.provider.dimensionId) return true;
        return false;
    }
    public net.minecraft.nbt.NBTTagCompound writeRules() {
        net.minecraft.nbt.NBTTagCompound n=new net.minecraft.nbt.NBTTagCompound();
        n.setBoolean("tan",toughAsNailsIntegration);n.setIntArray("tanOffsets",tanSeasonModifiers);
        n.setBoolean("tanUnderground",tanUndergroundEffect);n.setInteger("tanDepth",tanEquilibriumDepth);
        n.setBoolean("snow",generateSnowAndIce);n.setBoolean("crops",seasonalCrops);
        n.setInteger("underground",undergroundFertilityLevel);n.setInteger("behavior",outOfSeasonCropBehavior);
        n.setIntArray("dimensions",dimensions);n.setIntArray("blacklist",blacklistedBiomes);n.setIntArray("tropical",tropicalBiomes);
        n.setIntArray("lesser",lesserColorBiomes);n.setIntArray("infertile",infertileBiomes);
        strings(n,"plants",cropSeasons);strings(n,"seeds",seedSeasons);strings(n,"glass",greenhouseGlass);strings(n,"protected",unbreakableCrops);
        for(int i=0;i<12;i++)n.setTag("season"+i,properties[i].write());return n;
    }
    public static ModConfig readRules(net.minecraft.nbt.NBTTagCompound n) {
        ModConfig c=new ModConfig();c.generateSnowAndIce=n.getBoolean("snow");c.seasonalCrops=n.getBoolean("crops");
        c.toughAsNailsIntegration=n.getBoolean("tan");
        int[] offsets=n.getIntArray("tanOffsets");if(offsets.length==12)c.tanSeasonModifiers=offsets;
        c.tanUndergroundEffect=n.getBoolean("tanUnderground");c.tanEquilibriumDepth=Math.max(1,n.getInteger("tanDepth"));
        c.undergroundFertilityLevel=n.getInteger("underground");c.outOfSeasonCropBehavior=n.getInteger("behavior");
        c.dimensions=n.getIntArray("dimensions");c.blacklistedBiomes=n.getIntArray("blacklist");c.tropicalBiomes=n.getIntArray("tropical");
        c.lesserColorBiomes=n.getIntArray("lesser");c.infertileBiomes=n.getIntArray("infertile");
        c.cropSeasons=strings(n,"plants");c.seedSeasons=strings(n,"seeds");c.greenhouseGlass=strings(n,"glass");c.unbreakableCrops=strings(n,"protected");
        for(int i=0;i<12;i++)c.properties[i].read(n.getCompoundTag("season"+i));return c;
    }
    private static void strings(net.minecraft.nbt.NBTTagCompound n,String key,String[] values) {
        net.minecraft.nbt.NBTTagList list=new net.minecraft.nbt.NBTTagList();
        for(String v:values)list.appendTag(new net.minecraft.nbt.NBTTagString(v));n.setTag(key,list);
    }
    private static String[] strings(net.minecraft.nbt.NBTTagCompound n,String key) {
        net.minecraft.nbt.NBTTagList list=n.getTagList(key,8);String[] values=new String[list.tagCount()];
        for(int i=0;i<values.length;i++)values[i]=list.getStringTagAt(i);return values;
    }
}
