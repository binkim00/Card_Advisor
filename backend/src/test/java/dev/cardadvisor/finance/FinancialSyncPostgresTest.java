package dev.cardadvisor.finance;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.cardadvisor.finance.CodefApprovalNormalizerTest.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"security.rate-limit-scope=synthetic-finance","codef.config-file=","codef.storage-key-file="})
@Import(FinancialConnectionsPostgresTest.LocalProviderConfiguration.class)
@EnabledIfEnvironmentVariable(named="RUN_FINANCE_PG_TESTS",matches="true")
class FinancialSyncPostgresTest {
 @LocalServerPort int port;@Autowired JdbcTemplate db;@Autowired FinancialConnectionsPostgresTest.Stub provider;
 final HttpClient http=HttpClient.newHttpClient();final List<UUID> users=new ArrayList<>();String a,b;UUID userA,userB,connection;
 record Reply(int status,JsonNode body){}
 Reply req(String token,String method,String path,Object body,UUID key)throws Exception{
  if("OTHER_TEST_ACCOUNT".equals(token))token=otherAccount();
  var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/v1"+path)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json");
  if(token!=null)r.header("Authorization","Bearer "+token);if(key!=null)r.header("Idempotency-Key",key.toString());
  r.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
  var out=http.send(r.build(),HttpResponse.BodyHandlers.ofString());assertFalse(out.body().contains("synthetic-cid"));assertFalse(out.body().contains("1234567812345678"));assertFalse(out.body().contains("PRIVATE_CANARY"));
  return new Reply(out.statusCode(),out.body().isBlank()?null:JSON.readTree(out.body()));
 }
 JsonNode ok(Reply r){assertEquals(200,r.status());return r.body();}
 String register()throws Exception{var r=req(null,"POST","/auth/register",Map.of("email","sync-"+UUID.randomUUID()+"@example.test","password",UUID.randomUUID().toString()),null);assertEquals(201,r.status());users.add(UUID.fromString(r.body().path("userId").asText()));return r.body().path("accessToken").asText();}
 UUID connect(String token,String org)throws Exception{return UUID.fromString(ok(req(token,"POST","/finance/connections",Map.of("organization",org,"loginId","synthetic-login","password","SYNTHETIC_PASSWORD_ONLY","consent",true,"consentVersion","FINANCE_LINK_V1"),UUID.randomUUID())).path("connection").path("id").asText());}
 @Autowired ConnectedIdCipher cipher;
 @Test void repairsLegacyMaskedRowsInPlaceAndReplaysWithoutDuplicates()throws Exception{
  var node=row("A1","50000","0","").put("resCardNo","*************123");var legacy=new CodefApprovalNormalizer().legacyNormalize("0301",node,DATE,DATE).get(0);
  var first=sync(node);assertEquals(1,count("ledger_entries"));var stored=db.queryForMap("SELECT * FROM financial_transactions WHERE user_id=?",userA);var id=stored.get("id");
  db.update("DELETE FROM financial_transactions WHERE user_id=?",userA);db.update("DELETE FROM ledger_entries WHERE user_id=?",userA);
  String key=db.queryForObject("SELECT transaction_key_id FROM financial_connections WHERE id=?",String.class,connection);
  byte[] legacyId=cipher.fingerprint(key,(userA+"|"+connection+"|unidentified|"+legacy.fingerprintMaterial()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  byte[] legacyFp=cipher.fingerprint(key,legacy.fingerprintMaterial().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  db.update("INSERT INTO financial_transactions(id,user_id,connection_id,identity_hash,fingerprint,used_date,used_time,kind,amount_won,cancelled_won,currency,payment_type,installments,status,issue,rule_version,source,last_run_id) VALUES(?,?,?,?,?,?,?,'APPROVAL',50000,0,'KRW','1',1,'HELD','MISSING_IDENTITY','CODEF_KB_HANA_20261019','SYNTHETIC',?)",id,userA,connection,legacyId,legacyFp,DATE,"120117",UUID.fromString(first.path("id").asText()));
  assertEquals(1,sync(node).path("applied").asInt());assertEquals(id,db.queryForObject("SELECT id FROM financial_transactions WHERE user_id=?",UUID.class,userA));long rev=revision();sync(node);assertEquals(1,count("ledger_entries"));assertEquals(1,count("financial_transactions"));assertEquals(rev,revision());
 }
 @Test void displayMetadataRefreshDoesNotDuplicateOrInvalidateMoney()throws Exception{var n=row("A1","50000","0","");sync(n);long rev=revision();n.put("resMemberStoreName","테스트 상점").put("resCardName","가상 카드");sync(n);assertEquals(1,count("ledger_entries"));assertEquals(rev,revision());var item=rows().get(0);assertEquals("테스트 상점",item.path("merchant_name").asText());assertEquals("가상 카드",item.path("card_label").asText());assertEquals(404,req(b,"GET","/finance/connections/"+connection+"/transactions",null,null).status());}
 @Test void distinctNewIdentitiesWithSameLossyLegacyShapeRemainSeparate()throws Exception{
  var one=row("A1","50000","0","").put("resCardNo","*************123");var two=one.deepCopy().put("resApprovalNo","A2");var data=JSON.createArrayNode().add(one).add(two);
  assertEquals("IMPORTED",sync(data).path("status").asText());assertEquals(2,count("financial_transactions"));assertEquals(2,count("ledger_entries"));sync(data);assertEquals(2,count("financial_transactions"));assertEquals(2,count("ledger_entries"));
 }
 @Test void ambiguousExistingLegacyAliasStillRejectsWithoutMutation()throws Exception{
  var one=row("A1","50000","0","").put("resCardNo","*************123");sync(one);var legacy=new CodefApprovalNormalizer().legacyNormalize("0301",one,DATE,DATE).get(0);
  String key=db.queryForObject("SELECT transaction_key_id FROM financial_connections WHERE id=?",String.class,connection);
  byte[] hash=cipher.fingerprint(key,(userA+"|"+connection+"|unidentified|"+legacy.fingerprintMaterial()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  db.update("UPDATE financial_transactions SET identity_hash=?,ledger_id=NULL,card_mask=NULL,status='HELD',issue='MISSING_IDENTITY',rule_version='CODEF_KB_HANA_20261019' WHERE user_id=?",hash,userA);db.update("DELETE FROM ledger_entries WHERE user_id=?",userA);
  var two=one.deepCopy().put("resApprovalNo","A2");assertEquals("FAILED",sync(JSON.createArrayNode().add(one).add(two)).path("status").asText());assertEquals(1,count("financial_transactions"));assertEquals(0,count("ledger_entries"));assertEquals("MISSING_IDENTITY",rows().get(0).path("issue").asText());
 }

 JsonNode mappings(String token){try{return ok(req(token,"GET","/finance/connections/"+connection+"/card-mappings",null,null));}catch(Exception e){throw new IllegalStateException(e);}}
 String productCard(String token,String product,String source)throws Exception{return ok(req(token,"POST","/cards/from-product",Map.of("productId",product,"expectedRuleVersion",source),null)).path("id").asText();}
 Map<String,Object> mappingBody(JsonNode g,String card,long version){var in=new HashMap<String,Object>();in.put("sourceKey",g.path("sourceKey").asText());in.put("cardId",card);in.put("expectedVersion",version);in.put("expectedConnectionVersion",2);in.put("confirmed",true);return in;}
 @Test void mappingIsExplicitVersionedAndNeverDuplicatesLedger()throws Exception{
  var n=row("A1","50000","0","").put("resCardNo","*************123").put("resCardName","테스트 조회 카드");sync(n);var g=mappings(a).path("groups").get(0);assertTrue(g.path("cardId").isNull());String card=productCard(a,"KB_EASY_ALL_AUTO",dev.cardadvisor.domain.ActualCardRules.KB);var in=mappingBody(g,card,0);in.put("confirmed",false);assertEquals(422,req(a,"PUT","/finance/connections/"+connection+"/card-mappings",in,null).status());assertEquals(0,count("financial_card_mappings"));in.put("confirmed",true);long rev=revision();
  var saved=ok(req(a,"PUT","/finance/connections/"+connection+"/card-mappings",in,null)).path("groups").get(0);assertEquals(card,saved.path("cardId").asText());assertEquals(1,saved.path("version").asLong());assertEquals(rev+1,revision());assertEquals("RULE_REVIEW_REQUIRED",saved.path("calculationStatus").asText());
  assertEquals(409,req(a,"PUT","/finance/connections/"+connection+"/card-mappings",in,null).status());long after=revision();ok(req(a,"PUT","/finance/connections/"+connection+"/card-mappings",mappingBody(saved,card,1),null));assertEquals(after,revision());sync(n);assertEquals(1,count("ledger_entries"));assertEquals(1,count("financial_transactions"));assertEquals(50000,spent());assertEquals(0,count("cancellations"));assertNotNull(rows().get(0).get("mapped_card_name"));
  var removed=ok(req(a,"PUT","/finance/connections/"+connection+"/card-mappings",mappingBody(saved,null,1),null)).path("groups").get(0);assertTrue(removed.path("cardId").isNull());assertEquals(2,removed.path("version").asLong());assertEquals(1,count("ledger_entries"));assertEquals(409,req(a,"PUT","/finance/connections/"+connection+"/card-mappings",in,null).status());
 }
 @Test void mappingOwnerIssuerAndVirtualRulesAreProtected()throws Exception{
  sync(row("A1","50000","0",""));var g=mappings(a).path("groups").get(0);String other=productCard(b,"KB_EASY_ALL_AUTO",dev.cardadvisor.domain.ActualCardRules.KB);assertEquals(404,req(b,"GET","/finance/connections/"+connection+"/card-mappings",null,null).status());assertEquals(404,req(a,"PUT","/finance/connections/"+connection+"/card-mappings",mappingBody(g,other,0),null).status());String hana=productCard(a,"HANA_TRAVELOG",dev.cardadvisor.domain.ActualCardRules.LOG);assertEquals(422,req(a,"PUT","/finance/connections/"+connection+"/card-mappings",mappingBody(g,hana,0),null).status());ok(req(a,"POST","/demo/setup",null,null));String virtual=ok(req(a,"GET","/cards",null,null)).get(0).path("id").asText();for(var c:ok(req(a,"GET","/cards",null,null)))if(!c.path("source").asText().startsWith("OFFICIAL_"))virtual=c.path("id").asText();assertEquals(422,req(a,"PUT","/finance/connections/"+connection+"/card-mappings",mappingBody(g,virtual,0),null).status());assertEquals(0,count("financial_card_mappings"));
 }
 @Test void mappingLeavesPerformanceUnknownAndBlocksUnreviewedRecommendation()throws Exception{
  sync(row("A1","50000","0",""));String card=productCard(a,"KB_EASY_ALL_AUTO",dev.cardadvisor.domain.ActualCardRules.KB);var g=mappings(a).path("groups").get(0);ok(req(a,"PUT","/finance/connections/"+connection+"/card-mappings",mappingBody(g,card,0),null));
  var perf=ok(req(a,"GET","/cards/performance?month="+DATE.toString().substring(0,7),null,null)).get(0);assertTrue(perf.path("performanceWon").isNull());assertTrue(perf.path("ledgerBenefitUsedWon").isNull());assertEquals("FINANCIAL_RULE_REVIEW_REQUIRED",perf.path("benefitSource").asText());assertEquals(0L,db.queryForObject("SELECT count(*) FROM ledger_entries WHERE user_id=? AND card_id IS NOT NULL",Long.class,userA));
  var checkout=ok(req(a,"POST","/checkout-sessions",Map.of("orderValueWon",50000,"pointUsedWon",0,"prepaidMoneyUsedWon",0,"cardChargeWon",50000,"category","SHOPPING","paymentMethod","NAVER_PAY_CARD","installments",1,"splitApproval","NO","confirmedByUser",true),null));var body=Map.of("checkoutId",checkout.path("id").asText(),"snapshotVersion",checkout.path("snapshotVersion").asLong(),"mode","BENEFIT","maxOpportunityCostWon",0);var rejected=req(a,"POST","/recommendations",body,null);assertEquals(409,rejected.status());assertEquals("FINANCIAL_CARD_CALCULATION_REVIEW_REQUIRED",rejected.body().path("error").path("code").asText());assertEquals(50000,spent());
 }
 @Test void ledgerDisplayMetadataIsOwnerScopedAndDoesNotChangeMoney()throws Exception{
  var n=row("DISPLAY1","50000","0","").put("resCardNo","*************123").put("resMemberStoreName","테스트 상점").put("resCardName","조회 카드");sync(n);
  String month=DATE.toString().substring(0,7);var before=ok(req(a,"GET","/transactions?month="+month,null,null));assertEquals(1,before.size());assertEquals("테스트 상점",before.get(0).path("merchant_name").asText());assertEquals("조회 카드",before.get(0).path("display_card_name").asText());assertEquals("123",before.get(0).path("card_last_digits").asText());assertFalse(before.get(0).has("card_mask"));assertEquals(0,ok(req(b,"GET","/transactions?month="+month,null,null)).size());
  String card=productCard(a,"KB_EASY_ALL_AUTO",dev.cardadvisor.domain.ActualCardRules.KB);var group=mappings(a).path("groups").get(0);ok(req(a,"PUT","/finance/connections/"+connection+"/card-mappings",mappingBody(group,card,0),null));
  long rev=revision();var perf=ok(req(a,"GET","/cards/performance?month="+month,null,null));var mapped=ok(req(a,"GET","/transactions?month="+month,null,null)).get(0);assertEquals(ok(req(a,"GET","/cards",null,null)).get(0).path("name").asText(),mapped.path("display_card_name").asText());assertTrue(mapped.path("card_id").isNull());assertEquals(50000,mapped.path("net_won").asLong());
  ok(req(a,"GET","/transactions?month="+month,null,null));assertEquals(rev,revision());assertEquals(perf,ok(req(a,"GET","/cards/performance?month="+month,null,null)));sync(n);assertEquals(1,count("ledger_entries"));
  // Use documented fixture helper for cumulative partial cancellation.
  sync(row("DISPLAY1","50000","2","20000").put("resCardNo","*************123").put("resMemberStoreName","테스트 상점").put("resCardName","조회 카드"));
  var partial=ok(req(a,"GET","/transactions?month="+month,null,null)).get(0);assertEquals(30000,partial.path("net_won").asLong());assertEquals(50000,partial.path("amount_won").asLong());
  sync(row("DISPLAY1","50000","1","50000").put("resCardNo","*************123").put("resMemberStoreName","테스트 상점").put("resCardName","조회 카드"));assertEquals(0,ok(req(a,"GET","/transactions?month="+month,null,null)).get(0).path("net_won").asLong());assertEquals(1,count("ledger_entries"));assertEquals(0,spent());
 }
 @Test void manualLedgerReadPreservesBudgetPerformanceAndBenefitAfterRefund()throws Exception{
  ok(req(a,"POST","/demo/setup",null,null));String month=DATE.toString().substring(0,7);var card=ok(req(a,"GET","/cards",null,null)).get(0);String cardId=card.path("id").asText();
  var baseline=ok(req(a,"GET","/cards/performance?month="+month,null,null));JsonNode base=null;for(var c:baseline)if(c.path("id").asText().equals(cardId))base=c;assertNotNull(base);
  var purchase=ok(req(a,"POST","/transactions/manual",Map.of("clientId",UUID.randomUUID(),"amountWon",50000,"cardId",cardId,"cardChargeWon",50000,"purchasedAt",DATE.toString(),"category","SHOPPING","included",true),null));
  for(int phase=0;phase<3;phase++){
   long net=phase==0?50000:phase==1?30000:0;long rev=revision();var perf=ok(req(a,"GET","/cards/performance?month="+month,null,null));var budget=ok(req(a,"GET","/budgets/"+month+"/summary",null,null));
   JsonNode current=null;for(var c:perf)if(c.path("id").asText().equals(cardId))current=c;assertNotNull(current);assertEquals(base.path("performanceWon").asLong()+net,current.path("performanceWon").asLong());assertEquals(base.path("currentQualified"),current.path("currentQualified"));long benefit=net*card.path("rateBp").asLong()/10000;assertEquals(benefit,current.path("ledgerBenefitUsedWon").asLong());assertEquals(base.path("remainingCapWon").asLong()-benefit,current.path("remainingCapWon").asLong());
   var entry=ok(req(a,"GET","/transactions?month="+month,null,null)).get(0);assertEquals(net,entry.path("net_won").asLong());assertEquals(card.path("name").asText(),entry.path("display_card_name").asText());assertTrue(entry.path("merchant_name").isNull());assertTrue(entry.path("card_last_digits").isNull());assertEquals(net,budget.path("spentWon").asLong());assertEquals(rev,revision());assertEquals(perf,ok(req(a,"GET","/cards/performance?month="+month,null,null)));
   if(phase<2)ok(req(a,"POST","/transactions/"+purchase.path("id").asText()+"/cancellations",Map.of("clientId",UUID.randomUUID(),"amountWon",phase==0?20000:30000,"cardRefundWon",phase==0?20000:30000),null));
  }
 }
 @Test void matchedManualLedgerNeverDisplaysVirtualCardAsActualFinancialCard()throws Exception{
  ok(req(a,"POST","/demo/setup",null,null));var card=ok(req(a,"GET","/cards",null,null)).get(0);String month=DATE.toString().substring(0,7);
  var purchase=ok(req(a,"POST","/transactions/manual",Map.of("clientId",UUID.randomUUID(),"amountWon",50000,"cardId",card.path("id").asText(),"cardChargeWon",50000,"purchasedAt",DATE.toString(),"category","SHOPPING","included",true),null));
  sync(row("MATCH","50000","0","").put("resCardName","조회된 실제 카드").put("resMemberStoreName","테스트 실제 상점"));resolve(a,rows().get(0),"LINK_MANUAL","ledgerId",purchase.path("id").asText());
  var entry=ok(req(a,"GET","/transactions?month="+month,null,null)).get(0);assertEquals("조회된 실제 카드",entry.path("display_card_name").asText());assertEquals("테스트 실제 상점",entry.path("merchant_name").asText());assertEquals(card.path("name").asText(),entry.path("card_name").asText());assertEquals(1,count("ledger_entries"));
  db.update("UPDATE financial_transactions SET card_label=NULL WHERE user_id=?",userA);entry=ok(req(a,"GET","/transactions?month="+month,null,null)).get(0);assertTrue(entry.path("display_card_name").isNull());assertEquals(50000,entry.path("net_won").asLong());
 }

 JsonNode review(JsonNode t)throws Exception{return ok(req(a,"GET","/finance/transactions/"+t.path("id").asText()+"/performance-review",null,null));}
 Map<String,Object> reviewBody(JsonNode v,String decision,String receipt){var body=new LinkedHashMap<String,Object>();body.put("expectedVersion",v.path("version").asLong());body.put("expectedTransactionVersion",v.path("transactionVersion").asLong());body.put("expectedMappingVersion",v.path("mappingVersion").asLong());body.put("decision",decision);body.put("confirmed",true);var dates=new ArrayList<Map<String,Object>>();for(var f:v.path("refunds")){var d=new LinkedHashMap<String,Object>();d.put("cancellationId",f.path("cancellationId").asText());d.put("receiptDate",receipt);dates.add(d);}body.put("refunds",dates);return body;}
 JsonNode saveReview(JsonNode t,String decision,String receipt)throws Exception{var v=review(t);return ok(req(a,"PUT","/finance/transactions/"+t.path("id").asText()+"/performance-review",reviewBody(v,decision,receipt),null));}
 String mappedKB()throws Exception{String c=productCard(a,"KB_EASY_ALL_AUTO",dev.cardadvisor.domain.ActualCardRules.KB);ok(req(a,"PUT","/finance/connections/"+connection+"/card-mappings",mappingBody(mappings(a).path("groups").get(0),c,0),null));return c;}
 JsonNode cardPerformance(String card,String month)throws Exception{for(var p:ok(req(a,"GET","/cards/performance?month="+month,null,null)))if(p.path("id").asText().equals(card))return p;throw new AssertionError("Card missing");}
 void baseline(String card,String month)throws Exception{ok(req(a,"PUT","/cards/"+card+"/months/"+month,Map.of("untrackedPerformanceWon",450000,"currentQualified",false,"remainingCapWon",10000,"version",0),null));}
 @Test void financialPerformanceDecisionsRefundsAndIsolation()throws Exception{
  sync(row("P1","90000","0",""));String card=mappedKB(),month=DATE.toString().substring(0,7),id=rows().get(0).path("id").asText();var t=rows().get(0);baseline(card,month);
  assertTrue(cardPerformance(card,month).path("performanceWon").isNull());assertEquals("UNKNOWN",review(t).path("decision").asText());
  var v=saveReview(t,"INCLUDE",null);var p=cardPerformance(card,month);assertEquals(540000,p.path("performanceWon").asLong());assertTrue(p.path("nextMonthThresholdMet").asBoolean());assertFalse(p.path("currentQualified").asBoolean());assertEquals(90000,p.path("ledgerPerformanceWon").asLong());assertTrue(p.path("ledgerBenefitUsedWon").isNull());long revision=revision();saveReview(t,"INCLUDE",null);assertEquals(revision,revision());
  assertEquals(404,req(b,"GET","/finance/transactions/"+id+"/performance-review",null,null).status());assertEquals(404,req(b,"PUT","/finance/transactions/"+id+"/performance-review",reviewBody(v,"EXCLUDE",null),null).status());assertEquals(404,req(b,"GET","/cards/"+card+"/financial-performance?month="+month,null,null).status());
  saveReview(t,"EXCLUDE",null);assertEquals(450000,cardPerformance(card,month).path("performanceWon").asLong());assertEquals(409,req(a,"PUT","/finance/transactions/"+id+"/performance-review",reviewBody(v,"INCLUDE",null),null).status());
  saveReview(t,"UNKNOWN",null);assertTrue(cardPerformance(card,month).path("performanceWon").isNull());saveReview(t,"INCLUDE",null);
  sync(row("P1","90000","2","20000"));assertEquals("UNKNOWN",review(t).path("decision").asText());assertTrue(cardPerformance(card,month).path("performanceWon").isNull());saveReview(t,"INCLUDE",null);
  var subtotal=ok(req(a,"GET","/cards/"+card+"/financial-performance?month="+month,null,null));assertTrue(subtotal.path("amountWon").isNull());assertEquals(90000,subtotal.path("confirmedSubtotalWon").asLong());assertEquals(1,subtotal.path("unknownRefundDateCount").asInt());
  assertEquals(422,req(a,"PUT","/finance/transactions/"+id+"/performance-review",reviewBody(review(t),"INCLUDE",DATE.minusDays(1).toString()),null).status());
  saveReview(t,"INCLUDE",DATE.toString());assertEquals(520000,cardPerformance(card,month).path("performanceWon").asLong());sync(row("P1","90000","2","20000"));assertEquals(520000,cardPerformance(card,month).path("performanceWon").asLong());assertEquals(1,count("cancellations"));
  sync(row("P1","90000","1","90000"));assertTrue(cardPerformance(card,month).path("performanceWon").isNull());saveReview(t,"INCLUDE",DATE.toString());assertEquals(450000,cardPerformance(card,month).path("performanceWon").asLong());assertEquals(1,count("ledger_entries"));assertEquals(2,count("cancellations"));assertEquals(0,spent());assertFalse(cardPerformance(card,month).path("currentQualified").asBoolean());
 }
 @Test void financialPerformanceMissingBaselineInstallmentAndBudgetIndependence()throws Exception{
  sync(row("PI","90000","0","").put("resPaymentType","2").put("resInstallmentMonth","3"));String card=mappedKB(),month=DATE.toString().substring(0,7);var t=rows().get(0);saveReview(t,"INCLUDE",null);
  assertTrue(cardPerformance(card,month).path("performanceWon").isNull());assertEquals(90000,cardPerformance(card,month).path("ledgerPerformanceWon").asLong());baseline(card,month);
  UUID ledger=UUID.fromString(t.path("ledger_id").asText());db.update("UPDATE ledger_entries SET included=false,category='HOUSING' WHERE user_id=? AND id=?",userA,ledger);assertEquals(540000,cardPerformance(card,month).path("performanceWon").asLong());
  // Legacy manual-card match must not count the same financial approval twice.
  db.update("UPDATE ledger_entries SET card_id=?,card_charge_won=90000,performance_eligible=true WHERE user_id=? AND id=?",UUID.fromString(card),userA,ledger);assertEquals(540000,cardPerformance(card,month).path("performanceWon").asLong());
  assertEquals(422,req(a,"PUT","/finance/transactions/"+t.path("id").asText()+"/performance-review",reviewBody(review(t),"INVALID",null),null).status());
 }
 @Test void financialPerformanceReceiptMonthAndSeparateCancel()throws Exception{
  sync(row("PA","90000","0",""));String card=mappedKB(),month=DATE.toString().substring(0,7);var t=rows().get(0);LocalDate previous=DATE.minusMonths(1);db.update("UPDATE financial_transactions SET used_date=? WHERE user_id=? AND id=?",previous,userA,UUID.fromString(t.path("id").asText()));baseline(card,month);
  saveReview(t,"INCLUDE",null);sync(row("PC","-20000","1",""));var cancel=rows().get(0);for(var item:rows())if(item.path("kind").asText().equals("CANCEL_EVENT"))cancel=item;
  assertTrue(cardPerformance(card,month).path("performanceWon").isNull());resolve(a,cancel,"LINK_CANCELLATION","approvalId",t.path("id").asText());assertEquals("UNKNOWN",review(t).path("decision").asText());saveReview(t,"INCLUDE",DATE.toString());
  assertEquals(430000,cardPerformance(card,month).path("performanceWon").asLong());assertEquals(-20000,cardPerformance(card,month).path("ledgerPerformanceWon").asLong());assertEquals(90000,cardPerformance(card,previous.toString().substring(0,7)).path("ledgerPerformanceWon").asLong());assertEquals(70000,spent());
  var group=mappings(a).path("groups").get(0);ok(req(a,"PUT","/finance/connections/"+connection+"/card-mappings",mappingBody(group,null,1),null));group=mappings(a).path("groups").get(0);ok(req(a,"PUT","/finance/connections/"+connection+"/card-mappings",mappingBody(group,card,2),null));assertEquals("UNKNOWN",review(t).path("decision").asText());assertTrue(cardPerformance(card,month).path("performanceWon").isNull());
 }

 JsonNode benefitReview(JsonNode t)throws Exception{return ok(req(a,"GET","/finance/transactions/"+t.path("id").asText()+"/benefit-review",null,null));}
 Map<String,Object> benefitBody(JsonNode t,String decision,String area){var body=new LinkedHashMap<String,Object>();body.put("expectedVersion",t.path("version").asLong());body.put("expectedTransactionVersion",t.path("transactionVersion").asLong());body.put("expectedMappingVersion",t.path("mappingVersion").asLong());body.put("decision",decision);body.put("area",area);body.put("conditionsConfirmed",true);return body;}
 JsonNode benefit(String card,String month)throws Exception{return ok(req(a,"GET","/cards/"+card+"/financial-benefits?month="+month,null,null));}
 void benefitMonth(String card,String month,int tier,boolean complete)throws Exception{var v=benefit(card,month);ok(req(a,"PUT","/cards/"+card+"/financial-benefits?month="+month,Map.of("expectedVersion",v.path("version").asLong(),"expectedQualificationVersion",v.path("qualificationVersion").asLong(),"tier",tier,"coverageConfirmed",complete),null));}
 void saveBenefit(JsonNode t,String decision,String area)throws Exception{ok(req(a,"PUT","/finance/transactions/"+t.path("id").asText()+"/benefit-review",benefitBody(benefitReview(t),decision,area),null));}
 @Test void benefitsCoverageCapsCancellationAndRecommendation()throws Exception{
  ok(req(a,"POST","/demo/setup",null,null));sync(row("BEN1","90000","0",""));String card=mappedKB(),month=DATE.toString().substring(0,7);var t=rows().get(0);ok(req(a,"PUT","/cards/"+card+"/months/"+month,Map.of("untrackedPerformanceWon",450000,"currentQualified",true,"remainingCapWon",10000,"version",0),null));saveReview(t,"INCLUDE",null);
  assertTrue(benefit(card,month).path("estimatedUsedWon").isNull());saveBenefit(t,"RULE","FOOD");assertTrue(benefit(card,month).path("estimatedUsedWon").isNull());benefitMonth(card,month,1,true);var b=benefit(card,month);assertEquals(2700,b.path("estimatedUsedWon").asLong());assertEquals(13300,b.path("estimatedRemainingCapWon").asLong());assertEquals(2700,cardPerformance(card,month).path("ledgerBenefitUsedWon").asLong());assertEquals(13300,cardPerformance(card,month).path("remainingCapWon").asLong());
  long rev=revision();saveBenefit(t,"RULE","FOOD");benefitMonth(card,month,1,true);assertEquals(rev,revision());var stale=benefitReview(t);saveBenefit(t,"EXCLUDE",null);assertEquals(0,benefit(card,month).path("estimatedUsedWon").asLong());assertEquals(409,req(a,"PUT","/finance/transactions/"+t.path("id").asText()+"/benefit-review",benefitBody(stale,"RULE","FOOD"),null).status());saveBenefit(t,"RULE","FOOD");
  sync(row("BEN1","90000","2","20000"));assertTrue(benefit(card,month).path("estimatedUsedWon").isNull());saveBenefit(t,"RULE","FOOD");assertEquals(2100,benefit(card,month).path("estimatedUsedWon").asLong());assertEquals(13900,benefit(card,month).path("estimatedRemainingCapWon").asLong());sync(row("BEN1","90000","2","20000"));assertEquals(1,count("cancellations"));
  sync(row("BEN1","90000","1","90000"));assertTrue(benefit(card,month).path("estimatedUsedWon").isNull());saveBenefit(t,"RULE","FOOD");saveReview(t,"INCLUDE",DATE.toString());assertEquals(0,benefit(card,month).path("estimatedUsedWon").asLong());assertEquals(16000,benefit(card,month).path("estimatedRemainingCapWon").asLong());
  var checkout=ok(req(a,"POST","/checkout-sessions",Map.of("orderValueWon",50000,"pointUsedWon",0,"prepaidMoneyUsedWon",0,"cardChargeWon",50000,"category","SHOPPING","paymentMethod","NAVER_PAY_CARD","installments",1,"splitApproval","NO","confirmedByUser",true,"purchaseFacts",Map.of("merchant","NAVER_PLUS_STORE","purchaseKind","GENERAL_GOODS","confirmed",true)),null));
  var recommendation=ok(req(a,"POST","/recommendations",Map.of("checkoutId",checkout.path("id").asText(),"snapshotVersion",checkout.path("snapshotVersion").asLong(),"mode","BENEFIT","maxOpportunityCostWon",0),null));assertEquals(card,recommendation.path("result").path("recommendedCardId").asText());assertEquals(0,spent());saveBenefit(t,"UNKNOWN",null);assertEquals("INVALIDATED",ok(req(a,"GET","/recommendations/"+recommendation.path("id").asText(),null,null)).path("validity").asText());
 }
 @Test void benefitsIsolationQualificationVersionAndIncompleteFacts()throws Exception{
  sync(row("BEN2","90000","0",""));String card=mappedKB(),month=DATE.toString().substring(0,7);var t=rows().get(0);baseline(card,month);assertEquals(404,req(b,"GET","/finance/transactions/"+t.path("id").asText()+"/benefit-review",null,null).status());assertEquals(404,req(b,"GET","/cards/"+card+"/financial-benefits?month="+month,null,null).status());assertEquals(404,req(b,"PUT","/finance/transactions/"+t.path("id").asText()+"/benefit-review",benefitBody(benefitReview(t),"EXCLUDE",null),null).status());
  var v=benefit(card,month);assertEquals(422,req(a,"PUT","/cards/"+card+"/financial-benefits?month="+month,Map.of("expectedVersion",0,"expectedQualificationVersion",1,"tier",1,"coverageConfirmed",true),null).status());saveBenefit(t,"EXCLUDE",null);benefitMonth(card,month,0,true);assertEquals(0,benefit(card,month).path("estimatedUsedWon").asLong());
  ok(req(a,"PUT","/cards/"+card+"/months/"+month,Map.of("untrackedPerformanceWon",450000,"currentQualified",true,"remainingCapWon",10000,"version",1),null));assertFalse(benefit(card,month).path("coverageConfirmed").asBoolean());assertTrue(benefit(card,month).path("estimatedUsedWon").isNull());benefitMonth(card,month,1,true);saveBenefit(t,"RULE","COFFEE");assertEquals(4000,benefit(card,month).path("estimatedUsedWon").asLong());
  var bad=benefitBody(benefitReview(t),"RULE","FOREIGN_DUTYFREE");assertEquals(422,req(a,"PUT","/finance/transactions/"+t.path("id").asText()+"/benefit-review",bad,null).status());sync(row("BEN2","90000","0","").put("resPaymentType","2").put("resInstallmentMonth","3"));assertEquals(422,req(a,"PUT","/finance/transactions/"+t.path("id").asText()+"/benefit-review",benefitBody(benefitReview(t),"RULE","FOOD"),null).status());
 }

 @Test void privateMerchantSuggestionAndPeriodComparisonKeepCorrectionsAndUnknowns()throws Exception{
  ok(req(a,"POST","/demo/setup",null,null));sync(row("AI1","90000","0","").put("resMemberStoreName","비공개 테스트 커피점"));var entry=ok(req(a,"GET","/transactions?month="+DATE.toString().substring(0,7),null,null)).get(0);String id=entry.path("id").asText();var suggestion=ok(req(a,"POST","/transactions/"+id+"/classification-suggestion",null,null));assertEquals("FOOD",suggestion.path("category").asText());assertFalse(suggestion.toString().contains("비공개 테스트 커피점"));
  ok(req(a,"PUT","/transactions/"+id+"/classification",Map.of("version",entry.path("classification_version").asLong(),"category","TRANSPORT","included",true),null));assertEquals("TRANSPORT",ok(req(a,"POST","/transactions/"+id+"/classification-suggestion",null,null)).path("category").asText());
  var comparison=ok(req(a,"GET","/insights?month="+DATE.toString().substring(0,7),null,null)).path("comparison");assertEquals(90000,comparison.path("knownCurrentWon").asLong());assertEquals(0,comparison.path("knownPreviousWon").asLong());assertEquals(90000,comparison.path("differenceWon").asLong());
  sync(row("AI2","50000","2",""));comparison=ok(req(a,"GET","/insights?month="+DATE.toString().substring(0,7),null,null)).path("comparison");assertTrue(comparison.path("differenceWon").isNull());assertEquals(90000,comparison.path("knownCurrentWon").asLong());assertEquals("PARTIAL_KNOWN_TRANSACTIONS",comparison.path("status").asText());
 }
 String otherAccount()throws Exception{if("OTHER_TEST_ACCOUNT".equals(b)){b=register();userB=users.get(users.size()-1);}return b;}
 JsonNode authChallenge(String method){return JSON.createObjectNode().put("method",method).put("jobIndex",0).put("threadIndex",0).put("jti","PRIVATE_CANARY").put("twoWayTimestamp",System.currentTimeMillis()).put("phoneNo","PRIVATE_CANARY");}
 JsonNode pendingAuth(String method)throws Exception{var behavior=response(authChallenge(method));behavior.code="CF-03002";var r=ok(req(a,"POST","/finance/connections/"+connection+"/sync",input(),UUID.randomUUID()));assertEquals("AUTH_REQUIRED",r.path("status").asText());return r;}
 Reply answer(String token,JsonNode r,UUID key)throws Exception{return req(token,"POST","/finance/sync-runs/"+r.path("id").asText()+"/authenticate",Map.of("expectedVersion",r.at("/authentication/version").asLong(),"answer",r.at("/authentication/method").asText().equals("simpleAuth")?"1":"123456"),key);}
 @Test void twoWayResumeEncryptedIsolatedAndIdempotent()throws Exception{
  var r=pendingAuth("smsAuthNo");assertEquals(0,count("ledger_entries"));var stored=db.queryForMap("SELECT * FROM financial_sync_auth WHERE user_id=?",userA);assertNotNull(stored.get("ciphertext"));assertFalse(new String((byte[])stored.get("ciphertext"),java.nio.charset.StandardCharsets.UTF_8).contains("PRIVATE_CANARY"));
  assertEquals(404,answer(b,r,UUID.randomUUID()).status());assertEquals(409,req(a,"POST","/finance/connections/"+connection+"/sync",input(),UUID.randomUUID()).status());
  response(row("AUTH1","50000","0",""));UUID key=UUID.randomUUID();assertEquals("IMPORTED",ok(answer(a,r,key)).path("status").asText());int calls=provider.calls.size();assertEquals("IMPORTED",ok(answer(a,r,key)).path("status").asText());assertEquals(calls,provider.calls.size());assertEquals(1,count("ledger_entries"));assertNull(db.queryForObject("SELECT ciphertext FROM financial_sync_auth WHERE user_id=?",byte[].class,userA));sync(row("AUTH1","50000","0",""));assertEquals(1,count("ledger_entries"));
 }
 @Test void repeatedChallengeRequiresNewVersionAndExplicitAnswer()throws Exception{
  var first=pendingAuth("smsAuthNo");var response=response(authChallenge("simpleAuth"));response.code="CF-03002";UUID key=UUID.randomUUID();var next=ok(answer(a,first,key));assertEquals(2,next.at("/authentication/version").asLong());assertEquals("AUTH_REQUIRED",next.path("status").asText());int calls=provider.calls.size();ok(answer(a,first,key));assertEquals(calls,provider.calls.size());assertEquals(409,answer(a,first,UUID.randomUUID()).status());response(row("AUTH2","20000","0",""));assertEquals("IMPORTED",ok(answer(a,next,UUID.randomUUID())).path("status").asText());
 }
 @Test void authExpiryPurgesCiphertextAndDoesNotSendAnswer()throws Exception{
  var r=pendingAuth("emailAuthNo");db.update("UPDATE financial_sync_auth SET expires_at=now()-interval '1 second' WHERE user_id=?",userA);int calls=provider.calls.size();var expired=ok(req(a,"GET","/finance/sync-runs/"+r.path("id").asText(),null,null));assertEquals("FAILED",expired.path("status").asText());assertEquals("CODEF_AUTH_EXPIRED",expired.path("error_code").asText());assertEquals(409,answer(a,r,UUID.randomUUID()).status());assertEquals(calls,provider.calls.size());assertNull(db.queryForObject("SELECT ciphertext FROM financial_sync_auth WHERE user_id=?",byte[].class,userA));
 }
 @Test void ambiguousAuthNeverReplaysAndPreservesLedger()throws Exception{
  var r=pendingAuth("smsAuthNo");var fail=response(JSON.createArrayNode());fail.status=500;UUID key=UUID.randomUUID();assertEquals("AUTH_UNKNOWN",ok(answer(a,r,key)).path("status").asText());int calls=provider.calls.size();ok(answer(a,r,key));assertEquals(409,answer(a,r,UUID.randomUUID()).status());assertEquals(409,req(a,"POST","/finance/connections/"+connection+"/sync",input(),UUID.randomUUID()).status());assertEquals(calls,provider.calls.size());assertEquals(0,count("ledger_entries"));
 }
 @Test void changedConnectionCannotConsumeChallenge()throws Exception{
  var r=pendingAuth("simpleAuth");db.update("UPDATE financial_connections SET version=version+1 WHERE user_id=?",userA);int calls=provider.calls.size();assertEquals(409,answer(a,r,UUID.randomUUID()).status());assertEquals(calls,provider.calls.size());assertEquals(0,count("ledger_entries"));
 }
 @Test void invalidAnswerLeavesChallengeAvailable()throws Exception{
  var r=pendingAuth("smsAuthNo");int calls=provider.calls.size();assertEquals(422,req(a,"POST","/finance/sync-runs/"+r.path("id").asText()+"/authenticate",Map.of("expectedVersion",1,"answer","not an OTP"),UUID.randomUUID()).status());assertEquals(calls,provider.calls.size());assertEquals("AUTH_REQUIRED",ok(req(a,"GET","/finance/sync-runs/"+r.path("id").asText(),null,null)).path("status").asText());
 }
 Map<String,Object> qualificationBody(JsonNode state,Long amount,String date){var in=new LinkedHashMap<String,Object>();in.put("expectedVersion",state.path("version").asLong());in.put("expectedStateVersion",state.path("stateVersion").asLong());in.put("previousPerformanceWon",amount);in.put("previousSource",amount==null?"UNKNOWN":"USER_INPUT");in.put("asOf",amount==null?null:LocalDate.now(ZoneId.of("Asia/Seoul")).toString());in.put("firstUseDate",date);in.put("confirmed",true);return in;}
 @Test void actualQualificationEvidencePersistsWithoutChangingNextMonthPerformance()throws Exception{
  sync(row("QUAL1","90000","0",""));String card=mappedKB(),m=DATE.toString().substring(0,7);baseline(card,m);saveReview(rows().get(0),"INCLUDE",null);var original=cardPerformance(card,m);String path="/cards/"+card+"/qualification?month="+m;var old=ok(req(a,"GET",path,null,null));var body=qualificationBody(old,0L,DATE.toString());long rev=revision();var saved=ok(req(a,"PUT",path,body,null));assertTrue(saved.at("/calculation/registrationGrace").asBoolean());assertEquals(1,saved.at("/calculation/tier").asInt());assertEquals(saved,ok(req(a,"GET",path,null,null)));assertTrue(revision()>rev);assertEquals(original.path("performanceWon"),cardPerformance(card,m).path("performanceWon"));assertTrue(cardPerformance(card,m).path("currentQualified").asBoolean());assertEquals(1,benefit(card,m).path("tier").asInt());assertFalse(benefit(card,m).path("coverageConfirmed").asBoolean());assertEquals(409,req(a,"PUT",path,body,null).status());assertEquals(404,req(b,"GET",path,null,null).status());assertEquals(404,req(b,"PUT",path,body,null).status());
  var high=ok(req(a,"PUT",path,qualificationBody(saved,1500000L,DATE.toString()),null));assertEquals(3,high.at("/calculation/tier").asInt());var bs=benefit(card,m);assertEquals(422,req(a,"PUT","/cards/"+card+"/financial-benefits?month="+m,Map.of("expectedVersion",bs.path("version").asLong(),"expectedQualificationVersion",bs.path("qualificationVersion").asLong(),"tier",1,"coverageConfirmed",true),null).status());var unknown=ok(req(a,"PUT",path,qualificationBody(high,null,null),null));assertTrue(unknown.at("/calculation/qualified").isNull());assertTrue(cardPerformance(card,m).path("currentQualified").isNull());assertEquals(original.path("performanceWon"),cardPerformance(card,m).path("performanceWon"));assertFalse(benefit(card,m).path("coverageConfirmed").asBoolean());
 }
 @Test void qualificationRejectsInventedSourcesFutureRegistrationAndPartialPreviousMonth()throws Exception{
  String card=productCard(a,"HANA_TRAVELGO_BANK",dev.cardadvisor.domain.ActualCardRules.GO),m=DATE.toString().substring(0,7),path="/cards/"+card+"/qualification?month="+m;var state=ok(req(a,"GET",path,null,null));var body=qualificationBody(state,200000L,null);body.put("previousSource","OFFICIAL_QUERY");assertEquals(422,req(a,"PUT",path,body,null).status());body.put("previousSource","USER_INPUT");body.put("firstUseDate",LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1).toString());assertEquals(422,req(a,"PUT",path,body,null).status());body.put("firstUseDate",null);body.put("asOf",DATE.minusMonths(1).toString());assertEquals(422,req(a,"PUT",path,body,null).status());
 }
 @BeforeEach void setup()throws Exception{db.update("DELETE FROM request_limits WHERE bucket_hash=?",dev.cardadvisor.auth.Passwords.sha("auth-ip:synthetic-finance:127.0.0.1:"+(java.time.Instant.now().getEpochSecond()/60)));provider.calls.clear();provider.behaviors.clear();a=register();userA=users.get(0);b="OTHER_TEST_ACCOUNT";connection=connect(a,"0301");}
 @AfterEach void cleanup(){for(UUID u:users)db.update("DELETE FROM app_users WHERE id=?",u);for(UUID u:users)assertEquals(0L,db.queryForObject("SELECT count(*) FROM app_users WHERE id=?",Long.class,u));}
 Map<String,Object> input(){return Map.of("from",DATE.toString(),"to",LocalDate.now(ZoneId.of("Asia/Seoul")).toString(),"expectedVersion",2);}
 FinancialConnectionsPostgresTest.Behavior response(JsonNode data){var behavior=new FinancialConnectionsPostgresTest.Behavior();behavior.approvalData=data;provider.behaviors.add(behavior);return behavior;}
 JsonNode sync(JsonNode data)throws Exception{response(data);return ok(req(a,"POST","/finance/connections/"+connection+"/sync",input(),UUID.randomUUID()));}
 JsonNode rows()throws Exception{return ok(req(a,"GET","/finance/connections/"+connection+"/transactions",null,null));}
 long count(String table){return db.queryForObject("SELECT count(*) FROM "+table+" WHERE user_id=?",Long.class,userA);}
 long spent(){return db.queryForObject("SELECT COALESCE(sum(amount_won),0) FROM ledger_entries WHERE user_id=?",Long.class,userA)-db.queryForObject("SELECT COALESCE(sum(amount_won),0) FROM cancellations WHERE user_id=?",Long.class,userA);}
 long revision(){return db.queryForObject("SELECT revision FROM app_users WHERE id=?",Long.class,userA);}
 JsonNode resolve(String token,JsonNode row,String action,String field,String value)throws Exception{var body=new LinkedHashMap<String,Object>();body.put("version",row.path("version").asLong());body.put("action",action);if(field!=null)body.put(field,value);return ok(req(token,"POST","/finance/transactions/"+row.path("id").asText()+"/resolve",body,null));}
 @Test void repeatOverlappingAndIdempotency()throws Exception{
  var purchase=row("A1","50000","0","").put("commSession","PRIVATE_CANARY").put("resMemberStoreAddr","PRIVATE_CANARY");
  UUID key=UUID.randomUUID();response(JSON.createArrayNode().add(purchase).add(purchase));var first=ok(req(a,"POST","/finance/connections/"+connection+"/sync",input(),key));assertEquals("IMPORTED",first.path("status").asText());long rev=revision();
  var replay=ok(req(a,"POST","/finance/connections/"+connection+"/sync",input(),key));assertEquals(first.path("id"),replay.path("id"));assertEquals(2,provider.calls.size());
  sync(purchase);assertEquals(1,count("ledger_entries"));assertEquals(1,count("financial_transactions"));assertEquals(50000,spent());assertEquals(rev,revision());
  String persisted=db.queryForObject("SELECT row_to_json(t)::text FROM financial_transactions t WHERE user_id=?",String.class,userA);assertFalse(persisted.contains("PRIVATE_CANARY"));assertFalse(persisted.contains("1234567812345678"));
  var changed=new HashMap<>(input());changed.put("expectedVersion",1);assertEquals(409,req(a,"POST","/finance/connections/"+connection+"/sync",changed,key).status());
 }
 @Test void partialRefundReplayRegressionAndFullCancel()throws Exception{
  sync(row("A1","50000","0",""));long rev=revision();sync(row("A1","50000","2","20000"));assertEquals(30000,spent());assertTrue(revision()>rev);
  sync(row("A1","50000","2","20000"));assertEquals(1,count("cancellations"));sync(row("A1","50000","2","30000"));assertEquals(20000,spent());assertEquals(2,count("cancellations"));
  assertEquals("PARTIAL",sync(row("A1","50000","2","10000")).path("status").asText());assertEquals(20000,spent());assertEquals("REFUND_REGRESSION",rows().get(0).path("issue").asText());
  sync(row("A1","50000","1","50000"));assertEquals(0,spent());assertEquals(3,count("cancellations"));
 }
 @Test void installmentAndUnknownValues()throws Exception{
  var installment=row("A1","90000","0","").put("resPaymentType","2").put("resInstallmentMonth","3");
  sync(JSON.createArrayNode().add(installment).add(row("A2","50000","2","")).add(row("A3","50000","3","")).add(row("A4","20.5","0","").put("resAccountCurrency","USD")));
  assertEquals(90000,spent());assertEquals(1,count("ledger_entries"));assertEquals(4,count("financial_transactions"));assertEquals(3,db.queryForObject("SELECT installments FROM financial_transactions WHERE user_id=? AND status='APPLIED'",Integer.class,userA));
  assertEquals(0L,db.queryForObject("SELECT count(*) FROM ledger_entries WHERE user_id=? AND performance_rule_version IS NOT NULL",Long.class,userA));
 }
 @Test void cancellationSelectionRejectsFutureAndExcessWithoutLinking()throws Exception{
  sync(JSON.createArrayNode().add(row("A1","50000","0","")).add(row("C1","-20000","1","")));
  JsonNode original=null,cancel=null;for(var item:rows()){if(item.path("kind").asText().equals("CANCEL_EVENT"))cancel=item;else original=item;}
  UUID originalId=UUID.fromString(original.path("id").asText()),cancelId=UUID.fromString(cancel.path("id").asText());
  var body=Map.of("version",cancel.path("version").asLong(),"action","LINK_CANCELLATION","approvalId",originalId.toString());
  db.update("UPDATE financial_transactions SET used_date=? WHERE user_id=? AND id=?",DATE.plusDays(1),userA,originalId);
  assertEquals(422,req(a,"POST","/finance/transactions/"+cancelId+"/resolve",body,null).status());
  db.update("UPDATE financial_transactions SET used_date=? WHERE user_id=? AND id=?",DATE,userA,originalId);
  db.update("UPDATE financial_transactions SET cancelled_won=60000 WHERE user_id=? AND id=?",userA,cancelId);
  assertEquals(422,req(a,"POST","/finance/transactions/"+cancelId+"/resolve",body,null).status());
  assertEquals(0,count("cancellations"));assertNull(db.queryForMap("SELECT linked_transaction_id FROM financial_transactions WHERE user_id=? AND id=?",userA,cancelId).get("linked_transaction_id"));
 }
 @Test void separateCancellationOwnerMatchAndOverRefund()throws Exception{
  sync(JSON.createArrayNode().add(row("A1","50000","0","")).add(row("C1","-20000","1","")));
  JsonNode original=null,cancel=null;for(var r:rows()){if(r.path("kind").asText().equals("CANCEL_EVENT"))cancel=r;else original=r;}
  assertEquals(50000,spent());assertEquals("CANCELLATION_MATCH_REQUIRED",cancel.path("issue").asText());
  assertEquals(404,req(b,"POST","/finance/transactions/"+cancel.path("id").asText()+"/resolve",Map.of("version",cancel.path("version").asLong(),"action","LINK_CANCELLATION","approvalId",original.path("id").asText()),null).status());
  resolve(a,cancel,"LINK_CANCELLATION","approvalId",original.path("id").asText());assertEquals(30000,spent());
  sync(row("C1","-20000","1",""));assertEquals(1,count("cancellations"));sync(row("C1","-60000","1",""));assertEquals(30000,spent());
  sync(row("A1","50000","2","20000"));assertEquals(30000,spent());assertEquals(1,count("cancellations"));
 }
 @Test void manualDuplicateConfirmationPreservesClassification()throws Exception{
  UUID manual=UUID.randomUUID();db.update("INSERT INTO ledger_entries(id,user_id,client_id,amount_won,purchased_at,category,included,source,original_category,original_included) VALUES (?,?,?,?,?,'FOOD',false,'MANUAL','FOOD',false)",manual,userA,UUID.randomUUID(),50000,DATE);
  sync(row("A1","50000","2","20000"));assertEquals(1,count("ledger_entries"));var held=rows().get(0);assertEquals("MANUAL_DUPLICATE_REVIEW",held.path("issue").asText());
  assertEquals(409,req(a,"POST","/finance/transactions/"+held.path("id").asText()+"/resolve",Map.of("version",0,"action","LINK_MANUAL","ledgerId",manual.toString()),null).status());
  resolve(a,held,"LINK_MANUAL","ledgerId",manual.toString());assertEquals(1,count("ledger_entries"));assertEquals(30000,spent());sync(row("A1","50000","2","20000"));assertEquals(1,count("cancellations"));
  assertEquals("FOOD",db.queryForObject("SELECT category FROM ledger_entries WHERE id=?",String.class,manual));assertFalse(db.queryForObject("SELECT included FROM ledger_entries WHERE id=?",Boolean.class,manual));
  assertEquals(409,req(a,"POST","/transactions/"+manual+"/cancellations",Map.of("clientId",UUID.randomUUID(),"amountWon",1),null).status());
 }
 @Test void sameApprovalAndKeyAreIsolatedByOwner()throws Exception{
  UUID other=connect(b,"0313"),key=UUID.randomUUID();response(row("A1","50000","0",""));var first=ok(req(a,"POST","/finance/connections/"+connection+"/sync",input(),key));response(row("A1","50000","0",""));ok(req(b,"POST","/finance/connections/"+other+"/sync",input(),key));
  assertEquals(1,count("ledger_entries"));assertEquals(1L,db.queryForObject("SELECT count(*) FROM ledger_entries WHERE user_id=?",Long.class,userB));assertEquals(404,req(b,"GET","/finance/connections/"+connection+"/transactions",null,null).status());assertEquals(404,req(b,"GET","/finance/sync-runs/"+first.path("id").asText(),null,null).status());assertEquals(404,req(b,"POST","/finance/connections/"+connection+"/sync",input(),UUID.randomUUID()).status());assertEquals(401,req(null,"GET","/finance/connections/"+connection+"/transactions",null,null).status());
  assertEquals(1,ok(req(a,"GET","/finance/connections/"+connection+"/sync-runs",null,null)).size());assertEquals(404,req(b,"GET","/finance/connections/"+connection+"/sync-runs",null,null).status());assertEquals(401,req(null,"GET","/finance/connections/"+connection+"/sync-runs",null,null).status());
 }
 @Test void malformedFailureAndEmptyResponse()throws Exception{
  assertEquals("FAILED",sync(JSON.createArrayNode().add(row("A1","50000","0","")).add("invalid")).path("status").asText());assertEquals(0,count("ledger_entries"));
  var f=response(row("A1","50000","0",""));f.status=500;assertEquals("FAILED",ok(req(a,"POST","/finance/connections/"+connection+"/sync",input(),UUID.randomUUID())).path("status").asText());assertEquals(0,count("financial_transactions"));sync(row("A1","50000","0",""));sync(JSON.createArrayNode());assertEquals(50000,spent());
 }
 @Test void connectionChangedDuringRead()throws Exception{
  var behavior=response(row("A1","50000","0",""));behavior.entered=new CountDownLatch(1);behavior.release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
  try{var pending=pool.submit(()->req(a,"POST","/finance/connections/"+connection+"/sync",input(),UUID.randomUUID()));assertTrue(behavior.entered.await(5,TimeUnit.SECONDS));db.update("UPDATE financial_connections SET state='UNKNOWN',version=version+1 WHERE user_id=? AND id=?",userA,connection);behavior.release.countDown();assertEquals("FAILED",ok(pending.get(10,TimeUnit.SECONDS)).path("status").asText());assertEquals(0,count("ledger_entries"));}finally{behavior.release.countDown();pool.shutdownNow();}
 }
 @Test void concurrentDuplicateDoesNotRepeatProviderCall()throws Exception{
  var behavior=response(row("A1","50000","0",""));behavior.entered=new CountDownLatch(1);behavior.release=new CountDownLatch(1);UUID key=UUID.randomUUID();var pool=Executors.newSingleThreadExecutor();
  try{var pending=pool.submit(()->req(a,"POST","/finance/connections/"+connection+"/sync",input(),key));assertTrue(behavior.entered.await(5,TimeUnit.SECONDS));assertEquals("RUNNING",ok(req(a,"POST","/finance/connections/"+connection+"/sync",input(),key)).path("status").asText());assertEquals(409,req(a,"POST","/finance/connections/"+connection+"/sync",input(),UUID.randomUUID()).status());assertEquals(2,provider.calls.size());behavior.release.countDown();assertEquals("IMPORTED",ok(pending.get(10,TimeUnit.SECONDS)).path("status").asText());assertEquals(1,count("ledger_entries"));}finally{behavior.release.countDown();pool.shutdownNow();}
 }
 @Test void ambiguousIdentityWithinOneResponseNeverImportsLastRowWins()throws Exception{
  assertEquals("FAILED",sync(JSON.createArrayNode().add(row("A1","50000","0","")).add(row("A1","60000","0",""))).path("status").asText());assertEquals(0,count("ledger_entries"));assertEquals(0,count("financial_transactions"));
 }
 @Test void pendingEvidenceInvalidatesRecommendationAndBlocksNewOne()throws Exception{
  ok(req(a,"POST","/demo/setup",null,null));var checkout=ok(req(a,"POST","/checkout-sessions",Map.of("orderValueWon",50000,"pointUsedWon",0,"prepaidMoneyUsedWon",0,"cardChargeWon",50000,"category","SHOPPING","paymentMethod","NAVER_PAY_CARD","installments",1,"splitApproval","NO","confirmedByUser",true),null));
  var body=Map.of("checkoutId",checkout.path("id").asText(),"snapshotVersion",checkout.path("snapshotVersion").asLong(),"mode","BENEFIT","maxOpportunityCostWon",0);
  var recommendation=ok(req(a,"POST","/recommendations",body,null));sync(row("A1","50000","0",""));sync(row("A1","50000","2",""));
  var old=ok(req(a,"GET","/recommendations/"+recommendation.path("id").asText(),null,null));assertEquals("INVALIDATED",old.path("validity").asText());
  assertEquals(409,req(a,"POST","/recommendations",body,null).status());var budget=ok(req(a,"GET","/budgets/"+DATE.toString().substring(0,7)+"/summary",null,null));assertEquals(1,budget.path("pendingFinancialTransactions").asLong());assertEquals(50000,budget.path("spentWon").asLong());
  var held=rows().get(0);assertEquals(409,req(a,"POST","/finance/transactions/"+held.path("id").asText()+"/resolve",Map.of("version",held.path("version").asLong(),"action","IGNORE"),null).status());
 }
 @Test void newHeldEvidenceAlsoChangesRevisionAndCanBeExplicitlyIgnored()throws Exception{
  long before=revision();sync(row("A1","50000","2",""));assertTrue(revision()>before);assertEquals(0,count("ledger_entries"));var held=rows().get(0);resolve(a,held,"IGNORE",null,null);sync(row("A1","50000","0",""));assertEquals(0,count("ledger_entries"));assertEquals("IGNORED",rows().get(0).path("status").asText());
 }
 @Test void confirmedSeparatePurchaseAndHanaPartialCancellation()throws Exception{
  connection=connect(a,"0313");UUID manual=UUID.randomUUID();db.update("INSERT INTO ledger_entries(id,user_id,client_id,amount_won,purchased_at,category,included,source,original_category,original_included) VALUES (?,?,?,?,?,'OTHER',true,'MANUAL','OTHER',true)",manual,userA,UUID.randomUUID(),50000,DATE);
  sync(row("H1","50000","0",""));var held=rows().get(0);resolve(a,held,"IMPORT_SEPARATELY",null,null);assertEquals(2,count("ledger_entries"));sync(row("H1","50000","2","20000"));assertEquals(80000,spent());assertEquals(1,count("cancellations"));
 }
 @Test void databaseRejectsCrossUserLedgerReferenceAndBadWindows()throws Exception{otherAccount();
  sync(row("A1","50000","0",""));UUID foreign=UUID.randomUUID();db.update("INSERT INTO ledger_entries(id,user_id,client_id,amount_won,purchased_at,category,included,source,original_category,original_included) VALUES (?,?,?,?,?,'OTHER',true,'MANUAL','OTHER',true)",foreign,userB,UUID.randomUUID(),50000,DATE);
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("UPDATE financial_transactions SET ledger_id=? WHERE user_id=?",foreign,userA));
  var bad=new HashMap<>(input());bad.put("from",DATE.minusMonths(2).toString());assertEquals(422,req(a,"POST","/finance/connections/"+connection+"/sync",bad,UUID.randomUUID()).status());assertEquals(422,req(a,"POST","/finance/connections/"+connection+"/sync",input(),null).status());
 }
 @Test void hanaGoSharedBenefitsAreExplicitAndCancellationNeedsReview()throws Exception{
  connection=connect(a,"0313");sync(row("GO1","20000","0",""));String card=productCard(a,"HANA_TRAVELGO_BANK",dev.cardadvisor.domain.ActualCardRules.GO),m=DATE.toString().substring(0,7);
  ok(req(a,"PUT","/finance/connections/"+connection+"/card-mappings",mappingBody(mappings(a).path("groups").get(0),card,0),null));
  ok(req(a,"PUT","/cards/"+card+"/months/"+m,Map.of("untrackedPerformanceWon",200000,"currentQualified",true,"remainingCapWon",5000,"version",0),null));
  var t=rows().get(0);assertEquals(5,benefitReview(t).path("areas").size());saveBenefit(t,"RULE","GO_COFFEE");benefitMonth(card,m,1,true);assertEquals(1000,benefit(card,m).path("estimatedUsedWon").asLong());assertEquals(4000,benefit(card,m).path("estimatedRemainingCapWon").asLong());
  sync(row("GO1","20000","2","5000"));assertTrue(benefit(card,m).path("estimatedUsedWon").isNull());saveBenefit(t,"RULE","GO_COFFEE");assertEquals(750,benefit(card,m).path("estimatedUsedWon").asLong());sync(row("GO1","20000","2","5000"));assertEquals(1,count("ledger_entries"));assertEquals(1,count("cancellations"));
 }
}
