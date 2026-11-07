package sereneseasons.season;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import sereneseasons.init.ModConfig;

public class SeasonSavedData extends WorldSavedData {
    public int seasonCycleTicks;
    public long lastDayTime = Long.MIN_VALUE;
    public boolean wasRaining;
    public SeasonSavedData(String name) { super(name); }
    public void readFromNBT(NBTTagCompound tag) {
        seasonCycleTicks = Math.floorMod(tag.getInteger("SeasonCycleTicks"), (new SeasonTime(0)).getCycleDuration());
    }
    public void writeToNBT(NBTTagCompound tag) { tag.setInteger("SeasonCycleTicks", seasonCycleTicks); }
    public static SeasonSavedData get(World world) {
        String key = "sereneseasons_" + world.provider.dimensionId;
        SeasonSavedData data = (SeasonSavedData) world.perWorldStorage.loadData(SeasonSavedData.class, key);
        if (data == null) {
            data = new SeasonSavedData(key);
            int start = ModConfig.seasons.startingSubSeason;
            data.seasonCycleTicks = (start == 0 ? world.rand.nextInt(12) : start - 1) * (new SeasonTime(0)).getSubSeasonDuration();
            world.perWorldStorage.setData(key, data);
            data.markDirty();
        }
        return data;
    }
}
