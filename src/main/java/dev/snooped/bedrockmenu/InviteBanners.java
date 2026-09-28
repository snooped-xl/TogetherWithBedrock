// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;

/** A notification gets thirty seconds of main-menu time, never gameplay overlay time. */
public final class InviteBanners {
    private final Set<String> shown=new HashSet<>();
    private final ArrayDeque<JsonObject> queue=new ArrayDeque<>();
    private JsonObject current;
    private long remaining,lastTick;
    public void accept(JsonArray invitations) {
        for(JsonElement element:invitations) {
            if(!element.isJsonObject())continue;
            JsonObject invite=element.getAsJsonObject();String id=invite.get("id").getAsString();
            if(invite.has("seen")&&invite.get("seen").getAsBoolean())continue;
            if(expired(invite)||shown.contains("world:"+id)||queue.size()>=100)continue;
            JsonObject notification=invite.deepCopy();notification.addProperty("notificationType","world");
            shown.add("world:"+id);queue.add(notification);
        }
    }
    public void acceptFriendRequests(JsonArray requests) {
        for(JsonElement element:requests) {
            if(!element.isJsonObject())continue;
            JsonObject request=element.getAsJsonObject();String xuid=request.get("xuid").getAsString();
            if(shown.contains("friend:"+xuid)||queue.size()>=100)continue;
            JsonObject notification=request.deepCopy();notification.addProperty("id",xuid);notification.addProperty("notificationType","friend");
            shown.add("friend:"+xuid);queue.add(notification);
        }
    }
    public void retainFriendRequests(Set<String> pending) {
        if(current!=null&&isFriendRequest(current)&&!pending.contains(current.get("id").getAsString()))current=null;
        queue.removeIf(value->isFriendRequest(value)&&!pending.contains(value.get("id").getAsString()));
        shown.removeIf(id->id.startsWith("friend:")&&!pending.contains(id.substring(7)));
    }
    public void clearFriendRequests() {
        if(current!=null&&isFriendRequest(current))current=null;
        queue.removeIf(InviteBanners::isFriendRequest);
    }
    public static boolean isFriendRequest(JsonObject notification) {
        return notification.has("notificationType")&&"friend".equals(notification.get("notificationType").getAsString());
    }
    public void tick(boolean titleScreen,long now,Consumer<String> onShown) {
        long elapsed=lastTick==0?0:Math.max(0,Math.min(1000,now-lastTick));lastTick=now;
        if(current!=null&&(expired(current)||(titleScreen&&(remaining-=elapsed)<=0)))current=null;
        if(titleScreen&&current==null)while(!queue.isEmpty()) {
            JsonObject next=queue.remove();if(expired(next))continue;
            current=next;remaining=30_000;onShown.accept(current.get("id").getAsString());break;
        }
    }
    public JsonObject current(){return current;}
    public int seconds(){return (int)Math.ceil(remaining/1000.0);}
    public void dismiss(){current=null;}
    public void clear(){current=null;queue.clear();shown.clear();remaining=0;lastTick=0;}
    public static boolean expired(JsonObject invite) {
        if(isFriendRequest(invite))return false;
        try {String expiry=invite.get("expires").getAsString();return !expiry.isBlank()&&!java.time.Instant.parse(expiry).isAfter(java.time.Instant.now());}
        catch(Exception e){return true;}
    }
}
