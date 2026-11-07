package sereneseasons.core;
import net.minecraft.world.World;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.season.SeasonTime;
import sereneseasons.network.SyncSeasonPacket;
public class CommonProxy {
    public void init() {}
    public sereneseasons.init.ModConfig rules() { return sereneseasons.init.ModConfig.seasons; }
    public int birchColour(int original, net.minecraft.world.IBlockAccess access, int x, int y, int z) { return original; }
    public World clientWorld() { return null; }
    public boolean enabled(World world) { return sereneseasons.init.ModConfig.seasons.enabled(world); }
    public boolean seasonalTemperature(World world) { return sereneseasons.init.ModConfig.seasons.generateSnowAndIce; }
    public void receive(SyncSeasonPacket packet) {}
    public ISeasonState getSeasonState(World world) { return SeasonTime.ZERO; }
}
