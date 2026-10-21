package dev.cardadvisor.android
import org.junit.Test
import org.junit.Assert.*
class CheckoutCandidateStoreTest {
 private var now=0L
 private fun store()=CheckoutCandidateStore(mapOf("shop" to "NAVER_PLUS_STORE")){now}.apply{authorize(true)}
 private fun observation(total:Set<Long> =setOf(50000L),points:Long=0)=CheckoutObservation("shop","v1",true,false,mapOf(DetectionAmount.FINAL_TOTAL to total,DetectionAmount.POINTS to setOf(points)))
 @Test fun changedPointsWithSameFinalTotalNeedNewConfirmation(){val s=store();s.accept(observation());val first=s.current()!!;s.accept(observation(points=1000));assertTrue(s.current()!!.generation>first.generation)}
 @Test fun invalidExtraAmountCannotBeSilentlyDiscarded(){val s=store();s.accept(observation(setOf(50000L,-1L)));assertNull(s.current()!!.amountWon);assertEquals("HELD",s.current()!!.state)}
 @Test fun expiryPublishesNullAndDropsPreviousConfirmation(){val s=store();s.accept(observation());now=119999;assertNotNull(s.current());now=120000;assertNull(s.current());assertNull(s.pending.value)}
 @Test fun continuousIdenticalObservationRefreshesAgeWithoutResettingConfirmation(){val s=store();s.accept(observation());val g=s.current()!!.generation;now=119000;s.accept(observation());now=121000;assertEquals(g,s.current()!!.generation)}
 @Test fun reobservingExpiredCheckoutCreatesANewGeneration(){val s=store();s.accept(observation());val g=s.current()!!.generation;now=120000;s.accept(observation());assertTrue(s.current()!!.generation>g)}
 @Test fun observedInputIsCopiedAndAuthenticationAlwaysClears(){val s=store();val totals=mutableSetOf(50000L);s.accept(observation(totals));totals.add(60000);val g=s.current()!!.generation;s.accept(observation(totals));assertTrue(s.current()!!.generation>g);assertNull(s.current()!!.amountWon);s.accept(observation().copy(authentication=true));assertNull(s.pending.value)}
}
