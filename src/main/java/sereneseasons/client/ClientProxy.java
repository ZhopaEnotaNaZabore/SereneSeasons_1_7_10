package sereneseasons.client;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.BiomeEvent;
import net.minecraftforge.event.world.WorldEvent;
import sereneseasons.core.CommonProxy;
import sereneseasons.api.season.*;
import sereneseasons.network.SyncSeasonPacket;
import sereneseasons.season.SeasonTime;
import sereneseasons.init.ModConfig;
import sereneseasons.util.Color;

public class ClientProxy extends CommonProxy {
    private final Map<Integer, SyncSeasonPacket> states = new HashMap<Integer, SyncSeasonPacket>();
    private final Queue<SyncSeasonPacket> pending = new ConcurrentLinkedQueue<SyncSeasonPacket>();
    private ModConfig serverRules = new ModConfig();
    @Override public ModConfig rules() {return serverRules;}
    public void init() { FMLCommonHandler.instance().bus().register(this); MinecraftForge.EVENT_BUS.register(this); }
    public void receive(SyncSeasonPacket packet) { pending.add(packet); }
    public World clientWorld() { return Minecraft.getMinecraft().theWorld; }
    @Override public int birchColour(int original, net.minecraft.world.IBlockAccess access, int x, int y, int z) {
        World world = clientWorld();
        if (!enabled(world) || !ModConfig.seasons.changeBirchColor || ModConfig.contains(serverRules.blacklistedBiomes,access.getBiomeGenForCoords(x,z).biomeID)) return original;
        ISeasonState state = getSeasonState(world);
        int seasonal=(SeasonHelper.usesTropicalSeasons(world,access.getBiomeGenForCoords(x,z))
            ? state.getTropicalSeason() : state.getSubSeason()).getBirchColor();
        return SeasonColorRegistry.resolve(SeasonColorRegistry.Type.BIRCH,original,seasonal,access.getBiomeGenForCoords(x,z),state);
    }
    public ISeasonState getSeasonState(World world) {
        SyncSeasonPacket p = states.get(world.provider.dimensionId);
        return p == null ? new SeasonTime(0) : new SeasonTime(p.ticks, p.dayDuration, p.subSeasonDuration);
    }
    public boolean enabled(World world) {
        SyncSeasonPacket p = world == null ? null : states.get(world.provider.dimensionId);
        return p != null && p.enabled;
    }
    public boolean seasonalTemperature(World world) {
        SyncSeasonPacket p = world == null ? null : states.get(world.provider.dimensionId);
        return p != null && p.seasonalTemperature;
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if (event.world.isRemote) { states.remove(event.world.provider.dimensionId); }
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        if(clearRequested){states.clear();pending.clear();serverRules=new ModConfig();clearRequested=false;}
        SyncSeasonPacket p;
        while ((p = pending.poll()) != null) {
            if (p.ticks < 0 || p.dayDuration < 20 || p.subSeasonDuration < 1 || (long)p.dayDuration * p.subSeasonDuration * 12 > Integer.MAX_VALUE) continue;
            SyncSeasonPacket old = states.put(p.dimension, p);
            if(p.rules!=null)serverRules=ModConfig.readRules(p.rules);
            World world = clientWorld();
            if (world != null && world.provider.dimensionId == p.dimension && (old == null
                || old.ticks / (p.dayDuration * p.subSeasonDuration) != p.ticks / (p.dayDuration * p.subSeasonDuration)))
                Minecraft.getMinecraft().renderGlobal.loadRenderers();
        }
    }
    private int colour(net.minecraft.world.biome.BiomeGenBase biome, int original, boolean grass) {
        World world = clientWorld();
        if (!enabled(world) || ModConfig.contains(serverRules.blacklistedBiomes,biome.biomeID)
            || (grass ? !ModConfig.seasons.changeGrassColor : !ModConfig.seasons.changeFoliageColor)) return original;
        ISeasonState state = getSeasonState(world);
        ISeasonColorProvider provider = SeasonHelper.usesTropicalSeasons(world,biome) ? state.getTropicalSeason() : state.getSubSeason();
        int overlay = grass ? provider.getGrassOverlay() : provider.getFoliageOverlay();
        int result = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int a = (original >> shift) & 255, b = (overlay >> shift) & 255;
            int value = overlay == 0xFFFFFF ? a : a < 128 ? 2 * a * b / 255 : 255 - 2 * (255-a) * (255-b) / 255;
            result |= value << shift;
        }
        float saturation = grass ? provider.getGrassSaturationMultiplier() : provider.getFoliageSaturationMultiplier();
        if(ModConfig.contains(serverRules.lesserColorBiomes,biome.biomeID)) {
            int mixed=0;
            for(int shift=0;shift<=16;shift+=8)mixed|=(((result>>shift&255)+3*(original>>shift&255))/4)<<shift;
            result=mixed;
        }
        if (saturation >= 0) {
            double[] hsv = new Color(result).toHSV();
            result = Color.convertHSVtoRGB(hsv[0], hsv[1] * saturation, hsv[2]).toInt();
        }
        return SeasonColorRegistry.resolve(grass?SeasonColorRegistry.Type.GRASS:SeasonColorRegistry.Type.FOLIAGE,original,result,biome,state);
    }
    @SubscribeEvent public void tooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent e) {
        if(!ModConfig.seasons.cropTooltips || !serverRules.seasonalCrops)return;
        int mask=FertilityRegistry.seedMask(serverRules,e.itemStack);
        if(mask<0)return;
        e.toolTip.add(net.minecraft.util.StatCollector.translateToLocal("desc.sereneseasons.fertile_seasons")+":");
        if(mask==15)e.toolTip.add(net.minecraft.util.EnumChatFormatting.LIGHT_PURPLE+net.minecraft.util.StatCollector.translateToLocal("desc.sereneseasons.year_round"));
        else {
            net.minecraft.util.EnumChatFormatting[] colours={net.minecraft.util.EnumChatFormatting.GREEN,net.minecraft.util.EnumChatFormatting.YELLOW,net.minecraft.util.EnumChatFormatting.GOLD,net.minecraft.util.EnumChatFormatting.AQUA};
            for(int i=0;i<4;i++)if((mask&(1<<i))!=0)e.toolTip.add(colours[i]+net.minecraft.util.StatCollector.translateToLocal("desc.sereneseasons."+Season.values()[i].name().toLowerCase(Locale.ROOT)));
        }
    }
    @SubscribeEvent public void disconnect(cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        // Hand off cache clearing from the network thread to the normal client tick.
        clearRequested=true;
    }
    private volatile boolean clearRequested;
    @SubscribeEvent public void grass(BiomeEvent.GetGrassColor e) { e.newColor = colour(e.biome, e.newColor, true); }
    @SubscribeEvent public void foliage(BiomeEvent.GetFoliageColor e) { e.newColor = colour(e.biome, e.newColor, false); }
}
