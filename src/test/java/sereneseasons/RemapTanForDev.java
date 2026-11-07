package sereneseasons;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Test tooling only: convert the supplied production TAN's SRG names to the dev client's MCP names. */
public final class RemapTanForDev {
    public static void main(String[] args) throws Exception {
        final Map<String,String> names=new HashMap<String,String>();
        for(String file:new String[]{"methods.csv","fields.csv"}) {
            try(BufferedReader in=new BufferedReader(new FileReader(new File(args[2],file)))) {
                String line;while((line=in.readLine())!=null){String[] row=line.split(",",3);if(row.length>=2)names.put(row[0],row[1]);}
            }
        }
        Remapper remapper=new Remapper() {
            @Override public String mapMethodName(String owner,String name,String desc){return names.containsKey(name)?names.get(name):name;}
            @Override public String mapFieldName(String owner,String name,String desc){return names.containsKey(name)?names.get(name):name;}
        };
        File output=new File(args[1]);output.getParentFile().mkdirs();
        try(JarFile input=new JarFile(args[0]);JarOutputStream out=new JarOutputStream(new FileOutputStream(output))) {
            Enumeration<JarEntry> entries=input.entries();
            while(entries.hasMoreElements()) {
                JarEntry entry=entries.nextElement();
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream in=input.getInputStream(entry)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);}
                byte[] data=bytes.toByteArray();
                if(entry.getName().endsWith(".class")) {
                    ClassWriter writer=new ClassWriter(0);
                    new ClassReader(data).accept(new RemappingClassAdapter(writer,remapper),ClassReader.EXPAND_FRAMES);data=writer.toByteArray();
                }
                out.putNextEntry(new JarEntry(entry.getName()));out.write(data);out.closeEntry();
            }
        }
    }
}
