package dev.cardadvisor.finance;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.*;import java.net.http.*;import java.nio.file.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.profiles.active=synthetic-preview","codef.config-file=","codef.storage-key-file=","openai.config-file=","security.rate-limit-scope=private-preview-test"})
@EnabledIfEnvironmentVariable(named="RUN_FINANCE_PG_TESTS",matches="true")
class SyntheticPreviewPostgresTest {
 @LocalServerPort int port;@Autowired JdbcTemplate db;@Autowired CodefClient client;
 final ObjectMapper json=new ObjectMapper();final HttpClient http=HttpClient.newHttpClient();final List<UUID> users=new ArrayList<>();String token;
 @DynamicPropertySource static void key(DynamicPropertyRegistry values)throws Exception{
  var path=Files.createTempFile("synthetic-preview-test-",".properties");path.toFile().deleteOnExit();
  byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);
  Files.writeString(path,"activeKeyId=preview-test\nkeys.preview-test="+Base64.getEncoder().encodeToString(key));
  values.add("card.preview.key-file",()->path.toString());
 }
 JsonNode call(String method,String path,Object body,String access,int expected)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/v1"+path)).header("Content-Type","application/json");if(access!=null)b.header("Authorization","Bearer "+access);
  b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
  var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());assertEquals(expected,r.statusCode());return json.readTree(r.body());
 }
 String signup()throws Exception{var s=call("POST","/auth/register",Map.of("email","preview-"+UUID.randomUUID()+"@example.test","password","Synthetic-preview-user-only"),null,201);users.add(UUID.fromString(s.path("userId").asText()));return s.path("accessToken").asText();}
 @BeforeEach void before()throws Exception{db.update("DELETE FROM request_limits WHERE bucket_hash=?",dev.cardadvisor.auth.Passwords.sha("auth-ip:private-preview-test:127.0.0.1:"+(java.time.Instant.now().getEpochSecond()/60)));token=signup();}
 @AfterEach void after(){for(UUID u:users)db.update("DELETE FROM app_users WHERE id=?",u);}
 @Test void previewLookupRegisterAndOwnerIsolationWithoutRealProvider()throws Exception{
  var status=call("GET","/finance/codef/status",null,token,200);assertTrue(status.path("privatePreviewEnabled").asBoolean());assertTrue(status.path("syntheticProvider").asBoolean());assertFalse(status.path("realInstitutionAccessEnabled").asBoolean());
  var first=call("POST","/demo/finance-preview",Map.of(),token,200);var c=first.path("connection");String base="/finance/connections/"+c.path("id").asText()+"/held-cards";
  assertEquals(c.path("id"),call("POST","/demo/finance-preview",Map.of(),token,200).path("connection").path("id"));
  var held=call("POST",base+"/refresh",Map.of("expectedConnectionVersion",c.path("version").asLong(),"confirmed",true),token,200);assertEquals(2,held.path("cards").size());assertEquals(0,held.path("products").size());
  var card=held.path("cards").get(0);var input=Map.of("expectedVersion",card.path("version").asLong(),"expectedConnectionVersion",c.path("version").asLong(),"confirmed",true);
  call("POST",base+"/"+card.path("id").asText()+"/register",input,token,200);call("POST",base+"/"+card.path("id").asText()+"/register",input,token,200);
  var mine=call("GET","/cards",null,token,200);assertEquals(1,mine.size());assertTrue(mine.get(0).path("name").asText().startsWith("[체험]"));assertTrue(mine.get(0).path("rateBp").isNull());
  String other=signup();assertEquals(0,call("GET","/cards",null,other,200).size());call("GET",base,null,other,404);
 }
 @Test void realProviderConfigurationIsRejected(){assertThrows(IllegalStateException.class,()->SyntheticPreviewConfiguration.requireBlank("not-empty"));assertTrue(Boolean.TRUE.equals(client.readiness().get("syntheticProvider")));}
}
