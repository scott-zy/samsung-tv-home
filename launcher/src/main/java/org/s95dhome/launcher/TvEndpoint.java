package org.s95dhome.launcher;
import java.net.URI;
final class TvEndpoint {
    static String localAddress(String value){
        if(value==null||!value.trim().matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))throw new IllegalArgumentException("请输入局域网 IPv4 地址");
        String[] p=value.trim().split("\\.");int[] n=new int[4];for(int i=0;i<4;i++){n[i]=Integer.parseInt(p[i]);if(n[i]>255)throw new IllegalArgumentException("IP 地址无效");}
        boolean local=n[0]==10||n[0]==127||(n[0]==172&&n[1]>=16&&n[1]<=31)||(n[0]==192&&n[1]==168)||(n[0]==169&&n[1]==254);
        if(!local||n[3]==0||n[3]==255)throw new IllegalArgumentException("仅支持局域网或本机地址");
        return n[0]+"."+n[1]+"."+n[2]+"."+n[3];
    }
    static URI channel(String host)throws Exception{return new URI("wss",null,localAddress(host),8002,"/api/v2/channels/com.samsung.tv.mobilebff","name=U2Ftc3VuZyBUViBIb21l",null);}
}
