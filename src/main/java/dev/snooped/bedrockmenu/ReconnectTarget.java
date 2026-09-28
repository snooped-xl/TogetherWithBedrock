package dev.snooped.bedrockmenu;

import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Resolve a saved logical destination even when a reconnect mod retained an expired proxy port. */
public record ReconnectTarget(String kind,String id) {
    private static final Pattern IDENTITY=Pattern.compile("^(server|world|realm|realmcode|hosted|host)-([a-z0-9-]+)(?:\\.[a-f0-9-]+)?\\.bedrock\\.local$");
    static ReconnectTarget parse(String value) {
        if(value==null || value.isBlank()) return null;
        var match=IDENTITY.matcher(ServerAddress.parseString(value).getHost().toLowerCase(Locale.ROOT));
        if(!match.matches()) return null;
        return new ReconnectTarget(match.group(1),match.group(2));
    }
    static ReconnectTarget resolve(String transport,String savedIdentity) {
        ReconnectTarget saved=parse(savedIdentity);
        return saved!=null?saved:parse(transport);
    }
}
