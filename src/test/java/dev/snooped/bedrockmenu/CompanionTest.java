package dev.snooped.bedrockmenu;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CompanionTest {
    @TempDir Path directory;
    @Test void privatePipeRequestsRestartPendingCancellationAndExitCleanup() throws Exception {
        Path runtime=directory.resolve("fake-java"),jar=directory.resolve("service.jar"),release=directory.resolve("release.json"),config=directory.resolve("config.json");
        Files.writeString(runtime,"""
                #!/usr/bin/python3
                import sys,json,os
                for line in sys.stdin:
                    r=json.loads(line)
                    if r['method']=='shutdown': break
                    if r['method']=='hang': continue
                    print(json.dumps({'id':r['id'],'result':{'pid':os.getpid(),'method':r['method']}}),flush=True)
                """);Files.setPosixFilePermissions(runtime,PosixFilePermissions.fromString("rwx------"));Files.writeString(jar,"");
        JsonObject descriptor=new JsonObject();descriptor.addProperty("java",runtime.toString());descriptor.addProperty("jar",jar.toString());Files.writeString(release,descriptor.toString());
        JsonObject settings=new JsonObject();settings.addProperty("release",release.toString());settings.addProperty("profile",directory.resolve("profile").toString());Files.writeString(config,settings.toString());
        Companion companion=new Companion(config);long first=companion.request("status").get(5,TimeUnit.SECONDS).get("pid").getAsLong();
        var a=companion.request("one");var b=companion.request("two");assertEquals("one",a.get(5,TimeUnit.SECONDS).get("method").getAsString());assertEquals("two",b.get(5,TimeUnit.SECONDS).get("method").getAsString());
        var pending=companion.request("hang");companion.restart().get(10,TimeUnit.SECONDS);assertThrows(ExecutionException.class,()->pending.get(5,TimeUnit.SECONDS));assertFalse(ProcessHandle.of(first).map(ProcessHandle::isAlive).orElse(false));
        long second=companion.request("status").get(5,TimeUnit.SECONDS).get("pid").getAsLong();assertNotEquals(first,second);companion.close();assertFalse(ProcessHandle.of(second).map(ProcessHandle::isAlive).orElse(false));
        assertThrows(ExecutionException.class,()->companion.request("status").get());
    }
}
