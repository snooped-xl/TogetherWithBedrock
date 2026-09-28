package dev.snooped.bedrockmenu;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FriendRequestNotificationsTest {
    @TempDir Path directory;

    private JsonObject person(String xuid,String relationship,boolean friend) {
        JsonObject row=new JsonObject();row.addProperty("xuid",xuid);row.addProperty("name","Player "+xuid);
        row.addProperty("relationship",relationship);row.addProperty("isFriend",friend);return row;
    }
    private JsonObject snapshot(String account,long second,JsonObject... incoming) {
        JsonObject data=new JsonObject();data.addProperty("accountXuid",account);data.addProperty("updatedAt",Instant.ofEpochSecond(second).toString());
        JsonArray rows=new JsonArray();for(JsonObject row:incoming)rows.add(row);data.add("incoming",rows);data.add("friends",new JsonArray());data.add("outgoing",new JsonArray());return data;
    }
    private FriendRequestNotifications notifications(){return new FriendRequestNotifications(directory.resolve("notifications.json"));}

    @Test void onlyIncomingNonFriendsNotifyAndRepeatedPollsAreDeduplicated() {
        FriendRequestNotifications notices=notifications();notices.setAccount("100");
        JsonObject data=snapshot("100",1,person("201","incoming",false),person("201","incoming",false),
                person("202","incoming",true),person("203","outgoing",false),person("204","incoming",false));
        data.getAsJsonArray("friends").add(person("204","friend",true));data.getAsJsonArray("outgoing").add(person("205","outgoing",false));
        assertTrue(notices.accept("100",data));assertEquals(Set.of("201"),notices.pendingIds());
        InviteBanners banners=new InviteBanners();banners.acceptFriendRequests(notices.unseen());banners.acceptFriendRequests(notices.unseen());
        List<String> seen=new ArrayList<>();banners.tick(true,1000,id->{seen.add(id);notices.shown(id);});
        assertTrue(InviteBanners.isFriendRequest(banners.current()));assertEquals(List.of("201"),seen);
        notices.accept("100",data);assertTrue(notices.unseen().isEmpty());banners.dismiss();banners.tick(true,2000,seen::add);assertNull(banners.current());
    }

    @Test void seenRequestsSurviveRestartButQueuedUnseenRequestsStillNotify() {
        JsonObject data=snapshot("100",1,person("201","incoming",false),person("202","incoming",false));
        FriendRequestNotifications first=notifications();first.setAccount("100");first.accept("100",data);first.shown("201");
        FriendRequestNotifications restarted=notifications();restarted.setAccount("100");restarted.accept("100",data);
        assertEquals(1,restarted.unseen().size());assertEquals("202",restarted.unseen().get(0).getAsJsonObject().get("xuid").getAsString());
        restarted.viewedRequests();FriendRequestNotifications third=notifications();third.setAccount("100");third.accept("100",data);assertTrue(third.unseen().isEmpty());
    }

    @Test void switchingAccountsRejectsStaleRepliesAndKeepsSeenHistorySeparate() {
        FriendRequestNotifications notices=notifications();notices.setAccount("100");
        JsonObject first=snapshot("100",1,person("201","incoming",false));notices.accept("100",first);notices.shown("201");
        assertTrue(notices.setAccount("101"));assertTrue(notices.pendingIds().isEmpty());assertFalse(notices.accept("100",first));
        assertFalse(notices.accept("101",first));assertTrue(notices.accept("101",snapshot("101",2,person("201","incoming",false))));assertEquals(1,notices.unseen().size());
        notices.setAccount("100");notices.accept("100",first);assertTrue(notices.unseen().isEmpty());
        notices.setAccount("");assertFalse(notices.accept("100",first));assertTrue(notices.pendingIds().isEmpty());
    }

    @Test void withdrawnRequestsLeaveQueueAndLaterNewRequestsCanNotify() {
        FriendRequestNotifications notices=notifications();notices.setAccount("100");InviteBanners banners=new InviteBanners();
        JsonObject first=snapshot("100",1,person("201","incoming",false));notices.accept("100",first);banners.acceptFriendRequests(notices.unseen());banners.tick(true,1000,notices::shown);
        assertTrue(notices.accept("100",snapshot("100",2)));banners.retainFriendRequests(notices.pendingIds());assertNull(banners.current());
        assertFalse(notices.accept("100",first));assertTrue(notices.pendingIds().isEmpty());
        notices.accept("100",snapshot("100",3,person("201","incoming",false)));banners.acceptFriendRequests(notices.unseen());banners.tick(true,2000,notices::shown);assertNotNull(banners.current());
    }

    @Test void friendBannerUsesThirtyMainMenuSecondsAndLeavesRequestDataIntact() {
        FriendRequestNotifications notices=notifications();notices.setAccount("100");JsonObject data=snapshot("100",1,person("201","incoming",false));notices.accept("100",data);
        InviteBanners banners=new InviteBanners();banners.acceptFriendRequests(notices.unseen());banners.tick(false,1000,notices::shown);assertNull(banners.current());
        banners.tick(true,2000,notices::shown);for(long now=3000;now<=100000;now+=1000)banners.tick(false,now,notices::shown);assertEquals(30,banners.seconds());
        for(long now=101000;now<=130000;now+=1000)banners.tick(true,now,notices::shown);assertNull(banners.current());
        assertEquals(1,data.getAsJsonArray("incoming").size());assertEquals(Set.of("201"),notices.pendingIds());
    }

    @Test void malformedSnapshotDoesNotForgetSeenHistory() {
        FriendRequestNotifications notices=notifications();notices.setAccount("100");JsonObject data=snapshot("100",1,person("201","incoming",false));notices.accept("100",data);notices.shown("201");
        JsonObject malformed=snapshot("100",2);malformed.remove("incoming");assertFalse(notices.accept("100",malformed));
        notices.accept("100",data);assertTrue(notices.unseen().isEmpty());
    }
}
