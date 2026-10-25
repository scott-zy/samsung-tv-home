package org.s95dhome.launcher;
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Synthetic parsing tests; optional --live sends reads only to the fixed, pinned TV. */
public final class TvSourceSessionTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static JSONObject response(String records) throws Exception {
        return new JSONObject("{\"id\":\"get.extsources\",\"statusCode\":\"100\",\"response\":"+records+"}");
    }
    public static void main(String[] args) throws Exception {
        check(TvSourceSession.decodeData(null)==null,"missing service notification data is ignored");
        check(TvSourceSession.decodeData(JSONObject.NULL)==null,"JSON null service notification is ignored");
        check(TvSourceSession.decodeData("null")==null,"string null notification is ignored");
        check(TvSourceSession.decodeData("ready")==null,"non-object notification cannot terminate a source read");
        check(TvSourceSession.decodeData("{\"id\":\"notify.service.status\"}").optString("id").equals("notify.service.status"),"JSON string notification decoded");
        check(TvSourceSession.isRequestEcho(new JSONObject("{\"id\":\"get.extsources\",\"seqId\":\"s95d-1\",\"params\":{}}")),"request echo is not a rejected source response");
        check(!TvSourceSession.isRequestEcho(response("[]")),"success reply is not filtered as echo");
        JSONObject fixture=response("[{\"sourceId\":\"s/source.layer.hdmi@HDMI2\",\"powerState\":\"2\",\"currentSource\":\"true\"},{\"sourceId\":\"s/source.layer.usb@USB/example\",\"powerState\":\"1\"},{\"sourceId\":\"s/source.layer.hdmi@HDMI1\",\"powerState\":\"0\"}]");
        TvSourceSession.Snapshot s=TvSourceSession.Snapshot.parse(fixture);
        check(s.present[0]&&s.present[1]&&!s.present[2],"map by port id, never list order");
        check(s.current[1]&&s.power[1].equals("2"),"retain raw state for calibration");
        check(s.states("","")[1].equals("detected"),"unverified power code must not become live signal");
        check(s.states("2","")[1].equals("signal"),"explicitly verified active mapping");
        check(s.states("","2")[1].equals("connected"),"non-current source is connected, not assumed asleep");
        check(s.states("1","2")[1].equals("connected"),"code 2 must not claim a valid video signal or standby");
        TvSourceSession.Snapshot active=TvSourceSession.Snapshot.parse(response("[{\"sourceId\":\"s/source.layer.hdmi@HDMI2\",\"powerState\":\"1\",\"currentSource\":\"true\"}]"));
        check(active.states("1","2")[1].equals("signal"),"real-TV active code 1 lights green");
        check(active.states("1","2")[0].equals("unknown"),"missing source cannot inherit prior live state");
        check(s.states("2","")[2].equals("unknown"),"unreported port is unknown, not disconnected");
        fixture.put("statusCode","500");
        try{TvSourceSession.Snapshot.parse(fixture);throw new AssertionError("error response accepted");}catch(IllegalArgumentException expected){}
        JSONObject dup=response("[{\"sourceId\":\"s/source.layer.hdmi@HDMI2\"},{\"sourceId\":\"s/source.layer.hdmi@HDMI2\"}]");
        try{TvSourceSession.Snapshot.parse(dup);throw new AssertionError("duplicate port accepted");}catch(IllegalArgumentException expected){}
        System.out.println("PASS: port IDs/order, USB exclusion, explicit calibration, absent/unknown states, rejected status and duplicate ports");
    }
}
