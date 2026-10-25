package org.s95dhome.launcher;
import java.security.MessageDigest;
import java.security.cert.*;
import javax.net.ssl.*;
/** Only the certificate explicitly approved during setup is trusted for actual traffic. */
final class PinnedTvTls {
    static String fingerprint(X509Certificate cert)throws CertificateException{
        try{StringBuilder hex=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()))hex.append(String.format(java.util.Locale.ROOT,"%02X",b&255));return hex.toString();}
        catch(Exception e){throw new CertificateException("Cannot fingerprint certificate",e);}
    }
    static void verify(X509Certificate cert,String expected)throws CertificateException{
        if(expected==null||!expected.matches("[A-F0-9]{64}"))throw new CertificateException("Approved certificate required");
        cert.checkValidity();if(!fingerprint(cert).equals(expected))throw new CertificateException("TV certificate changed; repeat setup");
    }
    static SSLSocketFactory factory(final String pin)throws Exception{
        if(pin==null||!pin.matches("[A-F0-9]{64}"))throw new IllegalArgumentException("Approved certificate required");
        X509TrustManager trust=new X509TrustManager(){
            public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{throw new CertificateException("Unsupported");}
            public void checkServerTrusted(X509Certificate[] c,String a)throws CertificateException{if(c==null||c.length==0)throw new CertificateException("Missing certificate");verify(c[0],pin);}
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
        };
        SSLContext context=SSLContext.getInstance("TLS");context.init(null,new TrustManager[]{trust},null);return context.getSocketFactory();
    }
}
