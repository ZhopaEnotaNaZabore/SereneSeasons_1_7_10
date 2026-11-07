package sereneseasons.api.season;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;
import sereneseasons.core.SereneSeasons;
import sereneseasons.season.SeasonSavedData;
import sereneseasons.season.SeasonTime;

public final class SeasonHelper {
    public interface ISeasonDataProvider {
        ISeasonState getServerSeasonState(World world);
        ISeasonState getClientSeasonState(World world);
        boolean usesTropicalSeasons(BiomeGenBase biome);
    }
    public static ISeasonDataProvider dataProvider=new ISeasonDataProvider() {
        public ISeasonState getServerSeasonState(World world){return new SeasonTime(SeasonSavedData.get(world).seasonCycleTicks);}
        public ISeasonState getClientSeasonState(World world){return SereneSeasons.proxy.getSeasonState(world);}
        public boolean usesTropicalSeasons(BiomeGenBase biome){return SeasonHelper.usesTropicalSeasons(sereneseasons.season.SeasonHooks.contextWorld(),biome);}
    };
    public static ISeasonState getSeasonState(World world) {
        return world == null ? SeasonTime.ZERO : world.isRemote
            ? dataProvider.getClientSeasonState(world) : dataProvider.getServerSeasonState(world);
    }
    public static boolean usesTropicalSeasons(BiomeGenBase biome) {
        return dataProvider.usesTropicalSeasons(biome);
    }
    public static boolean usesTropicalSeasons(World world,BiomeGenBase biome) {
        return sereneseasons.init.ModConfig.contains(sereneseasons.init.ModConfig.forWorld(world).tropicalBiomes,biome.biomeID);
    }
}
