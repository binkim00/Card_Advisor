package dev.cardadvisor.domain;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import java.util.*;
import dev.cardadvisor.domain.RecommendationEngine.*;
class RecommendationEngineTest {
 final RecommendationEngine e=new RecommendationEngine();
 final List<Card> cards=List.of(new Card("A","A",300,true,300000L,300000L,10000L,true,"SYNTHETIC"),new Card("B","B",100,true,270000L,300000L,10000L,true,"SYNTHETIC"));
 final Checkout checkout=new Checkout(50000,0,0,50000,"SHOPPING","NAVER_PAY_CARD",1,"NO",true);
 final Budget budget=new Budget(120000,0,70000L,0L,30000);
 @Test void immediate(){var r=e.evaluate(cards,checkout,budget,Mode.BENEFIT,0);assertEquals("A",r.recommendedCardId());assertEquals(1500L,r.candidates().get(0).benefitWon());}
 @Test void performance(){var r=e.evaluate(cards,checkout,budget,Mode.PERFORMANCE,0);assertEquals("B",r.recommendedCardId());var b=r.candidates().stream().filter(c->c.cardId().equals("B")).findFirst().orElseThrow();assertEquals(320000L,b.performanceAfterWon());assertEquals(1000L,b.opportunityCostWon());}
 @Test void balancedRequiresConsent(){assertEquals("A",e.evaluate(cards,checkout,budget,Mode.BALANCED,0).recommendedCardId());assertEquals("B",e.evaluate(cards,checkout,budget,Mode.BALANCED,1000).recommendedCardId());}
 @Test void budgets(){var r=e.evaluate(cards,checkout,budget,Mode.BENEFIT,0);assertEquals(70000,r.overallAfterWon());assertEquals(20000L,r.categoryAfterWon());assertEquals(40000,r.availableAfterWon());}
 @Test void mixedTender(){var c=new Checkout(60000,5000,10000,45000,"SHOPPING","NAVER_PAY_CARD",1,"NO",true);assertEquals(55000,c.expenseWon());assertEquals(1350L,e.evaluate(cards,c,budget,Mode.BENEFIT,0).candidates().get(0).benefitWon());}
 @Test void cap(){var c=new Card("C","C",300,true,0L,300000L,700L,true,"SYNTHETIC");assertEquals(700L,e.evaluate(List.of(c),checkout,budget,Mode.BENEFIT,0).candidates().get(0).benefitWon());}
 @Test void excludedPerformance(){var c=new Card("C","C",100,true,270000L,300000L,700L,false,"SYNTHETIC");var r=e.evaluate(List.of(c),checkout,budget,Mode.PERFORMANCE,0).candidates().get(0);assertFalse(r.targetReached());assertEquals(30000L,r.gapAfterWon());}
 @Test void currentQualificationSeparate(){var c=new Card("C","C",300,false,270000L,300000L,null,true,"SYNTHETIC");var r=e.evaluate(List.of(c),checkout,budget,Mode.PERFORMANCE,0).candidates().get(0);assertEquals(0L,r.benefitWon());assertTrue(r.targetReached());}
 @Test void unknownIsNotZero(){var c=new Card("C","C",300,null,null,300000L,null,null,"SYNTHETIC");var r=e.evaluate(List.of(c),checkout,budget,Mode.BENEFIT,0);assertEquals("HELD",r.status());assertNull(r.candidates().get(0).benefitWon());}
 @Test void splitHeld(){var c=new Checkout(50000,0,0,50000,"SHOPPING","NAVER_PAY_CARD",1,"UNKNOWN",true);assertEquals("HELD",e.evaluate(cards,c,budget,Mode.BENEFIT,0).status());}
 @Test void unconfirmedHeld(){var c=new Checkout(50000,0,0,50000,"SHOPPING","NAVER_PAY_CARD",1,"NO",false);assertEquals("HELD",e.evaluate(cards,c,budget,Mode.BENEFIT,0).status());}
 @Test void budgetWarningDoesNotPromotePerformance(){var b=new Budget(100,0,null,null,0);var r=e.evaluate(cards,checkout,b,Mode.BALANCED,1000);assertTrue(r.budgetWarning());assertEquals("A",r.recommendedCardId());}
 @Test void mismatchRejected(){assertThrows(IllegalArgumentException.class,()->new Checkout(50000,0,0,45000,"SHOPPING","DIRECT_CARD",1,"NO",true));}
 @Test void deterministic(){var reverse=List.of(cards.get(1),cards.get(0));assertEquals(e.evaluate(cards,checkout,budget,Mode.BENEFIT,0),e.evaluate(reverse,checkout,budget,Mode.BENEFIT,0));}
 @Test void wonBoundary(){var c=new Card("C","C",0,true,299999L,300000L,0L,true,"SYNTHETIC");var order=new Checkout(1,0,0,1,"SHOPPING","DIRECT_CARD",1,"NO",true);assertTrue(e.evaluate(List.of(c),order,budget,Mode.PERFORMANCE,0).candidates().get(0).targetReached());}
 @Test void unverifiedActualCardHeld(){var c=new Card("C","Real",300,true,0L,300000L,10000L,true,"UNVERIFIED");assertEquals("HELD",e.evaluate(List.of(c),checkout,budget,Mode.BENEFIT,0).status());}
}
