package sereneseasons.api.season;

import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.biome.BiomeGenBase;

/** Register during initialization; callbacks run on the render thread. */
public final class SeasonColorRegistry {
    public enum Type { GRASS,FOLIAGE,BIRCH }
    public interface Override {
        int apply(Type type,int original,int seasonal,int current,BiomeGenBase biome,ISeasonState state);
    }
    private static final CopyOnWriteArrayList<Override> overrides=new CopyOnWriteArrayList<Override>();
    public static void register(Override callback){if(callback==null)throw new NullPointerException("callback");overrides.addIfAbsent(callback);}
    public static void unregister(Override callback){overrides.remove(callback);}
    public static int resolve(Type type,int original,int seasonal,BiomeGenBase biome,ISeasonState state) {
        int current=seasonal;for(Override callback:overrides)current=callback.apply(type,original,seasonal,current,biome,state);return current;
    }
}
