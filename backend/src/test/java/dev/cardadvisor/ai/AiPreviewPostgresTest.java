package dev.cardadvisor.ai;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import java.net.*;import java.net.http.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.profiles.active=synthetic-preview","codef.config-file=","codef.storage-key-file=","openai.config-file=","security.rate-limit-scope=ai-preview-test"})
@EnabledIfEnvironmentVariable(named="RUN_FINANCE_PG_TESTS",matches="true")
@Import(AiPreviewPostgresTest.Provider.class)
class AiPreviewPostgresTest {
 static final ObjectMapper json=new ObjectMapper();static HttpServer server;static final AtomicInteger calls=new AtomicInteger();static volatile int status=200;static volatile boolean invalid=false;
 @LocalServerPort int port;String token;
 @TestConfiguration static class Provider {
  @Bean @Primary OpenAiGateway ai()throws Exception {
   server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   server.createContext("/responses",e->{try{
    calls.incrementAndGet();var request=json.readTree(e.getRequestBody());
    assertFalse(request.path("store").asBoolean());assertFalse(request.has("tools"));
    var facts=json.readTree(request.path("input").asText());for(var fact:facts){assertEquals(Set.of("id","values"),json.convertValue(fact,Map.class).keySet());}
    String id=invalid?"FORGED":request.at("/text/format/schema/properties/ids/items/enum/0").asText();
    byte[] reply=json.writeValueAsBytes(Map.of("status","completed","output",List.of(Map.of("type","message","content",List.of(Map.of("type","output_text","text",json.writeValueAsString(Map.of("ids",List.of(id)))))))));
    e.sendResponseHeaders(status,reply.length);e.getResponseBody().write(reply);
   }finally{e.close();}});server.start();
   return new OpenAiGateway(json,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/responses"));
  }
 }
 @DynamicPropertySource static void key(DynamicPropertyRegistry values)throws Exception{
  var path=Files.createTempFile("ai-preview-key-",".properties");path.toFile().deleteOnExit();byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);Files.writeString(path,"activeKeyId=test\nkeys.test="+Base64.getEncoder().encodeToString(key));values.add("card.preview.key-file",()->path.toString());
 }
 @AfterAll static void stop(){if(server!=null)server.stop(0);}
 JsonNode call(String method,String path,Object body,int expected)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/v1"+path)).header("Content-Type","application/json");if(token!=null)b.header("Authorization","Bearer "+token);
  b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
  var response=HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());assertEquals(expected,response.statusCode());return json.readTree(response.body());
 }
 @Test void appRoutesConsentFailuresAndFinanceIsolation()throws Exception{
  var session=call("POST","/auth/register",Map.of("email","ai-preview-"+UUID.randomUUID()+"@example.test","password","Synthetic-ai-only-password"),201);token=session.path("accessToken").asText();
  try {
   call("POST","/demo/setup",null,200);String month=java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul")).toString();
   assertEquals("CONSENT_REQUIRED",call("GET","/insights?month="+month,null,200).at("/assistance/status").asText());assertEquals(0,calls.get());
   call("PUT","/me/consents/AI",Map.of("accepted",true,"policyVersion","2026-10-19-v1","expectedVersion",0),200);
   var checkout=call("POST","/checkout-sessions",Map.of("orderValueWon",50000,"pointUsedWon",0,"prepaidMoneyUsedWon",0,"cardChargeWon",50000,"category","SHOPPING","paymentMethod","NAVER_PAY_CARD","installments",1,"splitApproval","NO","confirmedByUser",true),200);
   var recommendation=call("POST","/recommendations",Map.of("checkoutId",checkout.path("id").asText(),"snapshotVersion",1,"mode","BENEFIT","maxOpportunityCostWon",0),200);
   String id=recommendation.path("id").asText();
   assertEquals("OPENAI_VALIDATED",call("POST","/recommendations/"+id+"/explanation",null,200).path("source").asText());
   assertEquals("OPENAI_VALIDATED",call("GET","/insights?month="+month,null,200).at("/assistance/source").asText());
   invalid=true;assertEquals("DETERMINISTIC_TEMPLATE",call("GET","/insights?month="+month,null,200).at("/assistance/source").asText());invalid=false;
   status=429;assertEquals("AI_UNAVAILABLE_OR_REJECTED",call("GET","/insights?month="+month,null,200).at("/assistance/status").asText());status=200;
   call("PUT","/me/consents/AI",Map.of("accepted",false,"policyVersion","2026-10-19-v1","expectedVersion",1),200);int before=calls.get();
   assertEquals("CONSENT_REQUIRED",call("GET","/insights?month="+month,null,200).at("/assistance/status").asText());assertEquals(before,calls.get());
   assertFalse(call("GET","/finance/codef/status",null,200).path("realInstitutionAccessEnabled").asBoolean());
  }finally{
   var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/v1/me")).header("Authorization","Bearer "+token).header("X-Confirm-Delete","DELETE_ACCOUNT").DELETE().build();HttpClient.newHttpClient().send(b,HttpResponse.BodyHandlers.discarding());
  }
 }
}
