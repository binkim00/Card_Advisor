package dev.cardadvisor.account;
import dev.cardadvisor.domain.RecommendationEngine.*;import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class CheckoutAllocationPostgresTest extends ServiceCompletionPostgresTest{
 @Test void separateCategoriesAffectBudgetOnlyAndChangesInvalidate()throws Exception{
  ok("POST","/demo/setup",null);String m=day.toString().substring(0,7);ok("PUT","/budgets/"+m,Map.of("totalWon",120000,"categories",Map.of("FOOD",20000,"EDUCATION",50000),"fixedRemainingWon",0,"version",1));
  var checkout=ok("POST","/checkout-sessions",new Checkout(60000,5000,10000,45000,"FOOD","DIRECT_CARD",1,"NO",true,null,Map.of("FOOD",25000L,"EDUCATION",30000L)));
  var reply=ok("POST","/recommendations",Map.of("checkoutId",checkout.get("id").asText(),"snapshotVersion",1,"mode","BENEFIT","maxOpportunityCostWon",0));var result=reply.get("result");assertEquals(55000,result.path("expenseWon").asLong());assertEquals(-5000,result.at("/categoryAfterByCode/FOOD").asLong());assertEquals(20000,result.at("/categoryAfterByCode/EDUCATION").asLong());assertTrue(result.path("budgetWarning").asBoolean());assertEquals(1350,result.at("/candidates/0/benefitWon").asLong());assertEquals(0,ok("GET","/transactions?month="+m,null).size());
  assertEquals(422,call("POST","/checkout-sessions",Map.of("orderValueWon",100,"pointUsedWon",0,"prepaidMoneyUsedWon",0,"cardChargeWon",100,"category","FOOD","paymentMethod","DIRECT_CARD","installments",1,"splitApproval","NO","confirmedByUser",true,"allocationsWon",Map.of("FOOD",99)),token).status());
  ok("PUT","/budgets/"+m,Map.of("totalWon",120000,"categories",Map.of("FOOD",25000,"EDUCATION",50000),"fixedRemainingWon",0,"version",2));assertEquals("INVALIDATED",ok("GET","/recommendations/"+reply.path("id").asText(),null).path("validity").asText());
 }
 @Test void nonSingleApprovalIsHeldAndKeepsWholeBudgetExpense()throws Exception{
  ok("POST","/demo/setup",null);var checkout=ok("POST","/checkout-sessions",new Checkout(60000,0,0,60000,"SUBSCRIPTION","DIRECT_CARD",3,"YES",true));var r=ok("POST","/recommendations",Map.of("checkoutId",checkout.get("id").asText(),"snapshotVersion",1,"mode","BENEFIT","maxOpportunityCostWon",0)).get("result");assertEquals("HELD",r.path("status").asText());assertEquals(60000,r.path("expenseWon").asLong());assertTrue(r.at("/categoryAfterByCode/SUBSCRIPTION").isNull());assertTrue(r.at("/candidates/0/benefitWon").isNull());
 }
}
