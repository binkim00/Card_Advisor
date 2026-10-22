package dev.cardadvisor.account;
import dev.cardadvisor.api.*;import dev.cardadvisor.domain.*;import dev.cardadvisor.domain.RecommendationEngine.*;
import com.fasterxml.jackson.databind.*;import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;import java.net.http.*;import java.time.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"security.rate-limit-scope=synthetic-service-completion","codef.config-file=","codef.storage-key-file=","openai.config-file="})
@EnabledIfEnvironmentVariable(named="RUN_SERVICE_PG_TESTS",matches="true")
class ServiceCompletionPostgresTest {
 @LocalServerPort int port;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;@Autowired AdvisorService advisor;
 final HttpClient http=HttpClient.newHttpClient();final List<UUID> users=new ArrayList<>();String token;UUID user;
 final LocalDate day=LocalDate.now(ZoneId.of("Asia/Seoul")).withDayOfMonth(1);final String password="Synthetic-account-password-123";
 record Response(int status,JsonNode data){}
 Response call(String method,String path,Object input,String bearer)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/v1"+path)).header("Content-Type","application/json");if(bearer!=null)b.header("Authorization","Bearer "+bearer);
  b.method(method,input==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input)));
  var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());return new Response(r.statusCode(),r.body().isEmpty()?json.nullNode():json.readTree(r.body()));
 }
 JsonNode ok(String method,String path,Object input)throws Exception{var r=call(method,path,input,token);assertTrue(r.status>=200&&r.status<300,"Unexpected HTTP status "+r.status);return r.data;}
 JsonNode signup()throws Exception{var r=call("POST","/auth/register",Map.of("email","service-"+UUID.randomUUID()+"@example.test","password",password),null);assertEquals(201,r.status);users.add(UUID.fromString(r.data.get("userId").asText()));return r.data;}
 @BeforeEach void before()throws Exception{db.update("DELETE FROM request_limits WHERE bucket_hash=?",dev.cardadvisor.auth.Passwords.sha("auth-ip:synthetic-service-completion:127.0.0.1:"+(java.time.Instant.now().getEpochSecond()/60)));var session=signup();token=session.get("accessToken").asText();user=UUID.fromString(session.get("userId").asText());}
 @AfterEach void after(){for(UUID id:users){db.update("DELETE FROM app_users WHERE id=?",id);db.update("DELETE FROM deleted_user_tombstones WHERE user_id=?",id);}}
 JsonNode facts(){return json.valueToTree(new ActualCardRules.Facts("NAVER_PLUS_STORE","GENERAL_GOODS",true));}
 UUID card(String product)throws Exception{var p=ActualCardRules.PRODUCTS.stream().filter(x->x.id().equals(product)).findFirst().orElseThrow();return UUID.fromString(ok("POST","/cards/from-product",Map.of("productId",product,"expectedRuleVersion",p.ruleVersion())).get("id").asText());}
 JsonNode state(UUID card,String m,long amount,boolean qualified)throws Exception{
  var month=YearMonth.parse(m);return ok("PUT","/cards/"+card+"/months/"+m,Map.of("untrackedPerformanceWon",amount,"currentQualified",qualified,"version",0,"untrackedSource","USER_INPUT","untrackedAsOf",month.atDay(1).toString(),"qualificationSource","USER_INPUT","qualificationAsOf",month.atDay(1).minusDays(1).toString(),"qualificationPeriod",month.minusMonths(1).toString()));
 }
 JsonNode purchase(UUID card,long amount,LocalDate date)throws Exception{return ok("POST","/transactions/manual",Map.of("clientId",UUID.randomUUID(),"amountWon",amount,"cardChargeWon",amount,"cardId",card,"purchasedAt",date.toString(),"category","SHOPPING","included",true,"purchaseFacts",facts()));}
 @Test void purposeConsentConflictIsolationAndWithdrawal()throws Exception{
  assertEquals(0,ok("GET","/me/preferences",null).get("version").asInt());
  ok("PUT","/me/consents/AI",Map.of("accepted",true,"policyVersion",UserSettingsService.POLICY,"expectedVersion",0));
  assertEquals(409,call("PUT","/me/consents/CHECKOUT_DETECTION",Map.of("accepted",true,"policyVersion",UserSettingsService.POLICY,"expectedVersion",0),token).status);
  var other=signup();assertEquals(0,call("GET","/me/preferences",null,other.get("accessToken").asText()).data.get("version").asInt());
  ok("PUT","/me/consents/AI",Map.of("accepted",false,"policyVersion",UserSettingsService.POLICY,"expectedVersion",1));
  assertEquals(2L,db.queryForObject("SELECT count(*) FROM consent_events WHERE user_id=?",Long.class,user));
  assertEquals(401,call("POST","/me/deletion",Map.of("password","wrong","confirmation","계정 삭제"),token).status);
  ok("POST","/me/deletion",Map.of("password",password,"confirmation","계정 삭제"));assertEquals(401,call("GET","/me",null,token).status);assertEquals(1,db.queryForObject("SELECT count(*) FROM deleted_user_tombstones WHERE user_id=?",Integer.class,user));
 }
 @Test void refreshRotationReuseRevokesNewAccessToo()throws Exception{
  var session=signup();String old=session.get("accessToken").asText();var rotated=call("POST","/auth/refresh",Map.of("refreshToken",session.get("refreshToken").asText()),null);assertEquals(200,rotated.status);
  assertEquals(401,call("GET","/me",null,old).status);
  assertEquals(401,call("POST","/auth/refresh",Map.of("refreshToken",session.get("refreshToken").asText()),null).status);
  assertEquals(401,call("GET","/me",null,rotated.data.get("accessToken").asText()).status);
 }
 @Test void actualProductsRecommendationAndCrossMonthRefund()throws Exception{
  ok("POST","/demo/setup",null);UUID kb=card("KB_EASY_ALL_AUTO"),log=card("HANA_TRAVELOG");card("HANA_TRAVELGO_BANK");String month=day.toString().substring(0,7);
  state(kb,month,470000,false);
  var checkout=ok("POST","/checkout-sessions",new Checkout(50000,0,0,50000,"SHOPPING","NAVER_PAY_CARD",1,"NO",true,new ActualCardRules.Facts("NAVER_PLUS_STORE","GENERAL_GOODS",true)));
  var result=ok("POST","/recommendations",Map.of("checkoutId",checkout.get("id").asText(),"snapshotVersion",1,"mode","BENEFIT","maxOpportunityCostWon",0)).get("result");assertEquals(log.toString(),result.get("recommendedCardId").asText());
  for(var c:result.get("candidates"))if("SYNTHETIC".equals(c.get("source").asText()))assertTrue(c.get("benefitWon").isNull());
  var entry=purchase(kb,50000,day);UUID transaction=UUID.fromString(entry.get("id").asText());
  ok("POST","/transactions/"+transaction+"/cancellations",Map.of("clientId",UUID.randomUUID(),"amountWon",20000,"cardRefundWon",20000,"receivedAt",day.toString()));
  var performance=advisor.performance(user,month).stream().filter(c->c.id().equals(kb.toString())).findFirst().orElseThrow();assertEquals(500000L,performance.performanceWon());assertEquals(false,performance.currentQualified());
  LocalDate previous=day.minusMonths(1);state(kb,previous.toString().substring(0,7),0,false);var older=purchase(kb,10000,previous);
  ok("POST","/transactions/"+older.get("id").asText()+"/cancellations",Map.of("clientId",UUID.randomUUID(),"amountWon",5000,"cardRefundWon",5000,"receivedAt",day.toString()));
  assertEquals(495000L,advisor.performance(user,month).stream().filter(c->c.id().equals(kb.toString())).findFirst().orElseThrow().performanceWon());
  assertEquals(10000L,advisor.performance(user,previous.toString().substring(0,7)).stream().filter(c->c.id().equals(kb.toString())).findFirst().orElseThrow().performanceWon());
 }
 @Test void deterministicInsightsNeverAutosaveAndRejectStaleProposal()throws Exception{
  ok("POST","/demo/setup",null);UUID card=card("HANA_TRAVELOG");var entry=purchase(card,90000,day);String month=day.toString().substring(0,7);
  var analysis=ok("GET","/insights?month="+month,null);assertEquals("CONSENT_REQUIRED",analysis.at("/assistance/status").asText());assertEquals(90000,analysis.at("/proposedBudget/categories/SHOPPING").asLong());
  assertEquals(70000,ok("GET","/budgets/"+month,null).at("/categories/SHOPPING").asLong());
  ok("PUT","/budgets/"+month,analysis.get("proposedBudget"));assertEquals(409,call("PUT","/budgets/"+month,analysis.get("proposedBudget"),token).status);
  ok("PUT","/transactions/"+entry.get("id").asText()+"/classification",Map.of("category","FOOD","included",true,"version",1));
  var suggestion=ok("POST","/transactions/"+entry.get("id").asText()+"/classification-suggestion",null);assertEquals("FOOD",suggestion.get("category").asText());assertEquals("USER_CORRECTION",suggestion.at("/assistance/source").asText());
 }
}
