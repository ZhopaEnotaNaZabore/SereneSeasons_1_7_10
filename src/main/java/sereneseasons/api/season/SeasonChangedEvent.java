package sereneseasons.api.season;
import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.world.World;
/** Forge equivalent of the 1.21 standard and tropical events. */
public class SeasonChangedEvent<T> extends Event {
    public final World world;
    public final T previousSeason,newSeason;
    protected SeasonChangedEvent(World world,T previous,T next){this.world=world;previousSeason=previous;newSeason=next;}
    public World getLevel(){return world;}
    public T getPrevSeason(){return previousSeason;}
    public T getNewSeason(){return newSeason;}
    public static class Standard extends SeasonChangedEvent<Season.SubSeason> {
        public Standard(World w,Season.SubSeason previous,Season.SubSeason next){super(w,previous,next);}
    }
    public static class Tropical extends SeasonChangedEvent<Season.TropicalSeason> {
        public Tropical(World w,Season.TropicalSeason previous,Season.TropicalSeason next){super(w,previous,next);}
    }
}
