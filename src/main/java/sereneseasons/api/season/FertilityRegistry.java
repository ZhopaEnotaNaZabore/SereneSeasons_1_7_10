package sereneseasons.api.season;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import sereneseasons.init.ModConfig;

/** Config-based replacement for crop/item tags. Use names to support late mod registration. */
public final class FertilityRegistry {
    private static final Map<String[],Map<String,Rule>> cache=Collections.synchronizedMap(new WeakHashMap<String[],Map<String,Rule>>());
    private static final class Rule {
        int wildcard=-1;
        final Map<Integer,Integer> metadata=new HashMap<Integer,Integer>();
    }
    public static void validate(String[] entries) {
        for(String entry:entries) {
            int split=entry.lastIndexOf('=');
            if(split<1)throw new IllegalArgumentException("Invalid crop entry: "+entry);
            int mask=Integer.parseInt(entry.substring(split+1));
            if(mask<0 || mask>15)throw new IllegalArgumentException("Crop mask must be 0..15: "+entry);
            String key=entry.substring(0,split);int at=key.lastIndexOf('@');
            if(at>=0 && Integer.parseInt(key.substring(at+1))<0)throw new IllegalArgumentException("Negative metadata: "+entry);
            String name=at<0?key:key.substring(0,at);
            if(!name.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid registry name: "+entry);
        }
    }
    public static void registerCrop(String registryName,int mask) {
        String entry=registryName+"="+mask;validate(new String[]{entry});
        ModConfig.seasons.cropSeasons=append(ModConfig.seasons.cropSeasons,entry);
    }
    public static void registerSeed(String registryName,int mask) {
        String entry=registryName+"="+mask;validate(new String[]{entry});
        ModConfig.seasons.seedSeasons=append(ModConfig.seasons.seedSeasons,entry);
    }
    private static String[] append(String[] old,String value){String[] a=Arrays.copyOf(old,old.length+1);a[old.length]=value;return a;}
    public static int mask(String[] entries,String name,int meta) {
        Map<String,Rule> rules=cache.get(entries);
        if(rules==null) {
            validate(entries);rules=new HashMap<String,Rule>();
            for(String entry:entries) {
                int split=entry.lastIndexOf('=');String key=entry.substring(0,split);int value=Integer.parseInt(entry.substring(split+1));
                int at=key.lastIndexOf('@');String registry=at<0?key:key.substring(0,at);
                Rule rule=rules.get(registry);if(rule==null){rule=new Rule();rules.put(registry,rule);}
                if(at<0)rule.wildcard=value;else rule.metadata.put(Integer.parseInt(key.substring(at+1)),value);
            }
            cache.put(entries,rules);
        }
        Rule rule=rules.get(name);if(rule==null)return -1;
        Integer specific=rule.metadata.get(meta);return specific==null?rule.wildcard:specific;
    }
    public static boolean matches(String entry,String name,int meta){return entry.equals(name)||entry.equals(name+"@"+meta);}
    public static boolean listed(String[] entries,Block block,int meta) {
        String name=(String)Block.blockRegistry.getNameForObject(block);
        for(String e:entries)if(matches(e,name,meta))return true;return false;
    }
    public static int cropMask(World w,int x,int y,int z) {
        Block b=w.getBlock(x,y,z);int meta=w.getBlockMetadata(x,y,z);
        if(b==Blocks.sapling)meta&=7;
        return mask(ModConfig.forWorld(w).cropSeasons,(String)Block.blockRegistry.getNameForObject(b),meta);
    }
    public static int seedMask(ModConfig c,ItemStack stack) {
        return mask(c.seedSeasons,(String)Item.itemRegistry.getNameForObject(stack.getItem()),stack.getItemDamage());
    }
    public static boolean isFertile(World w,int x,int y,int z) {
        ModConfig c=ModConfig.forWorld(w);int mask=cropMask(w,x,y,z);
        if(mask<0)return true;
        if(y<c.undergroundFertilityLevel && !w.canBlockSeeTheSky(x,y,z))return true;
        BiomeGenBase biome=w.getBiomeGenForCoords(x,z);
        if(ModConfig.contains(c.infertileBiomes,biome.biomeID))return false;
        if(!c.seasonalCrops || !c.enabled(w) || ModConfig.contains(c.blacklistedBiomes,biome.biomeID))return true;
        if(ModConfig.contains(c.tropicalBiomes,biome.biomeID))return (mask&2)!=0;
        float temperature=sereneseasons.season.SeasonHooks.temperatureAt(w,biome,x,y,z);
        int bit=temperature<0.15F?8:1<<SeasonHelper.getSeasonState(w).getSeason().ordinal();
        return (mask&bit)!=0;
    }
    public static boolean greenhouse(World w,int x,int y,int z) {
        for(int roof=y+1;roof<=Math.min(255,y+16);roof++)
            if(listed(ModConfig.forWorld(w).greenhouseGlass,w.getBlock(x,roof,z),w.getBlockMetadata(x,roof,z)))return true;
        return false;
    }
}
