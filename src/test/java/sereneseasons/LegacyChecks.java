package sereneseasons;

import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import sereneseasons.asm.SeasonsTransformer;
import sereneseasons.api.season.Season;
import sereneseasons.season.*;
import net.minecraft.nbt.NBTTagCompound;
import sereneseasons.init.ModConfig;

/** Dependency-free regression checks, executed by Gradle check/build. */
public final class LegacyChecks {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        java.util.Map<String,String> srgNames=new java.util.HashMap<String,String>();
        require(args.length==1,"Forge mapping path supplied");
        try(BufferedReader reader=new BufferedReader(new FileReader(args[0]))) {
            String line;while((line=reader.readLine())!=null){String[] parts=line.split(",",3);if(parts.length>=2)srgNames.put(parts[1],parts[0]);}
        }
        int sub = 24000 * 8;
        for (int i=0;i<12;i++) {
            require(new SeasonTime(i*sub).getSubSeason() == Season.SubSeason.VALUES[i], "sub-season start " + i);
            require(new SeasonTime((i+1)*sub-1).getSubSeason() == Season.SubSeason.VALUES[i], "sub-season end " + i);
            require(new SeasonTime(i*sub).getSeason().ordinal() == i/3, "season " + i);
        }
        require(new SeasonTime(12*sub).getSubSeason() == Season.SubSeason.EARLY_SPRING, "year rollover");
        require(new SeasonTime(sub*11).getTropicalSeason() == Season.TropicalSeason.MID_WET, "tropical mapping");
        SeasonTime custom = new SeasonTime(120, 20, 2);
        require(custom.getSubSeason() == Season.SubSeason.EARLY_SUMMER && custom.getCycleDuration() == 480, "server duration snapshot");
        try { new SeasonTime(0, Integer.MAX_VALUE, 8); throw new AssertionError("overflow accepted"); }
        catch (IllegalArgumentException expected) {}
        try { new SeasonTime(-1); throw new AssertionError("negative time accepted"); }
        catch (IllegalArgumentException expected) {}
        NBTTagCompound nbt = new NBTTagCompound();
        SeasonSavedData saved = new SeasonSavedData("test"); saved.seasonCycleTicks = sub*9;
        saved.writeToNBT(nbt);
        SeasonSavedData restored = new SeasonSavedData("test"); restored.readFromNBT(nbt);
        require(restored.seasonCycleTicks == saved.seasonCycleTicks, "NBT roundtrip");
        nbt.setInteger("SeasonCycleTicks", -1); restored.readFromNBT(nbt);
        require(restored.seasonCycleTicks == sub*12-1, "negative save migration");
        ModConfig.seasons.subSeasonDuration = 1; restored.readFromNBT(nbt);
        require(restored.seasonCycleTicks == 24000*12-1, "config change normalizes save");
        ModConfig.seasons.subSeasonDuration = 8;
        String[] targets = {"net.minecraft.world.biome.BiomeGenBase", "net.minecraft.block.BlockCrops",
            "net.minecraft.block.BlockStem", "net.minecraft.block.BlockReed", "net.minecraft.block.BlockCactus", "net.minecraft.block.BlockCocoa", "net.minecraft.block.BlockSapling", "net.minecraft.block.BlockOldLeaf", "net.minecraft.world.WorldServer"};
        for (String name : targets) {
            InputStream stream = LegacyChecks.class.getResourceAsStream("/"+name.replace('.','/')+".class");
            require(stream != null, "missing Forge class " + name);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(); byte[] chunk = new byte[8192]; int n;
            while ((n=stream.read(chunk)) != -1) buffer.write(chunk,0,n); stream.close();
            checkTransform(name, buffer.toByteArray());
            // Production FML maps obfuscated Minecraft names to SRG names before our transformer.
            ClassNode srg = new ClassNode(); new ClassReader(buffer.toByteArray()).accept(srg,0);
            for (Object object : srg.methods) {
                MethodNode m = (MethodNode)object;
                if(srgNames.containsKey(m.name))m.name=srgNames.get(m.name);
                for(AbstractInsnNode insn:m.instructions.toArray())if(insn instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)insn;
                    if(srgNames.containsKey(call.name))call.name=srgNames.get(call.name);
                }
            }
            ClassWriter writer = new ClassWriter(0); srg.accept(writer); checkTransform(name, writer.toByteArray());
            if (name.endsWith("BlockOldLeaf")) {
                java.util.Iterator it=srg.methods.iterator();
                while(it.hasNext()) if(((MethodNode)it.next()).name.equals("func_149720_d")) it.remove();
                writer=new ClassWriter(0); srg.accept(writer);
                byte[] server=writer.toByteArray();
                require(java.util.Arrays.equals(server,new SeasonsTransformer().transform(name,name,server)),"dedicated server stripped leaf class");
            }
        }
        ModConfig configured=new ModConfig();configured.properties[10].temperature=-0.42F;
        String[] masks={"example:crop@2=5","example:crop=6"};
        require(sereneseasons.api.season.FertilityRegistry.mask(masks,"example:crop",2)==5,"metadata rule takes precedence");
        require(sereneseasons.api.season.FertilityRegistry.mask(masks,"example:crop",3)==6,"wildcard crop mask");
        require(sereneseasons.api.season.FertilityRegistry.mask(masks,"example:unlisted",0)==-1,"unlisted crop unchanged");
        configured.cropSeasons=new String[]{"example:crop=5"};
        configured.toughAsNailsIntegration=false;configured.tanSeasonModifiers[10]=-9;
        configured.tanUndergroundEffect=false;configured.tanEquilibriumDepth=37;
        ModConfig restoredRules=ModConfig.readRules(configured.writeRules());
        require(restoredRules.properties[10].temperature==-0.42F && restoredRules.cropSeasons[0].equals("example:crop=5"),"server rules snapshot roundtrip");
        require(!restoredRules.toughAsNailsIntegration && restoredRules.tanSeasonModifiers[10]==-9
            && !restoredRules.tanUndergroundEffect && restoredRules.tanEquilibriumDepth==37,"TAN optional settings snapshot roundtrip");
        sereneseasons.network.SyncSeasonPacket packet=new sereneseasons.network.SyncSeasonPacket();
        packet.dimension=0;packet.ticks=123;packet.dayDuration=24000;packet.subSeasonDuration=8;packet.rules=configured.writeRules();
        io.netty.buffer.ByteBuf buffer=io.netty.buffer.Unpooled.buffer();packet.toBytes(buffer);
        sereneseasons.network.SyncSeasonPacket decoded=new sereneseasons.network.SyncSeasonPacket();decoded.fromBytes(buffer);buffer.release();
        require(decoded.ticks==123 && ModConfig.readRules(decoded.rules).properties[10].temperature==-0.42F,"rules network codec");
        require(ModConfig.readRules(decoded.rules).tanSeasonModifiers[10]==-9,"TAN rules network codec");
        System.out.println("PASS: season boundaries, tropical mapping, duration validation, NBT, rules/network and all 18 MCP/SRG transformer cases.");
    }
    private static void checkTransform(String name, byte[] original) throws Exception {
        byte[] output = new SeasonsTransformer().transform(name,name,original);
        ClassNode node = new ClassNode(); new ClassReader(output).accept(node,0);
        int hooks = 0;
        for (Object object : node.methods) {
            MethodNode method = (MethodNode)object;
            boolean patched = false;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode && ((MethodInsnNode)instruction).owner.equals("sereneseasons/season/SeasonHooks")) {
                    hooks++; patched = true;
                }
            }
            if (patched) new Analyzer(new BasicVerifier()).analyze(node.name,method);
        }
        require(hooks > 0, "missing injected hook " + name);
    }
}
