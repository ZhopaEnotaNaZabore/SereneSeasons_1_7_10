package sereneseasons.season;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.world.World;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import sereneseasons.api.season.*;
import sereneseasons.core.SereneSeasons;
import sereneseasons.init.ModConfig;
import sereneseasons.network.SyncSeasonPacket;

public class SeasonHandler {
    @SubscribeEvent public void load(WorldEvent.Load event) {
        if (!event.world.isRemote && !event.world.getGameRules().hasRule("doSeasonCycle"))
            event.world.getGameRules().addGameRule("doSeasonCycle", "true");
    }
    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if (!world.isRemote && event.phase == TickEvent.Phase.START) { SeasonHooks.tickingWorld.set(world); return; }
        if (!world.isRemote && event.phase == TickEvent.Phase.END && !ModConfig.seasons.enabled(world)) SeasonHooks.tickingWorld.remove();
        if (event.phase != TickEvent.Phase.END || world.isRemote || !ModConfig.seasons.enabled(world)) return;
        SeasonSavedData data = SeasonSavedData.get(world);
        long now = world.getWorldTime();
        long delta = data.lastDayTime == Long.MIN_VALUE ? 0 : now - data.lastDayTime;
        data.lastDayTime = now;
        if (world.getGameRules().getGameRuleBooleanValue("doSeasonCycle")
            && (ModConfig.seasons.progressSeasonWhileOffline || !net.minecraft.server.MinecraftServer.getServer().getConfigurationManager().playerEntityList.isEmpty())) {
            // Sleeping advances the calendar. Backward /time changes never reverse it.
            if (delta < 0) delta = Math.floorMod(delta, 24000L);
            if (delta > 0) setTime(world, (int)((data.seasonCycleTicks + delta) % (new SeasonTime(0)).getCycleDuration()));
        }
        if (world.getTotalWorldTime() % 20 == 0) sync(world);
        SeasonHooks.updateWeather(world);
        SeasonHooks.tickingWorld.remove();
    }
    @SubscribeEvent public void bonemeal(net.minecraftforge.event.entity.player.BonemealEvent event) {
        if (!SeasonHooks.canGrow(event.world, event.x, event.y, event.z)) {
            event.setCanceled(true);
            net.minecraft.item.ItemStack held=event.entityPlayer.getCurrentEquippedItem();
            if(!event.world.isRemote && !event.entityPlayer.capabilities.isCreativeMode && held!=null
                && held.getItem()==net.minecraft.init.Items.dye && held.getItemDamage()==15) {
                held.stackSize--;
                if(held.stackSize<=0)event.entityPlayer.inventory.setInventorySlotContents(event.entityPlayer.inventory.currentItem,null);
            }
        }
    }
    public static void setTime(World world, int ticks) {
        SeasonSavedData data = SeasonSavedData.get(world);
        Season.SubSeason before = new SeasonTime(data.seasonCycleTicks).getSubSeason();
        Season.TropicalSeason previousTropical=new SeasonTime(data.seasonCycleTicks).getTropicalSeason();
        data.seasonCycleTicks = Math.floorMod(ticks, (new SeasonTime(0)).getCycleDuration());
        data.markDirty();
        Season.SubSeason after = new SeasonTime(data.seasonCycleTicks).getSubSeason();
        if (before != after) MinecraftForge.EVENT_BUS.post(new SeasonChangedEvent.Standard(world, before, after));
        Season.TropicalSeason tropical=new SeasonTime(data.seasonCycleTicks).getTropicalSeason();
        if(previousTropical!=tropical)MinecraftForge.EVENT_BUS.post(new SeasonChangedEvent.Tropical(world,previousTropical,tropical));
    }
    public static void sync(World world) { SereneSeasons.network.sendToDimension(new SyncSeasonPacket(world), world.provider.dimensionId); }
    private void syncPlayer(net.minecraft.entity.player.EntityPlayer player) {
        if (player instanceof EntityPlayerMP) SereneSeasons.network.sendTo(new SyncSeasonPacket(player.worldObj,true), (EntityPlayerMP)player);
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e) { syncPlayer(e.player); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { syncPlayer(e.player); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e) { syncPlayer(e.player); }
}
