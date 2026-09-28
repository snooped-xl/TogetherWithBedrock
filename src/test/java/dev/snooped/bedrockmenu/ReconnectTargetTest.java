package dev.snooped.bedrockmenu;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReconnectTargetTest {
    @Test void meteorSavedLoopbackUsesRealmIdentityAfterProxyPortHasClosed() {
        assertEquals(new ReconnectTarget("realm","12345"),ReconnectTarget.resolve("127.0.0.1:51342","realm-12345.00000000-0000-0000-0000-000000000001.bedrock.local"));
    }
    @Test void destinationsCoverServerWorldHostAndInviteCodeAndKeepDirectHostingSeparate() {
        for(String kind:new String[]{"server","world","hosted","host"}) {
            String id="00000000-0000-0000-0000-000000000002";
            assertEquals(new ReconnectTarget(kind,id),ReconnectTarget.resolve("127.0.0.1",kind+"-"+id+".bedrock.local"));
        }
        assertEquals(new ReconnectTarget("realmcode","abcxyz123"),ReconnectTarget.parse("realmcode-abcxyz123.bedrock.local"));
    }
    @Test void addressOnlyReconnectStillWorksAndRegularJavaNeverRoutesThroughProxy() {
        assertEquals(new ReconnectTarget("realm","123"),ReconnectTarget.resolve("realm-123.bedrock.local",null));
        for(String server:new String[]{"example.org","127.0.0.1:25565","localhost","[::1]:25565","realm-123.bedrock.local.example.org"})assertNull(ReconnectTarget.resolve(server,server));
        assertNull(ReconnectTarget.parse(null));assertNull(ReconnectTarget.parse(""));
    }
}
