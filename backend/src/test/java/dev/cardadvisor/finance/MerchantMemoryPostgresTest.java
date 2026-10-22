package dev.cardadvisor.finance;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;import static dev.cardadvisor.finance.CodefApprovalNormalizerTest.*;
class MerchantMemoryPostgresTest extends FinancialSyncPostgresTest {
 @Test void rememberedCategoryIsPrivateVersionedSuggestionAndCanBeForgotten()throws Exception{
  sync(JSON.createArrayNode().add(row("MEMORYA","50000","0","").put("resMemberStoreName","합성 커피" )).add(row("MEMORYB","30000","0","").put("resMemberStoreName","합성 커피")));
  var all=ok(req(a,"GET","/transactions?month="+DATE.toString().substring(0,7),null,null));String id=all.get(0).path("id").asText(),second=all.get(1).path("id").asText();String path="/transactions/"+id+"/classification-rule";long before=revision();
  assertTrue(ok(req(a,"GET",path,null,null)).path("available").asBoolean());assertEquals(404,req(b,"GET",path,null,null).status());
  var body=new HashMap<String,Object>();body.put("category","EDUCATION");body.put("expectedVersion",0);body.put("confirmed",true);
  assertEquals(1,ok(req(a,"PUT",path,body,null)).path("version").asInt());assertEquals(before,revision());assertEquals(409,req(a,"PUT",path,body,null).status());
  assertEquals("EDUCATION",ok(req(a,"POST","/transactions/"+second+"/classification-suggestion",null,null)).path("category").asText());
  assertEquals(all,ok(req(a,"GET","/transactions?month="+DATE.toString().substring(0,7),null,null)));
  ok(req(a,"PUT","/transactions/"+second+"/classification",Map.of("category","HEALTH","included",true,"version",1),null));assertEquals("HEALTH",ok(req(a,"POST","/transactions/"+second+"/classification-suggestion",null,null)).path("category").asText());
  body.put("category",null);body.put("expectedVersion",1);assertEquals(2,ok(req(a,"PUT",path,body,null)).path("version").asInt());assertEquals("FOOD",ok(req(a,"POST","/transactions/"+id+"/classification-suggestion",null,null)).path("category").asText());
  assertEquals(1,db.queryForObject("SELECT count(*) FROM merchant_category_rules WHERE user_id=? AND category IS NULL",Integer.class,userA));assertEquals(2,count("ledger_entries"));assertEquals(80000,spent());
 }
}
