package org.s95dhome.launcher;
import org.junit.Test;
import org.s95dhome.privacy.DiagnosticSanitizer;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import org.junit.Assert;

public class PublicRegressionTest {
    @Test public void existingLogic()throws Exception{
        AppOrderTest.main(new String[0]);HomeReturnRaceTest.main(new String[0]);
        TvSourceSessionTest.main(new String[0]);HomeDiagnosticReportTest.main(new String[0]);
    }
    @Test public void localEndpointsOnly()throws Exception{
        Assert.assertEquals("127.0.0.1",TvEndpoint.localAddress(" 127.000.000.001 "));
        Assert.assertEquals("wss",TvEndpoint.channel("127.0.0.1").getScheme());
        for(String invalid:new String[]{"example.com","https://127.0.0.1","198.51.100.7","0.0.0.0","127.0.0.255","127.0.0.1/path","127.0.0.999"}){
            try{TvEndpoint.localAddress(invalid);Assert.fail("accepted unsafe endpoint");}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void privateDiagnosticsRedacted(){
        String input="21:01:22.175 local=/192.0.2.9:1234 peer=[2001:db8::1234] MAC=AA:BB:CC:DD:EE:FF\n"
            +"token=fixture-secret&x=1\n{\"token\":\"fixture-json-secret\"}\nAuthorization: Bearer fixture-bearer\nserial=fixture-serial\nD:\\Users\\Fixture\\private.txt";
        String safe=DiagnosticSanitizer.clean(input);
        for(String secret:new String[]{"192.0.2.9","2001:db8","AA:BB:CC","fixture-secret","fixture-json-secret","fixture-bearer","fixture-serial","private.txt"})Assert.assertFalse(secret,safe.contains(secret));
        Assert.assertTrue("event timestamps preserved",safe.contains("21:01:22.175"));
    }
    @Test public void certificatesAndRealLocalProtocol()throws Exception{
        Path dir=Files.createTempDirectory("tv-protocol-fixture");Path store=dir.resolve("fixture.jks");
        try{
            String executable=System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool";
            Process process=new ProcessBuilder(new File(System.getProperty("java.home"),"bin/"+executable).getPath(),"-genkeypair","-storetype","JKS","-keystore",store.toString(),"-alias","fixture","-storepass","source-test","-keypass","source-test","-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN=localhost").redirectErrorStream(true).start();
            try(InputStream in=process.getInputStream()){byte[] b=new byte[1024];while(in.read(b)!=-1){}}
            Assert.assertEquals("fixture generation",0,process.waitFor());
            KeyStore ks=KeyStore.getInstance("JKS");try(InputStream in=Files.newInputStream(store)){ks.load(in,"source-test".toCharArray());}
            X509Certificate certificate=(X509Certificate)ks.getCertificate("fixture");String pin=PinnedTvTls.fingerprint(certificate);
            PinnedTvTls.verify(certificate,pin);
            try{PinnedTvTls.verify(certificate,"0000000000000000000000000000000000000000000000000000000000000000");Assert.fail("wrong pin accepted");}catch(CertificateException expected){}
            try{PinnedTvTls.factory("");Assert.fail("unconfigured trust accepted");}catch(IllegalArgumentException expected){}
            TvSourceProtocolTest.main(new String[]{store.toString()});
            javax.net.ssl.KeyManagerFactory km=javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());km.init(ks,"source-test".toCharArray());
            javax.net.ssl.SSLContext tls=javax.net.ssl.SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),null,null);
            TvSourceProtocolTest.FakeTv server=new TvSourceProtocolTest.FakeTv(tls);TvSourceSession rejected=null;
            java.util.concurrent.CountDownLatch failed=new java.util.concurrent.CountDownLatch(1);
            try{
                server.start();Assert.assertTrue(server.ready.await(3,java.util.concurrent.TimeUnit.SECONDS));
                rejected=new TvSourceSession(server.endpoint(),PinnedTvTls.factory("0000000000000000000000000000000000000000000000000000000000000000"),new TvSourceSession.Listener(){
                    public void snapshot(TvSourceSession.Snapshot value){Assert.fail("untrusted peer supplied snapshot");}
                    public void unavailable(String reason){failed.countDown();}
                });rejected.start();Assert.assertTrue("wrong certificate stops connection",failed.await(5,java.util.concurrent.TimeUnit.SECONDS));Assert.assertTrue("no business request before pin approval",server.requests.isEmpty());
            }finally{if(rejected!=null)rejected.cancel();server.stop(1000);}
        }finally{Files.deleteIfExists(store);Files.deleteIfExists(dir);}
    }
}
