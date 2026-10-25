package org.s95dhome.launcher;
import android.app.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;
import org.json.JSONObject;
/** Setup probes only local addresses. Captured certificates require user confirmation before use. */
final class TvConnectionSetup {
    interface Listener{void changed();}
    private final Activity activity;private final TvConnectionSettings settings;private final Listener listener;private boolean busy;private int generation;
    TvConnectionSetup(Activity a,TvConnectionSettings s,Listener l){activity=a;settings=s;listener=l;}
    void startFirstUse(){if(!settings.ready())automatic();}
    void show(){
        if(busy){toast("正在识别电视，请稍候");return;}
        new AlertDialog.Builder(activity).setTitle("电视连接").setItems(new String[]{"自动识别本机电视","手动输入电视 IP","清除已保存的电视连接"},(d,i)->{
            if(i==0)automatic();else if(i==1)manual();else new AlertDialog.Builder(activity).setTitle("清除已保存的地址和证书？")
                .setPositiveButton("清除",(x,w)->{settings.clear();listener.changed();}).setNegativeButton("取消",null).show();
        }).setNegativeButton("返回",null).show();
    }
    private void manual(){
        EditText input=new EditText(activity);input.setSingleLine();input.setHint("局域网 IPv4 地址");input.setText(settings.host());
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("连接电视").setView(input).setPositiveButton("识别",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{String h=TvEndpoint.localAddress(input.getText().toString());dialog.dismiss();probe(Collections.singletonList(h));}catch(Exception e){input.setError(e.getMessage());}}));dialog.show();
    }
    private void automatic(){
        List<String> hosts=new ArrayList<>();hosts.add("127.0.0.1");
        try{
            Enumeration<NetworkInterface> nets=NetworkInterface.getNetworkInterfaces();
            while(nets!=null&&nets.hasMoreElements()){
                NetworkInterface net=nets.nextElement();if(!net.isUp()||net.isLoopback())continue;
                Enumeration<InetAddress> values=net.getInetAddresses();
                while(values.hasMoreElements()){InetAddress value=values.nextElement();if(value instanceof Inet4Address)try{String h=TvEndpoint.localAddress(value.getHostAddress());if(!hosts.contains(h)&&hosts.size()<8)hosts.add(h);}catch(Exception ignored){}}
            }
            probe(hosts);
        }catch(Exception e){probe(hosts);}
    }
    private void probe(List<String> hosts){
        if(busy)return;busy=true;final int request=++generation;toast("正在识别电视…");
        new Thread(()->{
            String[] result=null;for(String h:hosts)try{result=inspect(h);break;}catch(Exception ignored){}
            final String[] found=result;activity.runOnUiThread(()->{
                if(request!=generation||activity.isFinishing()||activity.isDestroyed())return;busy=false;
                if(found==null){new AlertDialog.Builder(activity).setTitle("暂未识别到本机电视").setMessage("请确认电视网络已连接后重试。部分机型或固件可能不支持此接口；高级排查入口位于首页设置的电视连接。")
                    .setPositiveButton("重新识别",(d,w)->automatic()).setNegativeButton("稍后",null).show();return;}
                confirm(found);
            });
        },"TV-setup").start();
    }
    private void confirm(String[] found){
        new AlertDialog.Builder(activity).setTitle("发现本机电视").setMessage("型号："+found[1]+"\n\n允许简洁首页连接本机电视，以显示 HDMI 状态和切换信号源？确认后会保存本次连接，之后自动使用。")
            .setPositiveButton("允许连接",(d,w)->{settings.save(found[0],found[2]);listener.changed();})
            .setNeutralButton("连接详情",(d,w)->new AlertDialog.Builder(activity).setTitle("连接详情").setMessage("地址："+found[0]+"\n\n证书 SHA-256：\n"+found[2]+"\n\n确认后仅信任此证书；证书变化时停止连接并需重新确认。")
                .setPositiveButton("返回确认",(x,y)->confirm(found)).show())
            .setNegativeButton("稍后",null).show();
    }
    private static String[] inspect(String raw)throws Exception{
        String host=TvEndpoint.localAddress(raw);JSONObject device;
        HttpURLConnection conn=(HttpURLConnection)new URL("http://"+host+":8001/api/v2/").openConnection();
        try{
            conn.setConnectTimeout(1500);conn.setReadTimeout(1500);conn.setInstanceFollowRedirects(false);
            if(conn.getResponseCode()!=200)throw new IOException("Not a TV endpoint");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=conn.getInputStream()){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>65536)throw new IOException("Oversize response");bytes.write(b,0,n);}}
            device=new JSONObject(new String(bytes.toByteArray(),"UTF-8")).getJSONObject("device");
            if(!device.optString("OS",device.optString("os")).toLowerCase(Locale.ROOT).contains("tizen"))throw new IOException("Not a Tizen TV");
        }finally{conn.disconnect();}
        final X509Certificate[] leaf=new X509Certificate[1];
        X509TrustManager capture=new X509TrustManager(){
            public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{throw new CertificateException("Unsupported");}
            public void checkServerTrusted(X509Certificate[] c,String a)throws CertificateException{if(c==null||c.length==0)throw new CertificateException("Missing certificate");c[0].checkValidity();leaf[0]=c[0];}
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
        };
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,new TrustManager[]{capture},null);
        try(SSLSocket socket=(SSLSocket)tls.getSocketFactory().createSocket()){socket.connect(new InetSocketAddress(host,8002),2500);socket.setSoTimeout(2500);socket.startHandshake();}
        if(leaf[0]==null)throw new CertificateException("Missing certificate");
        String model=device.optString("modelName",device.optString("model","Tizen TV"));
        return new String[]{host,model.matches("[A-Za-z0-9 ._-]{1,96}")?model:"Tizen TV",PinnedTvTls.fingerprint(leaf[0])};
    }
    private void toast(String text){Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
    void cancel(){generation++;busy=false;}
}
