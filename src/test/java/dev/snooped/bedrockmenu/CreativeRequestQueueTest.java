package dev.snooped.bedrockmenu;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CreativeRequestQueueTest {
 @Test void rejectionCannotBeOvertakenByAnUnrelatedLaterClick() {
  var q=new CreativeRequestQueue<String>();
  assertEquals("pickup",q.offer(1,"pickup").value()); assertNull(q.offer(2,"unrelated slot")); assertEquals(2,q.size());
  var result=q.complete(1,false); assertTrue(result.matched());assertNull(result.next());assertFalse(q.pending());assertEquals(0,q.size());
  assertFalse(q.complete(2,true).matched());
  assertEquals("new corrected click",q.offer(3,"new corrected click").value());
 }
 @Test void successfulClicksStayInOrderAndOnlyFinalAckIsIdle() {
  var q=new CreativeRequestQueue<String>();q.offer(1,"pick");q.offer(2,"split");q.offer(3,"place");
  assertEquals("split",q.complete(1,true).next().value());assertTrue(q.pending());
  assertEquals("place",q.complete(2,true).next().value());assertTrue(q.pending());
  assertNull(q.complete(3,true).next());assertFalse(q.pending());
 }
 @Test void staleRepliesCannotAdvanceTheQueue() {
  var q=new CreativeRequestQueue<String>();q.offer(2,"active");q.offer(3,"next");
  assertFalse(q.complete(1,false).matched());assertEquals(2,q.size());assertTrue(q.pending());
 }
 @Test void queueHasABoundAndDisconnectResetsIt() {
  var q=new CreativeRequestQueue<Integer>();for(int i=1;i<=64;i++)q.offer(i,i);
  assertThrows(IllegalStateException.class,()->q.offer(65,65));assertEquals(64,q.size());
  q.clear();assertFalse(q.pending());assertEquals(0,q.size());assertEquals(1,q.offer(1,1).sequence());
 }
}
