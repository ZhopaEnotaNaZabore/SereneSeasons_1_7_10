package sereneseasons.command;
import java.util.*;
import net.minecraft.command.*;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import sereneseasons.api.season.*;
import sereneseasons.season.*;
import sereneseasons.item.CalendarItem;

public class SeasonCommand extends CommandBase {
    public String getCommandName() { return "season"; }
    public String getCommandUsage(ICommandSender sender) { return "/season get | /season set <early_spring ... late_winter>"; }
    public int getRequiredPermissionLevel() { return 2; }
    public void processCommand(ICommandSender sender, String[] args) {
        World world = sender.getEntityWorld();
        if (!sereneseasons.init.ModConfig.seasons.enabled(world))
            throw new CommandException("commands.sereneseasons.setseason.disabled");
        if (args.length == 1 && args[0].equals("get")) { sender.addChatMessage(new ChatComponentText(CalendarItem.description(world))); return; }
        if (args.length == 2 && args[0].equals("set")) {
            Season.SubSeason season;
            try { season = Season.SubSeason.valueOf(args[1].toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw new WrongUsageException(getCommandUsage(sender)); }
            SeasonHandler.setTime(world, season.ordinal() * (new SeasonTime(0)).getSubSeasonDuration());
            SeasonHandler.sync(world);
            sender.addChatMessage(new ChatComponentText(CalendarItem.description(world))); return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "get", "set");
        if (args.length == 2 && args[0].equals("set")) {
            String[] names = new String[12];
            for (int i=0;i<12;i++) names[i] = Season.SubSeason.VALUES[i].getSerializedName();
            return getListOfStringsMatchingLastWord(args, names);
        }
        return null;
    }
}
