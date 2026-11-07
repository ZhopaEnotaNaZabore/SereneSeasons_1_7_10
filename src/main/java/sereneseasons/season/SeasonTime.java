/*******************************************************************************
 * Copyright 2021, the Glitchfiend Team.
 * All rights reserved.
 ******************************************************************************/
package sereneseasons.season;

import com.google.common.base.Preconditions;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.Season;
import sereneseasons.init.ModConfig;

public final class SeasonTime implements ISeasonState
{
    public static final SeasonTime ZERO = new SeasonTime(0);
    public final int time;
    private final int dayDuration;
    private final int subSeasonDays;
    
    public SeasonTime(int time)
    {
        this(time, ModConfig.seasons.dayDuration, ModConfig.seasons.subSeasonDuration);
    }

    public SeasonTime(int time, int dayDuration, int subSeasonDays)
    {
        Preconditions.checkArgument(time >= 0, "Time cannot be negative!");
        Preconditions.checkArgument(dayDuration >= 20 && subSeasonDays >= 1
            && (long)dayDuration * subSeasonDays * 12 <= Integer.MAX_VALUE, "Invalid season durations");
        this.time = time;
        this.dayDuration = dayDuration;
        this.subSeasonDays = subSeasonDays;
    }

    @Override
    public int getDayDuration()
    {
        return dayDuration;
    }

    @Override
    public int getSubSeasonDuration()
    {
        return getDayDuration() * subSeasonDays;
    }

    @Override
    public int getSeasonDuration()
    {
        return getSubSeasonDuration() * 3;
    }

    @Override
    public int getCycleDuration()
    {
        return getSubSeasonDuration() * Season.SubSeason.VALUES.length;
    }
    
    @Override
    public int getSeasonCycleTicks() 
    {
        return this.time;
    }

    @Override
    public int getDay()
    {
        return this.time / getDayDuration();
    }

    @Override
    public Season.SubSeason getSubSeason()
    {
        int index = (this.time / getSubSeasonDuration()) % Season.SubSeason.VALUES.length;
        return Season.SubSeason.VALUES[index];
    }

    @Override
    public Season getSeason()
    {
        return this.getSubSeason().getSeason();
    }

    @Override
    public Season.TropicalSeason getTropicalSeason()
    {
        int index = ((((this.time / getSubSeasonDuration()) + 11) / 2) + 5) % Season.TropicalSeason.VALUES.length;
        return Season.TropicalSeason.VALUES[index];
    }
}
