package dev.cardadvisor.account;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="RUN_SERVICE_PG_TESTS",matches="true")
class BudgetAllocationPostgresTest extends ServiceCompletionPostgresTest {
 @Test void splitRefundConflictIsolationAndPerformance()throws Exception{
  ok("POST","/demo/setup",null);var id=ok("GET","/cards",null).get(0).get("id").asText();String m=day.toString().substring(0,7);
  var tx=ok("POST","/transactions/manual",Map.of("clientId",UUID.randomUUID(),"amountWon",1000,"cardChargeWon",1000,"cardId",id,"purchasedAt",day.toString(),"category","SHOPPING","included",true)).get("id").asText();
  var before=ok("GET","/cards/performance?month="+m,null);
  var request=Map.of("category","FOOD","included",true,"version",1,"allocationsWon",Map.of("FOOD",400,"SHOPPING",600));
  ok("PUT","/transactions/"+tx+"/classification",request);assertEquals(before,ok("GET","/cards/performance?month="+m,null));
  assertEquals(409,call("PUT","/transactions/"+tx+"/classification",request,token).status());
  var other=signup();assertEquals(404,call("GET","/transactions/"+tx+"/classification-history",null,other.get("accessToken").asText()).status());
  ok("POST","/transactions/"+tx+"/cancellations",Map.of("clientId",UUID.randomUUID(),"amountWon",251,"cardRefundWon",251));
  var entry=ok("GET","/transactions/"+tx,null);assertEquals(300,entry.at("/net_allocations_won/FOOD").asInt());assertEquals(449,entry.at("/net_allocations_won/SHOPPING").asInt());
  assertEquals(749,ok("GET","/budgets/"+m+"/summary",null).get("spentWon").asLong());assertEquals(2,ok("GET","/transactions/"+tx+"/classification-history",null).size());
  assertEquals(422,call("PUT","/transactions/"+tx+"/classification",Map.of("category","FOOD","included",true,"version",2,"allocationsWon",Map.of("FOOD",749)),token).status());
  ok("PUT","/transactions/"+tx+"/classification",Map.of("category","FOOD","included",false,"version",2,"allocationsWon",Map.of("FOOD",400,"SHOPPING",600)));assertEquals(0,ok("GET","/budgets/"+m+"/summary",null).get("spentWon").asLong());
 }
}
