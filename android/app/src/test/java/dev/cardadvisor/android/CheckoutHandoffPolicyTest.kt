package dev.cardadvisor.android
import org.junit.Test
import org.junit.Assert.*
class CheckoutHandoffPolicyTest {
 private fun policy()=CheckoutHandoffPolicy("own",setOf("shop"))
 @Test fun recentsToOwnAppWithinGraceKeepsOnlyUnconfirmedCandidate(){val p=policy();assertTrue(p.keep("system",true,true,100));assertTrue(p.keep(null,true,false,200));assertTrue(p.keep("own",true,false,300));assertTrue(p.keep("own",true,false,9000))}
 @Test fun navigationEventsDoNotExtendGrace(){val p=policy();assertTrue(p.keep("system",true,true,0));assertTrue(p.keep("system",true,true,7999));assertFalse(p.keep("own",true,false,8000))}
 @Test fun unrelatedAppsAndMissingOrExpiredCandidateAreDiscarded(){val p=policy();assertFalse(p.keep("other",true,false,0));assertFalse(p.keep("own",false,false,1));assertFalse(p.keep("system",false,true,2))}
 @Test fun freshShoppingObservationStartsANewHandoff(){val p=policy();assertTrue(p.keep("system",true,true,0));assertTrue(p.keep("shop",true,false,2000));assertTrue(p.keep("system",true,true,6000));assertTrue(p.keep("own",true,false,11000))}
}
