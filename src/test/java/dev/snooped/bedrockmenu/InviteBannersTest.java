package dev.snooped.bedrockmenu;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InviteBannersTest {
    JsonObject invite(String id,boolean seen,Instant expiry){JsonObject j=new JsonObject();j.addProperty("id",id);j.addProperty("seen",seen);j.addProperty("expires",expiry.toString());return j;}
    @Test void onlyUsesMainMenuTimeAndRetainsInboxInput(){
        InviteBanners banners=new InviteBanners();JsonArray inbox=new JsonArray();inbox.add(invite("one",false,Instant.now().plusSeconds(300)));List<String> seen=new ArrayList<>();
        banners.accept(inbox);banners.tick(false,1000,seen::add);assertNull(banners.current());assertTrue(seen.isEmpty());
        banners.tick(true,2000,seen::add);assertEquals(30,banners.seconds());assertEquals(List.of("one"),seen);
        for(long i=3000;i<=100_000;i+=1000)banners.tick(false,i,seen::add);assertEquals(30,banners.seconds());
        for(long i=101_000;i<130_000;i+=1000)banners.tick(true,i,seen::add);assertNotNull(banners.current());
        banners.tick(true,130_000,seen::add);assertNull(banners.current());assertEquals(1,inbox.size());
        banners.accept(inbox);banners.tick(true,131_000,seen::add);assertNull(banners.current());assertEquals(1,seen.size());
    }
    @Test void deduplicatesSkipsExpiredAndPreviouslySeenAndQueuesNext(){
        InviteBanners banners=new InviteBanners();JsonArray inbox=new JsonArray();inbox.add(invite("expired",false,Instant.EPOCH));inbox.add(invite("old",true,Instant.now().plusSeconds(300)));
        inbox.add(invite("a",false,Instant.now().plusSeconds(300)));inbox.add(invite("a",false,Instant.now().plusSeconds(300)));inbox.add(invite("b",false,Instant.now().plusSeconds(300)));
        List<String> seen=new ArrayList<>();banners.accept(inbox);banners.tick(true,1000,seen::add);assertEquals("a",banners.current().get("id").getAsString());
        banners.dismiss();banners.tick(false,2000,seen::add);assertNull(banners.current());banners.tick(true,3000,seen::add);assertEquals("b",banners.current().get("id").getAsString());assertEquals(List.of("a","b"),seen);
    }
    @Test void friendAndWorldNotificationsShareQueueWithoutCollidingOrDismissingEachOther(){
        InviteBanners banners=new InviteBanners();JsonArray requests=new JsonArray();JsonObject request=new JsonObject();request.addProperty("xuid","123");requests.add(request);
        JsonArray inbox=new JsonArray();inbox.add(invite("123",false,Instant.now().plusSeconds(300)));
        banners.acceptFriendRequests(requests);banners.accept(inbox);banners.tick(true,1000,id->{});assertTrue(InviteBanners.isFriendRequest(banners.current()));
        banners.clearFriendRequests();banners.tick(true,2000,id->{});assertFalse(InviteBanners.isFriendRequest(banners.current()));assertEquals("123",banners.current().get("id").getAsString());
        banners.retainFriendRequests(Set.of());assertNotNull(banners.current());banners.clear();assertNull(banners.current());banners.tick(true,3000,id->{});assertNull(banners.current());
    }
}
