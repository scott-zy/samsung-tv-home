package org.s95dhome.privacy;
/** Best-effort redaction before display/export. No preferences or other-app logs are collected. */
public final class DiagnosticSanitizer {
    public static String clean(String value){
        if(value==null)return "";
        String text=value.length()>18000?value.substring(0,18000):value;
        text=text.replaceAll("(?im)\\b(authorization|cookie)\\s*[:=][^\\r\\n]+","$1=[redacted]");
        text=text.replaceAll("(?i)((?:token|password|secret|serial(?:number)?|duid|deviceid|macaddress)\\s*[\\\"']?\\s*[:=]\\s*[\\\"']?)[^\\s,;\\\"'&}]+","$1[redacted]");
        text=text.replaceAll("(?i)\\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b","[MAC]");
        text=text.replaceAll("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b","[IP]");
        text=text.replaceAll("(?i)\\[[0-9a-f:%.-]*:[0-9a-f:%.-]*\\]","[IP]");
        text=text.replaceAll("(?i)(?<![A-Za-z0-9])(?:[0-9a-f]{1,4}:){3,}[0-9a-f:%.-]*|(?<![A-Za-z0-9])[0-9a-f:]*::[0-9a-f:%.-]*","[IP]");
        text=text.replaceAll("(?i)\\b[a-z0-9._-]+\\.local\\b","[local-host]");
        text=text.replaceAll("(?i)\\b[a-z]:[\\\\/][^\\r\\n\\\"]+","[local-path]");
        text=text.replaceAll("(?i)\\b[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\b","[identifier]");
        return text.length()>12000?text.substring(0,12000):text;
    }
}
