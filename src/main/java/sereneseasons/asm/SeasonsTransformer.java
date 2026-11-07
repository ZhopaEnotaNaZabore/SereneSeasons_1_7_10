package sereneseasons.asm;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Runs after FML's deobfuscation transformer: both MCP and SRG method names are supported. */
public class SeasonsTransformer implements IClassTransformer, Opcodes {
    private static final String HOOKS = "sereneseasons/season/SeasonHooks";
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        boolean biome = "net.minecraft.world.biome.BiomeGenBase".equals(transformedName);
        boolean server = "net.minecraft.world.WorldServer".equals(transformedName);
        boolean birch = "net.minecraft.block.BlockOldLeaf".equals(transformedName);
        boolean crop = "net.minecraft.block.BlockCrops".equals(transformedName)
            || "net.minecraft.block.BlockSapling".equals(transformedName)
            || "net.minecraft.block.BlockStem".equals(transformedName)
            || "net.minecraft.block.BlockReed".equals(transformedName)
            || "net.minecraft.block.BlockCactus".equals(transformedName)
            || "net.minecraft.block.BlockCocoa".equals(transformedName);
        if (!biome && !crop && !birch && !server) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        int patched = 0;
        for (Object object : node.methods) {
            MethodNode m = (MethodNode)object;
            if(server) {
                for(AbstractInsnNode insn:m.instructions.toArray())if(insn instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)insn;
                    if((call.name.equals("fillWithRain")||call.name.equals("func_149639_l")) && call.owner.equals("net/minecraft/block/Block")
                        && call.desc.equals("(Lnet/minecraft/world/World;III)V")) {
                        m.instructions.set(call,new MethodInsnNode(INVOKESTATIC,HOOKS,"fillWithRain","(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;III)V",false));patched++;
                    }
                    if((call.name.equals("updateTick")||call.name.equals("func_149674_a")) && call.owner.equals("net/minecraft/block/Block")
                        && call.desc.equals("(Lnet/minecraft/world/World;IIILjava/util/Random;)V")) {
                        m.instructions.set(call,new MethodInsnNode(INVOKESTATIC,HOOKS,"randomTick","(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;IIILjava/util/Random;)V",false));patched++;
                    }
                }
            }
            if(biome && (m.name.equals("canSpawnLightningBolt")||m.name.equals("func_76738_d")) && m.desc.equals("()Z")) {
                for(AbstractInsnNode insn:m.instructions.toArray())if(insn.getOpcode()==IRETURN) {
                    InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));
                    hook.add(new MethodInsnNode(INVOKESTATIC,HOOKS,"precipitation","(ZLnet/minecraft/world/biome/BiomeGenBase;)Z",false));
                    m.instructions.insertBefore(insn,hook);
                }
                patched++;
            }
            if (birch && (m.name.equals("colorMultiplier") || m.name.equals("func_149720_d"))
                && m.desc.equals("(Lnet/minecraft/world/IBlockAccess;III)I")) {
                for (AbstractInsnNode insn : m.instructions.toArray()) if (insn.getOpcode() == IRETURN) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(ALOAD,1));
                    hook.add(new VarInsnNode(ILOAD,2)); hook.add(new VarInsnNode(ILOAD,3)); hook.add(new VarInsnNode(ILOAD,4));
                    hook.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "birchColour", "(ILnet/minecraft/world/IBlockAccess;III)I", false));
                    m.instructions.insertBefore(insn,hook);
                }
                patched++;
            }
            if (biome && (m.name.equals("getFloatTemperature") || m.name.equals("func_150564_a")) && m.desc.equals("(III)F")) {
                for (AbstractInsnNode insn : m.instructions.toArray()) if (insn.getOpcode() == FRETURN) {
                    InsnList hook = new InsnList(); hook.add(new VarInsnNode(ALOAD,0));
                    hook.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "temperature", "(FLnet/minecraft/world/biome/BiomeGenBase;)F", false));
                    m.instructions.insertBefore(insn,hook);
                }
                patched++;
            }
            if (crop && (m.name.equals("updateTick") || m.name.equals("func_149674_a"))
                && m.desc.equals("(Lnet/minecraft/world/World;IIILjava/util/Random;)V")) {
                InsnList hook = new InsnList(); hook.add(new VarInsnNode(ALOAD,1));
                hook.add(new VarInsnNode(ILOAD,2)); hook.add(new VarInsnNode(ILOAD,3)); hook.add(new VarInsnNode(ILOAD,4));
                hook.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "canGrow", "(Lnet/minecraft/world/World;III)Z", false));
                LabelNode proceed = new LabelNode(); hook.add(new JumpInsnNode(IFNE,proceed)); hook.add(new InsnNode(RETURN));
                hook.add(proceed); hook.add(new FrameNode(F_SAME,0,null,0,null));
                m.instructions.insert(hook); patched++;
            }
        }
        // FML strips the client-only colour method on a dedicated server.
        if (birch && patched == 0) return bytes;
        int expected=biome?2:server?4:1;
        if (patched != expected) throw new IllegalStateException("Serene Seasons: expected "+expected+" hooks in " + transformedName + ", found " + patched);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
