package dev.cardadvisor.finance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Inherits the existing real-HTTP/isolated-PG fixture and connection regression cases. */
class HeldCardsPostgresTest extends FinancialConnectionsPostgresTest {
 JsonNode connectionA()throws Exception{return expect(200,request(a,"POST","/finance/connections",connect("0301"),UUID.randomUUID())).path("connection");}
 String base(JsonNode c){return "/finance/connections/"+c.path("id").asText()+"/held-cards";}
 JsonNode data(String name,String mask){return json.createObjectNode().put("resCardName",name).put("resCardNo",mask).put("resCardType","체크/본인");}
 JsonNode fetch(JsonNode c,JsonNode...cards)throws Exception{
  var b=new Behavior();var array=json.createArrayNode();for(var card:cards)array.add(card);b.approvalData=array;provider.behaviors.add(b);
  return expect(200,request(a,"POST",base(c)+"/refresh",Map.of("expectedConnectionVersion",c.path("version").asLong(),"confirmed",true),null));
 }
 Map<String,Object> registration(JsonNode c,JsonNode card){return Map.of("expectedVersion",card.path("version").asLong(),"expectedConnectionVersion",c.path("version").asLong(),"confirmed",true);}
 JsonNode save(JsonNode c,JsonNode card,Map<String,Object> input,int status)throws Exception{return expect(status,request(a,"POST",base(c)+"/"+card.path("id").asText()+"/register",input,null));}
 @Test void explicitUnknownRegistrationIsOwnedIdempotentAndNotAutoInserted()throws Exception{
  var c=connectionA();var list=fetch(c,data("합성 미지원 카드","1234********5678"),data("합성 두번째 카드","9876********4321"));
  assertEquals(0,db.queryForObject("SELECT count(*) FROM cards WHERE user_id=?",Integer.class,uidA));
  int calls=provider.calls.size();expect(200,request(a,"GET",base(c),null,null));assertEquals(calls,provider.calls.size());
  var card=list.path("cards").get(0);var saved=save(c,card,registration(c,card),200);save(c,card,registration(c,card),200);
  assertNotNull(saved.path("cards").get(0).get("cardId"));
  var mine=expect(200,request(a,"GET","/cards",null,null));assertEquals(1,mine.size());assertTrue(mine.get(0).path("rateBp").isNull());assertTrue(mine.get(0).path("performanceWon").isNull());assertTrue(mine.get(0).path("targetWon").isNull());
  assertEquals(HeldCardService.UNVERIFIED,mine.get(0).path("source").asText());
  assertEquals(0,expect(200,request(b,"GET","/cards",null,null)).size());
  assertEquals(0,db.queryForObject("SELECT count(*) FROM ledger_entries WHERE user_id=?",Integer.class,uidA));
 }
 @Test void otherOwnersCannotListLookupOrRegister()throws Exception{
  var c=connectionA();var card=fetch(c,data("합성 카드","1234********5678")).path("cards").get(0);int calls=provider.calls.size();
  expect(404,request(b,"GET",base(c),null,null));
  expect(404,request(b,"POST",base(c)+"/refresh",Map.of("expectedConnectionVersion",c.path("version").asLong(),"confirmed",true),null));
  expect(404,request(b,"POST",base(c)+"/"+card.path("id").asText()+"/register",registration(c,card),null));assertEquals(calls,provider.calls.size());
 }
 @Test void requiresConfirmationAndFreshSnapshot()throws Exception{
  var c=connectionA();var card=fetch(c,data("합성 카드","1234********5678")).path("cards").get(0);
  save(c,card,Map.of("expectedVersion",card.path("version").asLong(),"expectedConnectionVersion",c.path("version").asLong(),"confirmed",false),422);
  var next=fetch(c,data("합성 카드","1234********5678")).path("cards").get(0);assertEquals(card.path("id"),next.path("id"));
  save(c,card,registration(c,card),409);save(c,next,registration(c,next),200);
 }
 @Test void malformedDuplicateOrFailedRefreshPreservesSnapshot()throws Exception{
  var c=connectionA();var prior=fetch(c,data("합성 카드","1234********5678"));
  for(var bad:List.of(json.createArrayNode().add(data("invalid","1234567890123456")),json.createArrayNode().add(data("same","1234********5678")).add(data("same","1234********5678")))){
   var behavior=new Behavior();behavior.approvalData=bad;provider.behaviors.add(behavior);
   expect(422,request(a,"POST",base(c)+"/refresh",Map.of("expectedConnectionVersion",c.path("version").asLong(),"confirmed",true),null));
   assertEquals(prior.path("cards").get(0).path("version"),expect(200,request(a,"GET",base(c),null,null)).path("cards").get(0).path("version"));
  }
  var fail=new Behavior();fail.status=503;provider.behaviors.add(fail);expect(503,request(a,"POST",base(c)+"/refresh",Map.of("expectedConnectionVersion",c.path("version").asLong(),"confirmed",true),null));
  assertEquals(1,expect(200,request(a,"GET",base(c),null,null)).path("cards").size());
 }
 @Test void explicitProductSelectionChecksIssuerAndRuleVersion()throws Exception{
  var c=connectionA();var snapshot=fetch(c,data("합성 상품 카드","1234********5678"));var card=snapshot.path("cards").get(0);var p=snapshot.path("products").get(0);
  var input=new HashMap<>(registration(c,card));input.put("productId","HANA_TRAVELOG");input.put("expectedRuleVersion","wrong");save(c,card,input,422);
  input.put("productId",p.path("id").asText());save(c,card,input,409);
  input.put("expectedRuleVersion",p.path("ruleVersion").asText());save(c,card,input,200);
  assertEquals(p.path("ruleVersion").asText(),expect(200,request(a,"GET","/cards",null,null)).get(0).path("source").asText());
 }
 @Test void emptyRefreshKeepsRegisteredCardsButPreventsMissingCardRegistration()throws Exception{
  var c=connectionA();var before=fetch(c,data("합성 A","1234********5678"),data("합성 B","9876********4321"));
  save(c,before.path("cards").get(0),registration(c,before.path("cards").get(0)),200);
  var empty=fetch(c);assertEquals(1,empty.path("cards").size());assertFalse(empty.path("cards").get(0).path("current").asBoolean());
  save(c,before.path("cards").get(1),registration(c,before.path("cards").get(1)),409);
  assertEquals(1,expect(200,request(a,"GET","/cards",null,null)).size());
 }
 @Test void changedLabelWithSameMaskCannotSilentlyDuplicateRegisteredCard()throws Exception{
  var c=connectionA();var old=fetch(c,data("합성 전 이름","1234********5678")).path("cards").get(0);save(c,old,registration(c,old),200);
  var next=fetch(c,data("합성 후 이름","1234********5678"));var card=next.path("cards").findValues("cardId");
  var changed=java.util.stream.StreamSupport.stream(next.path("cards").spliterator(),false).filter(x->x.path("current").asBoolean()).findFirst().orElseThrow();
  save(c,changed,registration(c,changed),409);assertEquals(1,expect(200,request(a,"GET","/cards",null,null)).size());
 }

