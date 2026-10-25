package org.s95dhome.launcher;
import java.io.FileInputStream;
import java.net.*;
import java.security.KeyStore;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.json.*;

/** Local TLS fake-TV regression for request echoes, ack packets, and source replies. */
public final class TvSourceProtocolTest {
    static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
    static final class FakeTv extends WebSocketServer {
        CountDownLatch ready=new CountDownLatch(1),opened=new CountDownLatch(1);
        BlockingQueue<String> requests=new LinkedBlockingQueue<>();
        volatile WebSocket peer;
        FakeTv(SSLContext context){super(new InetSocketAddress(InetAddress.getLoopbackAddress(),0));setWebSocketFactory(new DefaultSSLWebSocketServerFactory(context));}
        public void onStart(){ready.countDown();}
        public void onOpen(WebSocket p,ClientHandshake h){peer=p;opened.countDown();}
        public void onMessage(WebSocket p,String text){requests.add(text);}
        public void onClose(WebSocket p,int c,String r,boolean remote){}
        public void onError(WebSocket p,Exception e){e.printStackTrace();}
        URI endpoint() throws Exception{return new URI("wss://127.0.0.1:"+getPort()+"/api/v2/channels/com.samsung.tv.mobilebff");}
        void send(JSONObject inner) throws Exception{peer.send(new JSONObject().put("event","/sec/tv/appdata/mobilebff").put("data",inner.toString()).toString());}
    }
    public static void main(String[] args) throws Exception {
        KeyStore ks=KeyStore.getInstance("JKS");try(FileInputStream in=new FileInputStream(args[0])){ks.load(in,"source-test".toCharArray());}
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(ks,"source-test".toCharArray());
        TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(ks);
        SSLContext context=SSLContext.getInstance("TLS");context.init(km.getKeyManagers(),tm.getTrustManagers(),null);
        for(boolean denied:new boolean[]{false,true}){
            FakeTv server=new FakeTv(context);TvSourceSession session=null;
            final CountDownLatch snapshot=new CountDownLatch(1),failed=new CountDownLatch(1);
            final BlockingQueue<String> launches=new LinkedBlockingQueue<>();
            try{
                server.start();check(server.ready.await(3,TimeUnit.SECONDS),"server ready");
                session=new TvSourceSession(server.endpoint(),PinnedTvTls.factory(PinnedTvTls.fingerprint((java.security.cert.X509Certificate)ks.getCertificate("fixture"))),new TvSourceSession.Listener(){
                    public void snapshot(TvSourceSession.Snapshot value){check(value.states("1","2")[1].equals("signal"),"active source mapped");snapshot.countDown();}
                    public void unavailable(String reason){failed.countDown();}
                    public void launchResult(String result){launches.add(result);}
                });session.start();check(server.opened.await(4,TimeUnit.SECONDS),"TLS client open");
                check(session.launchInput(1)!=null,"no launch before authorization and source list");
                check(server.requests.poll(150,TimeUnit.MILLISECONDS)==null,"no request before authorization");
                if(denied){
                    server.peer.send("{\"event\":\"ms.channel.unauthorized\"}");
                    check(failed.await(2,TimeUnit.SECONDS),"denial stops session");
                    check(server.requests.poll(150,TimeUnit.MILLISECONDS)==null,"denied client sends no request");
                }else{
                    server.peer.send("{\"event\":\"ms.channel.connect\"}");server.peer.send("{\"event\":\"ms.channel.connect\"}");
                    check(server.requests.poll(150,TimeUnit.MILLISECONDS)==null,"wait for host service readiness after authorization");
                    server.send(new JSONObject().put("id","notify.service.status").put("response",new JSONObject().put("status","ready")));
                    String request=server.requests.poll(2,TimeUnit.SECONDS);check(request!=null,"source read sent");
                    JSONObject outer=new JSONObject(request);check(outer.getString("method").equals("ms.channel.emit"),"only read event method");
                    check(outer.getJSONObject("params").getString("to").equals("all"),"use verified channel broadcast route");
                    JSONObject inner=new JSONObject(outer.getJSONObject("params").getString("data"));
                    check(inner.getString("id").equals("get.extsources"),"only external-source request");
                    server.send(inner); // Local TV can relay the outbound request back to the client.
                    server.send(new JSONObject().put("id","get.extsources").put("seqId",inner.getString("seqId"))); // Empty ack.
                    check(!failed.await(150,TimeUnit.MILLISECONDS),"echo and ack must not close connection");
                    check(snapshot.getCount()==1,"echo is not a source snapshot");
                    JSONObject success=new JSONObject().put("id","get.extsources").put("seqId",inner.getString("seqId")).put("statusCode","100")
                        .put("response",new JSONArray("[{\"sourceId\":\"s/source.layer.hdmi@HDMI2\",\"powerState\":\"1\"}]"));
                    server.send(success);check(snapshot.await(2,TimeUnit.SECONDS),"real result after echo delivered");
                    check(session.diagnostics().contains("requestEchoes=1"),"echo counted");
                    check(session.diagnostics().contains("matched=1"),"real reply counted");
                    check(server.requests.poll(150,TimeUnit.MILLISECONDS)==null,"duplicate connect did not duplicate request");
                    check(session.launchInput(-1)!=null&&session.launchInput(4)!=null,"reject invalid ports");
                    check(session.launchInput(0)!=null,"reject unreported source");
                    check(session.launchInput(1)==null,"explicit HDMI2 selection sent");
                    check(session.launchInput(1)!=null,"repeat press suppressed while pending");
                    String launchRequest=server.requests.poll(2,TimeUnit.SECONDS);check(launchRequest!=null,"launch emitted");
                    JSONObject launchOuter=new JSONObject(launchRequest);
                    check(launchOuter.getJSONObject("params").getString("to").equals("all"),"launch uses verified broadcast route");
                    JSONObject launch=new JSONObject(launchOuter.getJSONObject("params").getString("data"));
                    check(launch.getString("id").equals("launch.extsource"),"native source launch");
                    check(launch.getJSONObject("params").getString("requestId").equals("s/source.layer.hdmi@HDMI2"),"exact HDMI2 mapping");
                    server.send(launch);
                    server.send(new JSONObject().put("id","launch.extsource").put("seqId",launch.getString("seqId")));
                    check(launches.poll(150,TimeUnit.MILLISECONDS)==null,"echo and empty ack are not success");
                    check(server.requests.poll(150,TimeUnit.MILLISECONDS)==null,"no repeated launch");
                    JSONObject launchReply=new JSONObject().put("id","launch.extsource").put("seqId",launch.getString("seqId"))
                        .put("statusCode","100").put("response",new JSONObject().put("status","ok"));
                    server.send(launchReply);
                    check(launches.poll(2,TimeUnit.SECONDS).contains("已接受 HDMI 2"),"real launch acceptance delivered");
                    String refresh=server.requests.poll(2,TimeUnit.SECONDS);check(refresh!=null,"refresh after launch");
                    check(new JSONObject(new JSONObject(refresh).getJSONObject("params").getString("data")).getString("id").equals("get.extsources"),"follow-up is read only");
                    server.send(launchReply);
                    check(launches.poll(150,TimeUnit.MILLISECONDS)==null,"duplicate acceptance ignored");
                    check(session.launchInput(1)==null,"another explicit action can launch");
                    JSONObject rejected=new JSONObject(new JSONObject(server.requests.poll(2,TimeUnit.SECONDS)).getJSONObject("params").getString("data"));
                    server.send(new JSONObject().put("id","launch.extsource").put("seqId",rejected.getString("seqId")).put("statusCode","500"));
                    check(launches.poll(2,TimeUnit.SECONDS).contains("未接受"),"rejection reported");
                    check(server.requests.poll(150,TimeUnit.MILLISECONDS)==null,"rejection never retries launch");
                }
            }finally{if(session!=null)session.cancel();server.stop(1000);}
        }
        System.out.println("PASS: TLS source read and explicit launch; auth gate; port validation; echo/ack ignored; repeat suppression; accept/reject; no automatic launch retry");
    }
}
