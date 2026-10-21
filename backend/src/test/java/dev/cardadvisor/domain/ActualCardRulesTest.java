package dev.cardadvisor.domain;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
import dev.cardadvisor.domain.RecommendationEngine.*;
class ActualCardRulesTest {
 private ActualCardRules.Facts facts(String kind,boolean confirmed){return new ActualCardRules.Facts("NAVER_PLUS_STORE",kind,confirmed);}
 @Test void roundingAndExclusions(){
  assertEquals(150,ActualCardRules.benefit(ActualCardRules.LOG,50000,facts("GENERAL_GOODS",true)));
  assertEquals(1,ActualCardRules.benefit(ActualCardRules.LOG,167,facts("GENERAL_GOODS",true)));
  assertEquals(0,ActualCardRules.benefit(ActualCardRules.LOG,166,facts("GENERAL_GOODS",true)));
  for(String kind:List.of("GIFT_CARD","PREPAID_TOPUP","TAX","PUBLIC_UTILITY","EDUCATION","INSURANCE"))assertEquals(0,ActualCardRules.benefit(ActualCardRules.LOG,50000,facts(kind,true)));
  assertThrows(IllegalArgumentException.class,()->ActualCardRules.benefit(ActualCardRules.LOG,50000,facts("GENERAL_GOODS",false)));
  assertFalse(ActualCardRules.supported(new ActualCardRules.Facts("UNVERIFIED_APP","GENERAL_GOODS",true)));
 }
 @Test void realRecommendationsCannotSelectSyntheticOrUnconfirmedFacts(){
  var cards=List.of(new Card("log","하나",30,true,0L,0L,null,true,ActualCardRules.LOG),new Card("kb","국민",0,null,470000L,500000L,null,true,ActualCardRules.KB),new Card("fake","[가상]",9000,true,0L,1L,100000L,true,"SYNTHETIC"));
  var engine=new RecommendationEngine();var budget=new Budget(200000,0,200000L,0L,0);
  var checkout=new Checkout(50000,0,0,50000,"SHOPPING","NAVER_PAY_CARD",1,"NO",true,facts("GENERAL_GOODS",true));
  var result=engine.evaluate(cards,checkout,budget,Mode.BENEFIT,0);assertEquals("log",result.recommendedCardId());
  assertNull(result.candidates().stream().filter(c->c.cardId().equals("fake")).findFirst().orElseThrow().benefitWon());
  assertEquals("kb",engine.evaluate(cards,checkout,budget,Mode.PERFORMANCE,0).recommendedCardId());
  assertEquals("log",engine.evaluate(cards,checkout,budget,Mode.BALANCED,149).recommendedCardId());
  assertEquals("kb",engine.evaluate(cards,checkout,budget,Mode.BALANCED,150).recommendedCardId());
  var unconfirmed=new Checkout(50000,0,0,50000,"SHOPPING","NAVER_PAY_CARD",1,"NO",true,facts("GENERAL_GOODS",false));
  assertEquals("HELD",engine.evaluate(cards,unconfirmed,budget,Mode.BENEFIT,0).status());
 }
}
