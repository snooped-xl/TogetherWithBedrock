// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Owns just our child, with inherited private pipes instead of an HTTP control port. */
public final class Companion implements AutoCloseable {
    private final ExecutorService starter=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Bedrock companion control");t.setDaemon(true);return t;});
    private final AtomicLong ids=new AtomicLong();
    private final Map<Long,CompletableFuture<JsonObject>> pending=new ConcurrentHashMap<>();
    private volatile Process process;
    private BufferedWriter input;
    private volatile boolean closed;
    private volatile Path profile;
    public Companion(){try{profile=CompanionBundle.profile();}catch(Exception ignored){}}
    public Path profile(){return profile;}
    public CompletableFuture<JsonObject> request(String method){return request(method,new JsonObject());}
    public CompletableFuture<JsonObject> request(String method,JsonObject params) {
        CompletableFuture<JsonObject> future=new CompletableFuture<>();
        if(closed)return CompletableFuture.failedFuture(new IOException("Bedrock companion is closed"));
        try{starter.execute(()->{
            try {
                ensureStarted();long id=ids.incrementAndGet();pending.put(id,future);
                future.orTimeout(95,TimeUnit.SECONDS).whenComplete((value,error)->pending.remove(id));
                JsonObject message=new JsonObject();message.addProperty("id",id);message.addProperty("method",method);message.add("params",params);
                synchronized(this){input.write(message.toString());input.newLine();input.flush();}
            }catch(Exception e){future.completeExceptionally(e);}
        });}catch(RejectedExecutionException e){future.completeExceptionally(new IOException("Bedrock companion is closing"));}
        return future;
    }
    private synchronized void ensureStarted() throws Exception {
        if(closed)throw new IOException("Bedrock companion is closed");
        if(process!=null&&process.isAlive())return;
        // Materialize the embedded service build and its launch descriptor inside
        // the game instance. Everything runs with the Java runtime the game uses.
        Path descriptor=CompanionBundle.install();
        profile=CompanionBundle.profile();
        try {
            if(Files.getFileStore(profile).supportsFileAttributeView(PosixFileAttributeView.class))
                Files.setPosixFilePermissions(profile,PosixFilePermissions.fromString("rwx------"));
        } catch(Exception ignored) {} // Best effort only; Windows needs no POSIX modes.
        JsonObject release=JsonParser.parseString(Files.readString(descriptor)).getAsJsonObject();
        Path java=Path.of(release.get("java25").getAsString()),jar=Path.of(release.get("jar").getAsString());
        if(!Files.isExecutable(java)||!Files.isRegularFile(jar))throw new IOException("The embedded companion build is damaged; reinstall the mod");
        Path log=profile.resolve("companion.log");if(Files.exists(log))Files.move(log,profile.resolve("companion-previous.log"),StandardCopyOption.REPLACE_EXISTING);
        Process child=new ProcessBuilder(java.toString(),"-Xms32m","-Xmx384m","-Dviabedrock.protocol=2193","-cp",jar.toString(),
                "net.raphimc.viaproxy.bedrock.menu.MenuService",profile.toString(),descriptor.toString(),Long.toString(ProcessHandle.current().pid()))
                .directory(profile.toFile()).redirectError(log.toFile()).start();
        process=child;input=new BufferedWriter(new OutputStreamWriter(child.getOutputStream(),StandardCharsets.UTF_8));
        Thread reader=new Thread(()->read(child),"Bedrock companion replies");reader.setDaemon(true);reader.start();
    }
    private void read(Process child) {
        try(Reader reader=new InputStreamReader(child.getInputStream(),StandardCharsets.UTF_8)) {
            StringBuilder line=new StringBuilder();int c;
            while((c=reader.read())!=-1) {
                if(c!='\n'){if(line.length()>2_097_152)throw new IOException("Oversized companion response");line.append((char)c);continue;}
                JsonObject response=JsonParser.parseString(line.toString()).getAsJsonObject();line.setLength(0);
                var future=pending.remove(response.get("id").getAsLong());if(future==null)continue;
                if(response.has("error"))future.completeExceptionally(new IOException(response.get("error").getAsString()));
                else future.complete(response.getAsJsonObject("result"));
            }
        }catch(Exception ignored) { }
        finally {
            synchronized(this){if(process==child){process=null;failPending("The Bedrock companion stopped. Open Bedrock → Proxy to restart it.");}}
            if(child.isAlive())child.destroy();
        }
    }
    private void failPending(String message){pending.values().forEach(f->f.completeExceptionally(new IOException(message)));pending.clear();}
    public CompletableFuture<Void> restart() {
        return CompletableFuture.runAsync(()->{stopProcess();try{ensureStarted();}catch(Exception e){throw new CompletionException(e);}},starter);
    }
    private void stopProcess() {
        Process child;
        synchronized(this) {
            child=process;if(child==null)return;process=null;failPending("The Bedrock service is restarting.");
            try{input.write("{\"id\":0,\"method\":\"shutdown\"}\n");input.flush();input.close();}catch(Exception ignored){}
        }
        try{if(!child.waitFor(20,TimeUnit.SECONDS)){child.destroy();if(!child.waitFor(3,TimeUnit.SECONDS))child.destroyForcibly();}}
        catch(InterruptedException e){child.destroyForcibly();Thread.currentThread().interrupt();}
    }
    @Override public void close(){closed=true;stopProcess();starter.shutdownNow();}
    public static String error(Throwable error){while(error.getCause()!=null&&(error instanceof CompletionException||error instanceof ExecutionException))error=error.getCause();return error instanceof TimeoutException?"The service took too long to reply. Retry or restart it from Proxy.":error.getMessage()==null?"The Bedrock service could not complete the request.":error.getMessage();}
}
