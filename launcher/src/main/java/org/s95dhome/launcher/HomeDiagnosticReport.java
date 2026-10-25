package org.s95dhome.launcher;
import org.s95dhome.privacy.DiagnosticSanitizer;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONObject;

/** Only the launcher's source diagnostics and own Home service log; no other app logs. */
final class HomeDiagnosticReport {
    static byte[] create(String model,int sdk,String sourceStatus,String connection) throws Exception {
        return create(model,sdk,sourceStatus,connection,"");
    }
    static byte[] create(String model,int sdk,String sourceStatus,String connection,String homeReturn) throws Exception {
        JSONObject data=new JSONObject();data.put("schema","home-diagnostics-0.1.0");data.put("model",safe(model));data.put("sdk",sdk);
        data.put("sourceStatus",safe(sourceStatus));data.put("sourceConnection",safe(connection));
        data.put("homeReturn",safe(homeReturn));
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)){
            zip.putNextEntry(new ZipEntry("home-diagnostics.json"));zip.write(data.toString(2).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
        return out.toByteArray();
    }
    private static String safe(String value){
        String clean=DiagnosticSanitizer.clean(value);
        // Retain multiline source state and protocol stages, while applying token redaction per line.
        StringBuilder out=new StringBuilder();
        if(value!=null)for(String line:value.split("\\r?\\n")){
            if(out.length()>12000)break;
            out.append(DiagnosticSanitizer.clean(line)).append('\n');
        }
        return out.length()==0?clean:out.toString();
    }
}
