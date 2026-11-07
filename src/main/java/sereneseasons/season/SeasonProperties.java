package sereneseasons.season;

import net.minecraftforge.common.config.Configuration;
import net.minecraft.nbt.NBTTagCompound;
import sereneseasons.api.season.Season;

/** Java 8 equivalent of the original per-sub-season weather properties. */
public final class SeasonProperties {
    public float temperature, meltPercent;
    public int meltRolls, minRain, maxRain, minThunder, maxThunder;
    public SeasonProperties(int index) {
        float[] melt={6.25F,8.33F,12.5F,25,25,25,12.5F,8.33F,6.25F,0,0,0};
        temperature=index>=9 ? -0.8F : index==0 || index==8 ? -0.25F : 0;
        meltPercent=melt[index]; meltRolls=index>=9?0:1;
        minRain=12000; maxRain=index>=9?36000:index>=6?180000:96000;
        minThunder=index>=9?-1:12000;maxThunder=index>=9?-1:180000;
    }
    public void load(Configuration config,int index) {
        String cat="season_properties."+Season.SubSeason.VALUES[index].getSerializedName();
        temperature=config.getFloat("temperature",cat,temperature,-10,10,"Biome temperature adjustment.");
        meltPercent=config.getFloat("meltPercent",cat,meltPercent,0,100,"Chance per melt roll, percent.");
        meltRolls=config.getInt("meltRolls",cat,meltRolls,0,64,"Random positions per active chunk per tick.");
        minRain=config.getInt("minRain",cat,minRain,-1,Integer.MAX_VALUE-1,"Minimum dry interval; -1 disables rain.");
        maxRain=config.getInt("maxRain",cat,maxRain,-1,Integer.MAX_VALUE-1,"Maximum dry interval.");
        minThunder=config.getInt("minThunder",cat,minThunder,-1,Integer.MAX_VALUE-1,"Minimum thunder interval; -1 disables thunder.");
        maxThunder=config.getInt("maxThunder",cat,maxThunder,-1,Integer.MAX_VALUE-1,"Maximum thunder interval.");
        validate();
    }
    private void validate() {
        if(minRain>maxRain || minThunder>maxThunder) throw new IllegalArgumentException("Season minimum weather interval exceeds maximum");
    }
    public NBTTagCompound write() {
        NBTTagCompound n=new NBTTagCompound(); n.setFloat("temperature",temperature);n.setFloat("melt",meltPercent);
        n.setInteger("rolls",meltRolls);n.setInteger("rainMin",minRain);n.setInteger("rainMax",maxRain);
        n.setInteger("thunderMin",minThunder);n.setInteger("thunderMax",maxThunder);return n;
    }
    public void read(NBTTagCompound n) {
        temperature=n.getFloat("temperature");meltPercent=n.getFloat("melt");meltRolls=n.getInteger("rolls");
        minRain=n.getInteger("rainMin");maxRain=n.getInteger("rainMax");
        minThunder=n.getInteger("thunderMin");maxThunder=n.getInteger("thunderMax");validate();
    }
}
