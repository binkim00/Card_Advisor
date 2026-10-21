package dev.cardadvisor.account;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"codef.config-file=","codef.storage-key-file=","openai.config-file=","account.mail-config-file="})
@EnabledIfEnvironmentVariable(named="RUN_CHECKOUT_ANDROID_PG_TEST",matches="true")
class AndroidCheckoutRecommendationPostgresTest {
 @LocalServerPort int port;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
 final String device=System.getenv().getOrDefault("ANDROID_TEST_DEVICE","emulator-5554");
 String adb(String...args)throws Exception{
  var cmd=new ArrayList<String>(List.of(System.getenv("LOCALAPPDATA")+"/Android/Sdk/platform-tools/adb.exe","-s",device));cmd.addAll(List.of(args));var p=new ProcessBuilder(cmd).redirectErrorStream(true).start();
  var output=CompletableFuture.supplyAsync(()->{try{return new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);}catch(Exception e){return "";}});
  if(!p.waitFor(180,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("Checkout device test timed out; raw output withheld");}assertEquals(0,p.exitValue());return output.get(3,TimeUnit.SECONDS);
 }
 @Test void emulatorConfirmationToRecommendationToPostgres()throws Exception{
  String run=UUID.randomUUID().toString(),email="checkout-flow-"+run+"@example.test";var report=new LinkedHashMap<String,Object>();report.put("passed",false);report.put("device",device);report.put("syntheticCheckout",true);report.put("externalProviderCalls",0);
  try{
   adb("reverse","tcp:18080","tcp:"+port);
   String out=adb("shell","am","instrument","-w","-r","-e","class","dev.cardadvisor.android.CheckoutRecommendationFlowTest","-e","runId",run,"dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner");
   report.put("failureLocations",out.lines().filter(x->x.matches(".*CheckoutRecommendationFlowTest\\.kt:[0-9]+.*")).map(x->x.replaceAll(".*CheckoutRecommendationFlowTest\\.kt:([0-9]+).*","CheckoutRecommendationFlowTest.kt:$1")).distinct().toList());
   assertTrue(out.contains("OK (1 test)"),"Checkout confirmation flow failed; raw output withheld");
   UUID user=db.queryForObject("SELECT id FROM app_users WHERE email=?",UUID.class,email);
   var rows=db.queryForList("SELECT c.payload::text AS checkout,r.payload::text AS result FROM recommendations r JOIN checkouts c ON c.id=r.checkout_id AND c.user_id=r.user_id WHERE r.user_id=? ORDER BY (c.payload->>'pointUsedWon')::bigint",user);
   assertEquals(2,rows.size());for(int i=0;i<2;i++){
    var c=json.readTree((String)rows.get(i).get("checkout"));var result=json.readTree((String)rows.get(i).get("result"));
    assertEquals(62000,c.get("orderValueWon").asInt());assertEquals(2000+i*1000,c.get("pointUsedWon").asInt());assertEquals(10000,c.get("prepaidMoneyUsedWon").asInt());assertEquals(50000-i*1000,c.get("cardChargeWon").asInt());assertEquals(i==0?"DIRECT_CARD":"NAVER_PAY_CARD",c.get("paymentMethod").asText());assertEquals("NAVER_PLUS_STORE",c.get("purchaseFacts").get("merchant").asText());assertEquals(60000-i*1000,result.get("expenseWon").asInt());assertFalse(result.get("recommendedCardId").isNull());
   }
   assertEquals(0L,db.queryForObject("SELECT count(*) FROM ledger_entries WHERE user_id=?",Long.class,user));report.put("recommendations",2);report.put("ledgerRows",0);report.put("passed",true);
  }finally{
   db.update("DELETE FROM app_users WHERE email=?",email);report.put("syntheticAccountRemoved",db.queryForObject("SELECT count(*) FROM app_users WHERE email=?",Long.class,email)==0);adb("reverse","--remove","tcp:18080");
   Files.createDirectories(Path.of("../.run"));Files.writeString(Path.of("../.run/checkout-recommendation-flow.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
  }
 }
}
