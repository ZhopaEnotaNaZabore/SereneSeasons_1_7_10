package sereneseasons.network;
import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import sereneseasons.core.SereneSeasons;
import sereneseasons.init.ModConfig;
import net.minecraft.world.World;
import sereneseasons.season.SeasonSavedData;

public class SyncSeasonPacket implements IMessage {
    public int dimension, ticks, dayDuration, subSeasonDuration;
    public boolean enabled;
    public boolean seasonalTemperature;
    public net.minecraft.nbt.NBTTagCompound rules;
    public SyncSeasonPacket() {}
    public SyncSeasonPacket(World world) {
        this(world,false);
    }
    public SyncSeasonPacket(World world,boolean includeRules) {
        dimension = world.provider.dimensionId;
        ticks = SeasonSavedData.get(world).seasonCycleTicks;
        dayDuration = ModConfig.seasons.dayDuration;
        subSeasonDuration = ModConfig.seasons.subSeasonDuration;
        enabled = ModConfig.seasons.enabled(world);
        seasonalTemperature = ModConfig.seasons.generateSnowAndIce;
        if(includeRules)rules=ModConfig.seasons.writeRules();
    }
    public void fromBytes(ByteBuf buf) {
        dimension = buf.readInt(); ticks = buf.readInt(); dayDuration = buf.readInt();
        subSeasonDuration = buf.readInt(); enabled = buf.readBoolean(); seasonalTemperature = buf.readBoolean();
        rules=cpw.mods.fml.common.network.ByteBufUtils.readTag(buf);
    }
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension); buf.writeInt(ticks); buf.writeInt(dayDuration);
        buf.writeInt(subSeasonDuration); buf.writeBoolean(enabled); buf.writeBoolean(seasonalTemperature);
        cpw.mods.fml.common.network.ByteBufUtils.writeTag(buf,rules);
    }
    public static class Handler implements IMessageHandler<SyncSeasonPacket, IMessage> {
        public IMessage onMessage(SyncSeasonPacket packet, MessageContext context) {
            SereneSeasons.proxy.receive(packet); return null;
        }
    }
}
