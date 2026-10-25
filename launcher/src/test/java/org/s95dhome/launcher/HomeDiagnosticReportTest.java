package org.s95dhome.launcher;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.json.JSONObject;

public class HomeDiagnosticReportTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) throws Exception {
        byte[] data=HomeDiagnosticReport.create("fixture-TV",29,"HDMI2 powerState=1\nstate received","authorized=true\nURL ?token=do-not-export&other=ok\n{\"token\":\"also-private\"}","收到 Home 事件\n任务通知 LockActivity\ntoken=home-private");
        check(data.length<65536,"diagnostics payload bounded");
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(data))){
            ZipEntry entry=zip.getNextEntry();check(entry!=null&&entry.getName().equals("home-diagnostics.json"),"one named report");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[1024];int n;
            while((n=zip.read(buf))!=-1)bytes.write(buf,0,n);
            String text=new String(bytes.toByteArray(),StandardCharsets.UTF_8);
            check(!text.contains("do-not-export")&&!text.contains("also-private")&&!text.contains("home-private"),"tokens must be redacted");
            JSONObject json=new JSONObject(text);
            check(json.length()==6,"only schema/model/sdk/sourceStatus/sourceConnection/homeReturn exported");
            check(json.getString("homeReturn").contains("收到 Home 事件")&&json.getString("homeReturn").contains("LockActivity"),"own Home records preserved");
            check(json.getString("sourceStatus").contains("HDMI2 powerState=1"),"source state preserved");
            check(zip.getNextEntry()==null,"no system binaries or unrelated logs");
        }
        System.out.println("PASS: single diagnostic JSON, fixed field whitelist, token redaction, source state preserved");
    }
}
