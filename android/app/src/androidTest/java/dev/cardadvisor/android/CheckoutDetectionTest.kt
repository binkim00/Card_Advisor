package dev.cardadvisor.android
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckoutDetectionTest {
 @After fun clear(){CheckoutDetection.authorize(false)}
 private fun observation(pkg:String,values:Set<Long>,auth:Boolean=false,title:Boolean=true)=CheckoutObservation(pkg,"synthetic-version",title,auth,mapOf(DetectionAmount.FINAL_TOTAL to values))
 @Test fun supportedRoutesRequireConfirmationAndInvalidateOnEveryChange(){
  CheckoutDetection.authorize(true)
  for(pkg in CheckoutDetection.packages.keys)repeat(35){i->
   CheckoutDetection.accept(observation(pkg,setOf(50000L+i)));val old=CheckoutDetection.current()!!;assertEquals("NEEDS_CONFIRMATION",old.state);assertEquals("UNVERIFIED_ROUTE_CONFIRM_REQUIRED",old.reason)
   CheckoutDetection.accept(observation(pkg,setOf(60000L+i)));assertTrue(CheckoutDetection.current()!!.generation>old.generation)
   CheckoutDetection.accept(observation(pkg,setOf(50000L,60000L)));assertEquals("HELD",CheckoutDetection.current()!!.state);assertNull(CheckoutDetection.current()!!.amountWon)
   CheckoutDetection.accept(observation(pkg,setOf(100L),auth=true));assertNull(CheckoutDetection.current())
   CheckoutDetection.accept(observation(pkg,emptySet()));assertEquals("HELD",CheckoutDetection.current()!!.state)
   CheckoutDetection.accept(observation(pkg,setOf(100L),title=false));assertNull(CheckoutDetection.current())
  }
  CheckoutDetection.accept(observation("unapproved.package",setOf(1L)));assertNull(CheckoutDetection.current())
  CheckoutDetection.authorize(false);CheckoutDetection.accept(observation(CheckoutDetection.packages.keys.first(),setOf(1L)));assertNull(CheckoutDetection.current())
 }
 @Test fun sameSessionRestorePreservesCandidateButLogoutDoesNot(){
  CheckoutDetection.authorize(true)
  CheckoutDetection.accept(observation("com.navercorp.navershopping",setOf(50000L)))
  val candidate=CheckoutDetection.current()!!
  CheckoutDetection.authorize(true)
  assertEquals(candidate.generation,CheckoutDetection.current()!!.generation)
  CheckoutDetection.authorize(false)
  assertNull(CheckoutDetection.current())
  CheckoutDetection.authorize(true)
  assertNull(CheckoutDetection.current())
 }
}
