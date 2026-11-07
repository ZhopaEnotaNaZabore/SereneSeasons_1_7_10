package sereneseasons.block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.world.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IIcon;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import cpw.mods.fml.relauncher.*;
import sereneseasons.api.season.*;
import sereneseasons.init.ModConfig;

public class SeasonSensorBlock extends BlockContainer {
    @SideOnly(Side.CLIENT) private IIcon[] tops;
    public SeasonSensorBlock() {
        super(Material.wood); setBlockName("sereneseasons.season_sensor"); setHardness(0.2F);
        setCreativeTab(sereneseasons.core.SereneSeasons.creativeTab); setBlockBounds(0, 0, 0, 1, 0.375F, 1);
    }
    public boolean isOpaqueCube() { return false; }
    public boolean renderAsNormalBlock() { return false; }
    public int getRenderType() { return 0; }
    public boolean canProvidePower() { return true; }
    public TileEntity createNewTileEntity(World world, int meta) { return new SensorTile(); }
    public int isProvidingWeakPower(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity tile = world.getTileEntity(x,y,z);
        return tile instanceof SensorTile ? ((SensorTile)tile).power : 0;
    }
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int side, float hx, float hy, float hz) {
        if (!p.capabilities.allowEdit) return false;
        if (!w.isRemote) { w.setBlockMetadataWithNotify(x,y,z,(w.getBlockMetadata(x,y,z)+1)%4,3); ((SensorTile)w.getTileEntity(x,y,z)).refresh(); }
        return true;
    }
    @SideOnly(Side.CLIENT) public void registerBlockIcons(IIconRegister r) {
        blockIcon = r.registerIcon("sereneseasons:season_sensor_side"); tops = new IIcon[4];
        String[] names = {"spring", "summer", "autumn", "winter"};
        for (int i=0;i<4;i++) tops[i] = r.registerIcon("sereneseasons:season_sensor_"+names[i]+"_top");
    }
    @SideOnly(Side.CLIENT) public IIcon getIcon(int side, int meta) { return side == 1 ? tops[meta & 3] : blockIcon; }
    public static class SensorTile extends TileEntity {
        public int power;
        @Override public void readFromNBT(net.minecraft.nbt.NBTTagCompound tag) {
            super.readFromNBT(tag);
            power = Math.max(0, Math.min(15, tag.getInteger("Power")));
        }
        @Override public void writeToNBT(net.minecraft.nbt.NBTTagCompound tag) {
            super.writeToNBT(tag); tag.setInteger("Power", power);
        }
        @Override public net.minecraft.network.Packet getDescriptionPacket() {
            net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
            writeToNBT(tag);
            return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord,yCoord,zCoord,0,tag);
        }
        @Override public void onDataPacket(net.minecraft.network.NetworkManager manager,
            net.minecraft.network.play.server.S35PacketUpdateTileEntity packet) {
            readFromNBT(packet.func_148857_g());
        }
        public void updateEntity() { if (!worldObj.isRemote && worldObj.getTotalWorldTime()%20 == 0) refresh(); }
        public void refresh() {
            ISeasonState state = SeasonHelper.getSeasonState(worldObj);
            int next = 0;
            if (ModConfig.seasons.enabled(worldObj) && state.getSeason().ordinal() == (getBlockMetadata() & 3))
                next = Math.min(15, 1 + (int)(15L * (state.getSeasonCycleTicks() % state.getSeasonDuration()) / state.getSeasonDuration()));
            if (power != next) {
                power = next; markDirty();
                worldObj.markBlockForUpdate(xCoord,yCoord,zCoord);
                worldObj.notifyBlocksOfNeighborChange(xCoord,yCoord,zCoord,getBlockType());
            }
        }
    }
}
