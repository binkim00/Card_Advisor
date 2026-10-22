package dev.cardadvisor.account;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="RUN_SERVICE_PG_TESTS",matches="true")
class FixedAllocationPostgresTest extends ServiceCompletionPostgresTest {
 @Test void crossMonthPartialPaymentRefundAndOverAllocation()throws Exception{
  ok("POST","/demo/setup",null);String card=ok("GET","/cards",null).get(0).get("id").asText(),m=day.toString().substring(0,7),next=day.plusMonths(1).toString().substring(0,7);
  String tx=ok("POST","/transactions/manual",Map.of("clientId",UUID.randomUUID(),"amountWon",1000,"cardChargeWon",1000,"cardId",card,"purchasedAt",day.minusMonths(1).toString(),"category","HOUSING","included",true)).get("id").asText();
  UUID plan=UUID.randomUUID();ok("POST","/fixed-costs",Map.of("id",plan,"name","Synthetic rent","amountWon",600,"category","HOUSING","dayOfMonth",31,"effectiveMonth",m,"active",true,"version",0));
  String a="/fixed-costs/"+plan+"/months/"+m+"/links",b="/fixed-costs/"+plan+"/months/"+next+"/links";
  assertEquals(0,ok("PUT",a,Map.of("transactionId",tx,"linked",true,"version",1,"allocatedWon",600)).at("/items/0/remainingWon").asLong());
  assertEquals(409,call("PUT",b,Map.of("transactionId",tx,"linked",true,"version",2,"allocatedWon",401),token).status());
  assertEquals(200,ok("PUT",b,Map.of("transactionId",tx,"linked",true,"version",2,"allocatedWon",400)).at("/items/0/remainingWon").asLong());
  ok("POST","/transactions/"+tx+"/cancellations",Map.of("clientId",UUID.randomUUID(),"amountWon",250,"cardRefundWon",250));
  assertEquals(150,ok("GET","/fixed-costs?month="+m,null).at("/items/0/remainingWon").asLong());assertEquals(300,ok("GET","/fixed-costs?month="+next,null).at("/items/0/remainingWon").asLong());
  assertEquals(0,ok("GET","/budgets/"+m+"/summary",null).get("spentWon").asLong());
  ok("PUT","/transactions/"+tx+"/classification",Map.of("category","HOUSING","included",false,"version",1));assertEquals(600,ok("GET","/fixed-costs?month="+m,null).at("/items/0/remainingWon").asLong());
  var other=signup();assertEquals(404,call("PUT",a,Map.of("transactionId",tx,"linked",false,"version",3),other.get("accessToken").asText()).status());
  ok("PUT",a,Map.of("transactionId",tx,"linked",false,"version",3));assertEquals(1,db.queryForObject("SELECT count(*) FROM fixed_cost_links WHERE user_id=?",Integer.class,user));
 }
}