 @Test @EnabledIfEnvironmentVariable(named="RUN_HELD_CARD_ANDROID",matches="true")
 void androidLookupAndRegisterUsesRealHttpAndPostgres()throws Exception{
  var c=connectionA();
  for(int i=0;i<2;i++){var behavior=new Behavior();behavior.approvalData=json.createArrayNode().add(data("합성 A 미지원","1234********5678")).add(data("합성 B 확인상품","9876********4321"));provider.behaviors.add(behavior);}
  String adb=java.nio.file.Path.of(System.getenv("LOCALAPPDATA"),"Android","Sdk","platform-tools","adb.exe").toString();
  String device="emulator-5564";String tcp="tcp:"+port;
  var reverse=new ProcessBuilder(adb,"-s",device,"reverse",tcp,tcp).redirectErrorStream(true).start();assertEquals(0,reverse.waitFor());
  try{
   var process=new ProcessBuilder(adb,"-s",device,"shell","am","instrument","-w","-r","-e","class","dev.cardadvisor.android.HeldCardsFlowTest","-e","baseUrl","http://127.0.0.1:"+port,"-e","accessToken",a,"dev.cardadvisor.android.designreview.test/androidx.test.runner.AndroidJUnitRunner").redirectErrorStream(true).start();
   var reader=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("SYNTHETIC_INSTRUMENTATION_OUTPUT_FAILED");}});
   boolean finished=process.waitFor(150,java.util.concurrent.TimeUnit.SECONDS);if(!finished)process.destroyForcibly();assertTrue(finished,"Android UI timeout");var output=reader.get(5,java.util.concurrent.TimeUnit.SECONDS);
   boolean passed=process.exitValue()==0&&output.contains("OK (1 test)");
   var locations=java.util.regex.Pattern.compile("[A-Za-z]+Test[.]kt:[0-9]+").matcher(output).results().map(java.util.regex.MatchResult::group).distinct().toList();
   System.out.println("Held-card Android UI: passed="+passed+", locations="+locations);output=null;
   assertTrue(passed,"Synthetic held-card Android UI failed; raw output withheld");
   var mine=expect(200,request(a,"GET","/cards",null,null));assertEquals(2,mine.size());
   assertEquals(2,db.queryForObject("SELECT count(*) FROM financial_held_cards WHERE user_id=? AND card_id IS NOT NULL",Integer.class,uidA));
   assertEquals(0,expect(200,request(b,"GET","/cards",null,null)).size());
  }finally{new ProcessBuilder(adb,"-s",device,"reverse","--remove",tcp).start().waitFor();}
 }

 @Test void connectionChangeDuringProviderReadCannotSaveStaleSnapshot()throws Exception{
  var c=connectionA();var prior=fetch(c,data("합성 기존 카드","1234********5678"));
  var behavior=new Behavior();behavior.entered=new java.util.concurrent.CountDownLatch(1);behavior.release=new java.util.concurrent.CountDownLatch(1);behavior.approvalData=json.createArrayNode().add(data("합성 신규 카드","9876********4321"));provider.behaviors.add(behavior);
  var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
  try{
   var future=executor.submit(()->request(a,"POST",base(c)+"/refresh",Map.of("expectedConnectionVersion",c.path("version").asLong(),"confirmed",true),null));
   assertTrue(behavior.entered.await(5,java.util.concurrent.TimeUnit.SECONDS));db.update("UPDATE financial_connections SET version=version+1 WHERE user_id=? AND id=?",uidA,UUID.fromString(c.path("id").asText()));behavior.release.countDown();
   assertEquals(409,future.get(10,java.util.concurrent.TimeUnit.SECONDS).status());
   assertEquals(prior.path("cards"),expect(200,request(a,"GET",base(c),null,null)).path("cards"));
  }finally{behavior.release.countDown();executor.shutdownNow();}
 }
 @org.springframework.beans.factory.annotation.Autowired dev.cardadvisor.account.CardCatalogService catalog;
 @org.springframework.beans.factory.annotation.Autowired org.springframework.transaction.PlatformTransactionManager manager;
 @org.springframework.beans.factory.annotation.Autowired dev.cardadvisor.api.AdvisorService advisor;
 @Test void disabledProviderAndUnknownRuleEditsRemainBlocked()throws Exception{
  var c=connectionA();var card=fetch(c,data("합성 미지원","1234********5678")).path("cards").get(0);save(c,card,registration(c,card),200);
  int before=provider.calls.size();var disabled=new CodefClient(new CodefSettings(new Properties()));
  var service=new HeldCardService(db,disabled,cipher,catalog,manager);
  assertThrows(CodefFailure.class,()->service.lookup(uidA,UUID.fromString(c.path("id").asText()),new HeldCardService.Lookup(c.path("version").asLong(),true)));assertEquals(before,provider.calls.size());
  UUID imported=UUID.fromString(expect(200,request(a,"GET","/cards",null,null)).get(0).path("id").asText());
  assertThrows(dev.cardadvisor.api.ApiError.class,()->advisor.saveMonthState(uidA,imported,"2026-10",new dev.cardadvisor.api.AdvisorService.MonthStateInput(0L,true,0L,0)));
 }
}
