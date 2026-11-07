package sereneseasons.asm;
import java.util.Map;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"sereneseasons.asm"})
public class SeasonsLoadingPlugin implements IFMLLoadingPlugin {
    public String[] getASMTransformerClass() { return new String[]{"sereneseasons.asm.SeasonsTransformer"}; }
    public String getModContainerClass() { return null; }
    public String getSetupClass() { return null; }
    public void injectData(Map<String,Object> data) {}
    public String getAccessTransformerClass() { return null; }
}
