package org.s95dhome.launcher;
import org.s95dhome.privacy.DiagnosticSanitizer;

import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

/** Source status subscription and explicit user-requested source selection. */
final class TvSourceSession {
    interface Listener {
        void snapshot(Snapshot snapshot);
        void unavailable(String reason);
        default void launchResult(String message) {}
    }
    static final class Snapshot {
        final String[] power = {"", "", "", ""};
        final String[] titles = {"HDMI 1", "HDMI 2", "HDMI 3", "HDMI 4"};
        final boolean[] present = new boolean[4], current = new boolean[4];
        static Snapshot parse(JSONObject data) throws Exception {
            if (!"get.extsources".equals(data.optString("id")) || !"100".equals(data.optString("statusCode"))) throw new IllegalArgumentException("source response rejected");
            JSONArray sources = data.optJSONArray("response");
            if (sources == null || sources.length() > 128) throw new IllegalArgumentException("invalid source list");
            Snapshot out = new Snapshot();
            for (int i=0;i<sources.length();i++) {
                JSONObject item = sources.optJSONObject(i); if(item==null) continue;
                String id = item.optString("sourceId");
                if (!id.matches("s/source\\.layer\\.hdmi@HDMI[1-4]")) continue;
                int port=id.charAt(id.length()-1)-'1';
                if(out.present[port]) throw new IllegalArgumentException("duplicate HDMI port");
                out.present[port]=true;
                String title=item.optString("title");
                if(title.matches("[^\\p{Cntrl}]{1,80}"))out.titles[port]=title;
                String power=item.optString("powerState");
                out.power[port]=power.matches("[0-9]{1,2}")?power:"";
                out.current[port]="true".equals(item.optString("currentSource"));
            }
            return out;
        }
        String[] states(String activePower, String connectedPower) {
            String[] out={"unknown","unknown","unknown","unknown"};
            for(int i=0;i<4;i++) if(present[i]) {
                out[i]="detected";
                if(!activePower.isEmpty()&&activePower.equals(power[i])) out[i]="signal";
                else if(!connectedPower.isEmpty()&&connectedPower.equals(power[i])) out[i]="connected";
            }
            return out;
        }
        String summary() {
            StringBuilder out=new StringBuilder("三星原生输入状态：");
            for(int i=0;i<4;i++)out.append("\nHDMI").append(i+1).append(present[i]?" powerState="+power[i]+", current="+current[i]:" 未报告");
            return out.toString();
        }
    }
    private final WebSocketClient socket;
    private final Listener listener;
    private final Timer timer=new Timer("S95D-source-status",true);
    private boolean closed, authorized, serviceReady, pending;
    private int serial;
    private long requestAt;
    private Snapshot latest;
    private long snapshotAt, launchAt;
    private int launchSerial, launchPort=-1;
    private String launchState="none";
    private final TvRemoteDraft.Trace trace=new TvRemoteDraft.Trace();
    private String stage="created",lastEvent="none",failure="",lastDataType="none",lastResponseCode="none";
    private String lastResponseType="none";
    private int receivedMessages,matchedReplies,ignoredReplies,ignoredNotifications,requestEchoes;
    static boolean isRequestEcho(JSONObject data) {
        return data.has("params")&&!data.has("response")&&!data.has("statusCode");
    }
    static JSONObject decodeData(Object value) throws Exception {
        if(value==null||value==JSONObject.NULL)return null;
        if(value instanceof JSONObject)return (JSONObject)value;
        if(!(value instanceof String))return null;
        String text=((String)value).trim();
        if(text.isEmpty()||text.equals("null"))return null;
        if(!text.startsWith("{"))return null;
        return new JSONObject(text);
    }
    TvSourceSession(URI endpoint, SSLSocketFactory tls, Listener listener) {
        if(!"wss".equals(endpoint.getScheme())||tls==null)throw new IllegalArgumentException("pinned TLS required");
        if(!"/api/v2/channels/com.samsung.tv.mobilebff".equals(endpoint.getPath()))throw new IllegalArgumentException("only source channel allowed");
        this.listener=listener;
        socket=new WebSocketClient(endpoint,new TvRemoteDraft(trace),null,6000){
            @Override protected void onSetSSLParameters(SSLParameters p){p.setEndpointIdentificationAlgorithm(null);}
            @Override public void onOpen(ServerHandshake h){synchronized(TvSourceSession.this){stage="HTTP "+h.getHttpStatus()+" local="+getLocalSocketAddress();}}
            @Override public void onMessage(String message){receive(message);}
            @Override public void onClose(int code,String reason,boolean remote){fail("输入状态连接关闭（"+code+"）："+DiagnosticSanitizer.clean(reason));}
            @Override public void onError(Exception e){
                StringBuilder detail=new StringBuilder();Throwable cause=e;
                for(int i=0;cause!=null&&i<3;i++,cause=cause.getCause())detail.append(cause.getClass().getSimpleName()).append(": ").append(DiagnosticSanitizer.clean(cause.getMessage())).append("; ");
                fail("输入状态连接失败："+detail);
            }
        };
        socket.setSocketFactory(tls);socket.setConnectionLostTimeout(0);
        socket.addHeader("Origin","https://"+endpoint.getHost()+":"+endpoint.getPort());
    }
    void start(){
        requestAt=System.currentTimeMillis();stage="connecting";
        timer.schedule(new TimerTask(){@Override public void run(){tick();}},1000,1000);
        socket.connect();
    }
    private synchronized void tick(){
        if(closed)return;
        long elapsed=System.currentTimeMillis()-requestAt;
        if(launchPort>=0&&System.currentTimeMillis()-launchAt>12000){
            launchPort=-1;launchState="response timeout; no retry";listener.launchResult("未收到切换确认，请查看电视画面；未自动重试");
        }
        if(!authorized&&elapsed>18000){fail("输入状态授权等待超时");return;}
        if(authorized&&!serviceReady&&elapsed>18000){fail("输入状态服务等待就绪超时");return;}
        if(pending&&elapsed>12000){fail("输入状态读取超时");return;}
        if(authorized&&!pending&&elapsed>=10000)query();
    }
    private synchronized void query(){
        if(closed||!authorized||!serviceReady||pending)return;
        try{
            JSONObject inner=new JSONObject();inner.put("version","0.2");inner.put("id","get.extsources");inner.put("seqId","s95d-"+(++serial));inner.put("params",new JSONObject());
            // On this TV host-directed requests timed out, while channel broadcast returned the list.
            // Polling only reads; selection is sent exclusively by launchInput().
            JSONObject params=new JSONObject();params.put("event","/sec/tv/appdata/mobilebff");params.put("to","all");params.put("data",inner.toString());
            JSONObject request=new JSONObject();request.put("method","ms.channel.emit");request.put("params",params);
            pending=true;stage="request "+serial;requestAt=System.currentTimeMillis();socket.send(request.toString());
        }catch(Exception e){fail("输入状态请求未完成");}
    }
    synchronized String launchInput(int port) {
        if(port<0||port>=4)return "HDMI 端口无效";
        if(closed||!authorized||!serviceReady)return "信号源接口尚未就绪，请刷新 HDMI 状态后重试";
        if(launchPort>=0)return "正在切换信号源，请稍候";
        if(latest==null||System.currentTimeMillis()-snapshotAt>25000)return "正在读取信号源，请稍后再按一次";
        if(!latest.present[port])return "电视未报告 HDMI "+(port+1)+"，请检查连接";
        try {
            JSONObject inner=new JSONObject().put("version","0.2").put("id","launch.extsource")
                .put("seqId","s95d-launch-"+(++launchSerial))
                .put("params",new JSONObject().put("requestId","s/source.layer.hdmi@HDMI"+(port+1)));
            JSONObject params=new JSONObject().put("event","/sec/tv/appdata/mobilebff").put("to","all").put("data",inner.toString());
            launchPort=port;launchAt=System.currentTimeMillis();launchState="sent HDMI"+(port+1);
            socket.send(new JSONObject().put("method","ms.channel.emit").put("params",params).toString());
            return null;
        } catch(Exception e){launchPort=-1;launchState="send failed";return "切换请求未发送，请刷新 HDMI 状态后重试";}
    }
    private synchronized void receive(String text){
        if(closed)return;
        if(text.length()>65536){fail("输入状态响应过大");return;}
        try{
            receivedMessages++;
            JSONObject outer=new JSONObject(text);String event=outer.optString("event");
            lastEvent=event.matches("[A-Za-z0-9./:_-]{1,80}")?event:"other";
            if(event.equals("ms.channel.unauthorized")){fail("电视未授权输入状态读取");return;}
            if(event.equals("ms.error")){fail("电视输入状态接口返回错误");return;}
            if(event.equals("ms.channel.connect")){if(!authorized){authorized=true;query();}return;}
            if(event.equals("ms.channel.ready")){serviceReady=true;query();return;}
            if(!authorized||!event.equals("/sec/tv/appdata/mobilebff"))return;
            Object value=outer.opt("data");lastDataType=value==null?"missing":value==JSONObject.NULL?"null":value.getClass().getSimpleName();
            JSONObject data=decodeData(value);
            if(data==null){ignoredNotifications++;return;}
            String id=data.optString("id");
            if(id.equals("notify.service.status")){
                JSONObject response=data.optJSONObject("response");
                if(response!=null&&"ready".equals(response.optString("status"))){serviceReady=true;query();}
            }else if(id.equals("launch.extsource")){
                if(isRequestEcho(data)){requestEchoes++;return;}
                if(launchPort<0||!("s95d-launch-"+launchSerial).equals(data.optString("seqId"))){ignoredReplies++;return;}
                String code=data.optString("statusCode");
                if(code.isEmpty()||code.equals("null"))return;
                JSONObject response=data.optJSONObject("response");
                boolean ok="100".equals(code)&&response!=null&&"ok".equals(response.optString("status"));
                int port=launchPort;launchPort=-1;launchState=(ok?"accepted HDMI":"rejected HDMI")+(port+1);
                listener.launchResult(ok?"电视已接受 HDMI "+(port+1)+" 切换请求":"电视未接受 HDMI "+(port+1)+" 切换请求");
                query();
            }else if(id.equals("get.extsources")){
                if(isRequestEcho(data)){requestEchoes++;return;}
                Object responseValue=data.opt("response");
                lastResponseType=responseValue==null?"missing":responseValue==JSONObject.NULL?"null":responseValue.getClass().getSimpleName();
                String code=data.optString("statusCode");lastResponseCode=code.isEmpty()?"missing-or-empty":code.matches("[A-Za-z0-9_-]{1,24}")?code:"other";
                if(!pending||!("s95d-"+serial).equals(data.optString("seqId"))){ignoredReplies++;return;}
                // Relay/ack messages are not source results. Only the success result is parsed.
                if(code.isEmpty()||code.equals("null")){ignoredNotifications++;return;}
                Snapshot snapshot=Snapshot.parse(data);latest=snapshot;snapshotAt=System.currentTimeMillis();pending=false;matchedReplies++;stage="received snapshot";listener.snapshot(snapshot);
            }else if(id.equals("notify.update")&&"get.extsources".equals(data.optJSONObject("response")==null?data.optString("reqMessage"):data.optJSONObject("response").optString("reqMessage"))){query();}
        }catch(Exception e){fail("输入状态响应格式无效："+e.getClass().getSimpleName()+"："+DiagnosticSanitizer.clean(e.getMessage()));}
    }
    private synchronized void fail(String reason){
        if(closed)return;failure=reason;closed=true;timer.cancel();closeSocket();listener.unavailable(reason);
    }
    synchronized String diagnostics(){return "launch="+launchState+", launchRequests="+launchSerial+"\nstage="+stage+"\nauthorized="+authorized+", serviceReady="+serviceReady+", closed="+closed+", pending="+pending+", recipient=all\nrequests="+serial+", messages="+receivedMessages+", matched="+matchedReplies+", ignoredSeq="+ignoredReplies+", ignoredNotifications="+ignoredNotifications+", requestEchoes="+requestEchoes+"\nlastEvent="+lastEvent+", dataType="+lastDataType+", responseCode="+lastResponseCode+", responseType="+lastResponseType+"\nfailure="+failure+"\n"+trace.summary();}
    // WebSocket close callbacks hold its internal lock and enter this session. Never
    // take the socket lock while holding the session lock (including receive/fail).
    private void closeSocket(){Thread closer=new Thread(()->socket.close(),"TV-source-close");closer.setDaemon(true);closer.start();}
    synchronized void cancel(){if(closed)return;closed=true;timer.cancel();closeSocket();}
}
