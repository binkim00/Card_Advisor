package dev.cardadvisor.finance;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.cardadvisor.api.ApiError;
import dev.cardadvisor.domain.RecommendationEngine;
import dev.cardadvisor.domain.RecommendationEngine.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HeldCardNormalizerTest {
 private final ObjectMapper json=new ObjectMapper();
 @Test void masksAndMinimizesFields()throws Exception{
  var result=new HeldCardNormalizer().normalize("0301",json.readTree("[{\"resCardNo\":\"1234********5678\",\"resCardName\":\"합성 카드\",\"resCardType\":\"체크/본인\",\"resUserNm\":\"DISCARD_ME\"}]"));
  assertEquals("************5678",result.get(0).mask());assertFalse(result.toString().contains("DISCARD_ME"));
  assertEquals("*************123",new HeldCardNormalizer().normalize("0301",json.readTree("[{\"resCardNo\":\"*************123\",\"resCardName\":\"합성 카드\"}]")).get(0).mask());
 }
 @Test void rejectsFullNumbersDuplicateIdentityAndMalformedFields()throws Exception{
  for(String raw:List.of("[{\"resCardNo\":\"1234567890123456\",\"resCardName\":\"합성\"}]","[{\"resCardNo\":\"1234********5678\",\"resCardName\":\"\"}]","{}"))assertThrows(ApiError.class,()->new HeldCardNormalizer().normalize("0301",json.readTree(raw)));
  var duplicate=json.readTree("[{\"resCardNo\":\"1234********5678\",\"resCardName\":\"합성\"},{\"resCardNo\":\"1234********5678\",\"resCardName\":\"합성\"}]");
  assertThrows(ApiError.class,()->new HeldCardNormalizer().normalize("0301",duplicate));
 }
 @Test void stableIdentityAndIssuerSpecificMask()throws Exception{
  var data=json.readTree("[{\"resCardNo\":\"*************123\",\"resCardName\":\"합성\"}]");
  assertEquals(new HeldCardNormalizer().normalize("0301",data),new HeldCardNormalizer().normalize("0301",data));
  assertThrows(ApiError.class,()->new HeldCardNormalizer().normalize("0313",data));
 }
 @Test void importedUnknownIsNeverZeroBenefitOrRanked(){
  var card=new Card(UUID.randomUUID().toString(),"합성 미지원 카드",null,null,null,null,null,null,HeldCardService.UNVERIFIED);
  var checkout=new Checkout(10000,0,0,10000,"SHOPPING","DIRECT_CARD",1,"NO",true);
  var result=new RecommendationEngine().evaluate(List.of(card),checkout,new Budget(100000,0,100000L,0L,0),Mode.BENEFIT,0);
  assertNull(result.recommendedCardId());assertNull(result.candidates().get(0).benefitWon());assertNull(result.candidates().get(0).performanceAfterWon());
  assertTrue(result.candidates().get(0).uncertaintyReasons().contains("PRODUCT_RULES_NOT_VERIFIED"));
  assertThrows(IllegalArgumentException.class,()->new Card("x","invalid",null,null,null,null,null,null,"SYNTHETIC"));
 }
}
