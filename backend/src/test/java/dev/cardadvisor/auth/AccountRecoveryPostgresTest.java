package dev.cardadvisor.auth;
import dev.cardadvisor.api.ApiError;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;import java.net.http.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"security.rate-limit-scope=synthetic-account","codef.config-file=","codef.storage-key-file=","account.mail-config-file="})
@Import(AccountRecoveryPostgresTest.MailConfig.class)
@EnabledIfEnvironmentVariable(named="RUN_ACCOUNT_PG_TESTS",matches="true")
class AccountRecoveryPostgresTest {
 @TestConfiguration static class MailConfig {@Bean @Primary CaptureMail captureMail(){return new CaptureMail();}}
 static class CaptureMail implements AccountMailGateway {boolean enabled=true,fail=false;Map<String,String> messages=new HashMap<>();public boolean ready(){return enabled;}public void send(String to,String purpose,String token){if(fail)throw new ApiError(503,"ACCOUNT_MAIL_DELIVERY_FAILED");messages.put(to+":"+purpose,token);}}
 @LocalServerPort int port;@Autowired JdbcTemplate db;@Autowired CaptureMail mail;ObjectMapper json=new ObjectMapper();HttpClient http=HttpClient.newHttpClient();List<UUID> users=new ArrayList<>();String email,access,refresh;UUID user;
 record Reply(int status,JsonNode body){}
 Reply request(String bearer,String path,Object input)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/v1"+path)).header("Content-Type","application/json");if(bearer!=null)b.header("Authorization","Bearer "+bearer);if(input==null)b.GET();else b.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input)));var out=http.send(b.build(),HttpResponse.BodyHandlers.ofString());for(var raw:mail.messages.values())assertFalse(out.body().contains(raw),"Action code must not be returned by API");return new Reply(out.statusCode(),json.readTree(out.body()));}
 @BeforeEach void setup()throws Exception{mail.enabled=true;mail.fail=false;mail.messages.clear();email="account-recovery-"+UUID.randomUUID()+"@example.test";var a=request(null,"/auth/register",Map.of("email",email,"password","Synthetic-original-1234"));assertEquals(201,a.status);user=UUID.fromString(a.body.path("userId").asText());users.add(user);access=a.body.path("accessToken").asText();refresh=a.body.path("refreshToken").asText();}
 @AfterEach void cleanup(){for(var id:users)db.update("DELETE FROM app_users WHERE id=?",id);mail.messages.clear();}
 String code(String purpose){return mail.messages.get(email+":"+purpose);}
 @Test void verificationIsOwnedSingleUseAndSeparateFromLogin()throws Exception{
  assertFalse(request(access,"/me/email-verification",null).body.path("emailVerified").asBoolean());assertEquals(200,request(access,"/me/email-verification/request",Map.of()).status);String token=code("VERIFY_EMAIL");assertNotNull(token);assertFalse(db.queryForObject("SELECT token_hash FROM account_action_tokens WHERE user_id=?",String.class,user).equals(token));
  var other=request(null,"/auth/register",Map.of("email","account-recovery-"+UUID.randomUUID()+"@example.test","password","Synthetic-other-1234"));users.add(UUID.fromString(other.body.path("userId").asText()));assertEquals(401,request(other.body.path("accessToken").asText(),"/me/email-verification/confirm",Map.of("token",token)).status);
  assertEquals(401,request(null,"/auth/password-reset/confirm",Map.of("token",token,"newPassword","Synthetic-new-1234")).status);assertTrue(request(access,"/me/email-verification/confirm",Map.of("token",token)).body.path("emailVerified").asBoolean());assertEquals(401,request(access,"/me/email-verification/confirm",Map.of("token",token)).status);assertEquals(200,request(access,"/cards",null).status);
 }
 @Test void resetPreservesDataAndRevokesAccessRefreshSessions()throws Exception{
  assertEquals(200,request(access,"/demo/setup",Map.of()).status);long cards=db.queryForObject("SELECT count(*) FROM cards WHERE user_id=?",Long.class,user);assertEquals(200,request(null,"/auth/password-reset/request",Map.of("email",email)).status);String token=code("RESET_PASSWORD");assertNotNull(token);
  var result=request(null,"/auth/password-reset/confirm",Map.of("token",token,"newPassword","Synthetic-new-password-1234"));assertEquals(200,result.status);assertTrue(result.body.path("allSessionsRevoked").asBoolean());assertEquals(cards,db.queryForObject("SELECT count(*) FROM cards WHERE user_id=?",Long.class,user));assertEquals(401,request(access,"/cards",null).status);assertEquals(401,request(null,"/auth/refresh",Map.of("refreshToken",refresh)).status);assertEquals(401,request(null,"/auth/login",Map.of("email",email,"password","Synthetic-original-1234")).status);assertEquals(200,request(null,"/auth/login",Map.of("email",email,"password","Synthetic-new-password-1234")).status);assertEquals(401,request(null,"/auth/password-reset/confirm",Map.of("token",token,"newPassword","Synthetic-other-password-1234")).status);
 }
 @Test void expiredReplacedAndInvalidCodesAreRejected()throws Exception{
  request(null,"/auth/password-reset/request",Map.of("email",email));String old=code("RESET_PASSWORD");db.update("UPDATE account_action_tokens SET expires_at=now()-interval '1 second' WHERE user_id=?",user);assertEquals(401,request(null,"/auth/password-reset/confirm",Map.of("token",old,"newPassword","Synthetic-password-1234")).status);
  request(null,"/auth/password-reset/request",Map.of("email",email));String replaced=code("RESET_PASSWORD");request(null,"/auth/password-reset/request",Map.of("email",email));assertEquals(401,request(null,"/auth/password-reset/confirm",Map.of("token",replaced,"newPassword","Synthetic-password-1234")).status);assertEquals(422,request(null,"/auth/password-reset/confirm",Map.of("token",code("RESET_PASSWORD"),"newPassword","short")).status);assertEquals(200,request(null,"/auth/password-reset/confirm",Map.of("token",code("RESET_PASSWORD"),"newPassword","Synthetic-password-1234")).status);
 }
 @Test void mailboxExistenceAndDeliveryFailureAreNotDisclosed()throws Exception{
  mail.fail=true;var known=request(null,"/auth/password-reset/request",Map.of("email",email));var unknown=request(null,"/auth/password-reset/request",Map.of("email","unknown-"+UUID.randomUUID()+"@example.test"));assertEquals(200,known.status);assertEquals(known.body,unknown.body);assertEquals(0L,db.queryForObject("SELECT count(*) FROM account_action_tokens WHERE user_id=? AND consumed_at IS NULL",Long.class,user));mail.enabled=false;assertEquals(503,request(null,"/auth/password-reset/request",Map.of("email",email)).status);assertEquals(503,request(null,"/auth/password-reset/request",Map.of("email","absent@example.test")).status);assertEquals(200,request(access,"/cards",null).status);
 }
}
