package org.s95dhome.launcher;
import org.s95dhome.privacy.DiagnosticSanitizer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.Framedata;
import org.json.JSONObject;

/** Read-only protocol breadcrumbs. The original strict parser still accepts/rejects every frame. */
final class TvRemoteDraft extends Draft_6455 {
    static final class Trace {
        private byte[] pending = new byte[0];
        private boolean observing = true;
        private String event = "无", peerClose = "未收到";
        synchronized void observe(ByteBuffer incoming) {
            if (!observing) return;
            ByteBuffer copy = incoming.duplicate();
            if (pending.length + copy.remaining() > 65536) { observing = false; pending = new byte[0]; return; }
            int oldLength = pending.length;
            pending = Arrays.copyOf(pending, oldLength + copy.remaining());
            copy.get(pending, oldLength, copy.remaining());
            int offset = 0;
            while (pending.length - offset >= 2) {
                int first = pending[offset] & 255, second = pending[offset + 1] & 255;
                // Never interpret masked, extended or oversized data as a trustworthy event.
                if ((first & 112) != 0 || (second & 128) != 0) { observing = false; break; }
                int length = second & 127, header = 2;
                if (length == 127) { observing = false; break; }
                if (length == 126) {
                    if (pending.length - offset < 4) break;
                    length = ((pending[offset+2] & 255) << 8) | (pending[offset+3] & 255); header = 4;
                }
                if (pending.length - offset < header + length) break;
                int body = offset + header, opcode = first & 15;
                if (opcode == 8 && length <= 125) {
                    int code = length >= 2 ? ((pending[body] & 255) << 8) | (pending[body+1] & 255) : 1005;
                    String reason = length > 2 ? new String(pending, body + 2, length - 2, StandardCharsets.UTF_8) : "";
                    peerClose = code + ": " + DiagnosticSanitizer.clean(reason);
                } else if (opcode == 1 && (first & 128) != 0) {
                    try {
                        String name = new JSONObject(new String(pending, body, length, StandardCharsets.UTF_8)).optString("event", "");
                        if (name.equals("ms.channel.connect") || name.equals("ms.channel.unauthorized")
                            || name.equals("ms.channel.clientConnect") || name.equals("ms.error")) event = name;
                    } catch (Exception ignored) { /* No raw message or token is logged. */ }
                }
                offset += header + length;
            }
            pending = observing ? Arrays.copyOfRange(pending, offset, pending.length) : new byte[0];
        }
        synchronized String summary() { return "最近协议事件=" + event + "；电视关闭帧=" + peerClose; }
    }
    private final Trace trace;
    TvRemoteDraft(Trace trace) { super(java.util.Collections.emptyList(), 65536); this.trace = trace; }
    @Override public List<Framedata> translateFrame(ByteBuffer buffer) throws InvalidDataException {
        trace.observe(buffer);
        return super.translateFrame(buffer);
    }
    @Override public Draft copyInstance() { return new TvRemoteDraft(trace); }
}
