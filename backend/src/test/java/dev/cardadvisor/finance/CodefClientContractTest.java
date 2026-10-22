package dev.cardadvisor.finance;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Cipher;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.cardadvisor.finance.CodefFailure.Kind.*;

/** Every response/credential is synthetic. Only loopback HTTP is used. */
class CodefClientContractTest {
 static KeyPair keys;
 static final String MARKER="SYNTHETIC_PRIVATE_CANARY";
 final ObjectMapper json=new ObjectMapper();
 HttpServer server;ExecutorService executor;
 URI base;CodefClient client;
 final Queue<Reply> replies=new ConcurrentLinkedQueue<>();
 final List<Request> requests=new CopyOnWriteArrayList<>();
 int tokens;Reply oauthReply;
 record Reply(int status,String body,int delay){}
 record Request(String path,String auth,String contentType,String body){}
 static class TestClock extends Clock {
  Instant now=Instant.parse("2026-10-19T00:00:00Z");
  public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
 }
 TestClock clock;
 @BeforeAll static void key()throws Exception{var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);keys=generator.generateKeyPair();}
 Properties properties(){var p=new Properties();p.setProperty("enabled","true");p.setProperty("usageApproved","true");p.setProperty("environment","SANDBOX");p.setProperty("clientId","synthetic-client");p.setProperty("clientSecret",MARKER);p.setProperty("publicKey",Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));return p;}
 @BeforeEach void setup()throws Exception{
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);executor=Executors.newCachedThreadPool();server.setExecutor(executor);
  server.createContext("/",exchange->{
   String path=exchange.getRequestURI().getPath();
   requests.add(new Request(path,exchange.getRequestHeaders().getFirst("Authorization"),exchange.getRequestHeaders().getFirst("Content-Type"),new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));
   Reply reply;
   if(path.equals("/oauth/token"))reply=oauthReply!=null?oauthReply:new Reply(200,"{\"access_token\":\"synthetic-token-"+(++tokens)+"\",\"token_type\":\"bearer\",\"expires_in\":120}",0);
   else{reply=replies.poll();if(reply==null)reply=new Reply(500,"",0);}
   try{if(reply.delay>0)Thread.sleep(reply.delay);byte[] bytes=reply.body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(reply.status,bytes.length);exchange.getResponseBody().write(bytes);}
   catch(Exception ignored){}finally{exchange.close();}
  });server.start();base=URI.create("http://127.0.0.1:"+server.getAddress().getPort());clock=new TestClock();
  client=new CodefClient(new CodefSettings(properties()),base,base.resolve("/oauth/token"),clock,Duration.ofSeconds(2));
 }
 @AfterEach void stop(){server.stop(0);executor.shutdownNow();}
 void reply(int status,String body){replies.add(new Reply(status,body,0));}
 String envelope(String code,String data){return "{\"result\":{\"code\":\""+code+"\",\"message\":\""+MARKER+"\"},\"data\":"+data+"}";}
 String success(){return envelope("CF-00000","{\"connectedId\":\"synthetic-cid\",\"successList\":[{\"countryCode\":\"KR\",\"businessType\":\"CD\",\"clientType\":\"P\",\"organization\":\"9999\",\"code\":\"CF-00000\"}],\"errorList\":[]}");}
 CodefClient.CardLogin login(){return new CodefClient.CardLogin("9999","synthetic-user",MARKER.toCharArray());}
 JsonNode decoded(int index)throws Exception{return json.readTree(URLDecoder.decode(requests.get(index).body,StandardCharsets.UTF_8));}
 CodefFailure fail(CodefFailure.Kind kind,Runnable action){var failure=assertThrows(CodefFailure.class,action::run);assertEquals(kind,failure.kind);assertFalse(failure.toString().contains(MARKER));assertNull(failure.getCause());return failure;}

 @Test void providerCodeSurvivesWithoutProviderMessage(){reply(200,envelope("CF-99999","{}"));var e=fail(REJECTED,()->client.accounts("synthetic-cid"));assertEquals("CF-99999",e.providerCode);assertFalse(e.toString().contains(MARKER));}
 @ParameterizedTest @ValueSource(strings={"CF-12345-secret","SYNTHETIC_PRIVATE_CANARY","CF-1234","cf-12345"}) void malformedProviderCodeIsDropped(String code){reply(200,envelope(code,"{}"));assertNull(fail(REJECTED,()->client.accounts("synthetic-cid")).providerCode);}
 @Test void perAccountFailureKeepsOnlyCode(){reply(200,envelope("CF-00000","{\"successList\":[],\"errorList\":[{\"code\":\"CF-99999\",\"message\":\""+MARKER+"\"}]}"));try(var login=login()){var result=client.create(login);assertEquals("CF-99999",result.providerCode);assertFalse(result.toString().contains(MARKER));}}
 @Test void disabledNeverContactsProvider(){var disabled=new CodefClient(new CodefSettings(new Properties()),base,base.resolve("/oauth/token"),clock,Duration.ofSeconds(1));fail(DISABLED,()->disabled.accounts("synthetic-cid"));assertTrue(requests.isEmpty());assertEquals(false,disabled.readiness().get("enabled"));}
 @Test void configurationRequiresExplicitApprovalAndCredentials(){var p=properties();p.remove("usageApproved");fail(CONFIGURATION,()->new CodefSettings(p));assertTrue(requests.isEmpty());}
 @Test void oauthFormBearerEncodingAndCache()throws Exception{
  reply(200,URLEncoder.encode(envelope("CF-00000","{\"accountList\":[]}"),StandardCharsets.UTF_8));reply(200,envelope("CF-00000","{}"));
  client.accounts("synthetic-cid");client.accounts("synthetic-cid");assertEquals(1,tokens);
  assertEquals("grant_type=client_credentials&scope=read",requests.get(0).body);assertEquals("application/x-www-form-urlencoded",requests.get(0).contentType);
  assertEquals("Basic "+Base64.getEncoder().encodeToString(("synthetic-client:"+MARKER).getBytes(StandardCharsets.UTF_8)),requests.get(0).auth);
  assertEquals("Bearer synthetic-token-1",requests.get(1).auth);assertTrue(requests.get(1).body.startsWith("%7B"));assertEquals("synthetic-cid",decoded(1).path("connectedId").asText());
 }
 @Test void expiryUsesProviderLifetime(){reply(200,envelope("CF-00000","{}"));client.accounts("synthetic-cid");clock.now=clock.now.plusSeconds(91);reply(200,envelope("CF-00000","{}"));client.accounts("synthetic-cid");assertEquals(2,tokens);}
 @ParameterizedTest @ValueSource(strings={"create","add","update","delete"}) void accountRoutesEncryptPasswordsAndCheckResults(String operation)throws Exception{
  reply(200,success());CodefClient.AccountResult result;
  try(var login=login()){
   result=switch(operation){case "create"->client.create(login);case "add"->client.add("synthetic-cid",login);case "update"->client.update("synthetic-cid",login);default->client.delete("synthetic-cid","9999");};
   assertFalse(login.toString().contains(MARKER));
  }
  assertEquals(CodefClient.AccountResult.Status.SUCCEEDED,result.status);assertEquals("synthetic-cid",result.connectedId());assertFalse(result.toString().contains("synthetic-cid"));assertEquals("/v1/account/"+operation,requests.get(1).path);
  JsonNode request=decoded(1),account=request.path("accountList").get(0);assertEquals("CD",account.path("businessType").asText());assertEquals("1",account.path("loginType").asText());assertEquals(!operation.equals("create"),request.has("connectedId"));
  if(operation.equals("delete")){assertFalse(account.has("id"));assertFalse(account.has("password"));}
  else{assertFalse(requests.get(1).body.contains(MARKER));var cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");cipher.init(Cipher.DECRYPT_MODE,keys.getPrivate());assertEquals(MARKER,new String(cipher.doFinal(Base64.getDecoder().decode(account.path("password").asText())),StandardCharsets.UTF_8));}
 }
 @Test void partialAccountResultIsNotFullSuccess(){reply(200,success().replace("\"errorList\":[]","\"errorList\":[{\"code\":\"CF-99999\"}]"));try(var login=login()){assertEquals(CodefClient.AccountResult.Status.PARTIAL,client.create(login).status);}}
 @Test void failureOnlyIsNotConnected(){reply(200,envelope("CF-00000","{\"successList\":[],\"errorList\":[{\"code\":\"CF-99999\"}]}"));try(var login=login()){var result=client.create(login);assertEquals(CodefClient.AccountResult.Status.FAILED,result.status);assertNull(result.connectedId());}}
 @Test void mismatchedInstitutionIsUnknownOutcome(){reply(200,success().replace("9999","9998"));try(var login=login()){fail(UNKNOWN_OUTCOME,()->client.create(login));}assertEquals(2,requests.size());}
 @Test void ambiguousMutationDoesNotRetry(){reply(500,MARKER);try(var login=login()){fail(UNKNOWN_OUTCOME,()->client.create(login));}assertEquals(2,requests.size());}
 @Test void timeoutMutationDoesNotRetry(){client=new CodefClient(new CodefSettings(properties()),base,base.resolve("/oauth/token"),clock,Duration.ofMillis(150));replies.add(new Reply(200,success(),500));try(var login=login()){fail(UNKNOWN_OUTCOME,()->client.create(login));}assertEquals(2,requests.size());}
 @Test void invalidTokenReadRetriesOnce(){reply(401,MARKER);reply(200,envelope("CF-00000","{}"));client.accounts("synthetic-cid");assertEquals(2,tokens);assertEquals(4,requests.size());}
 @Test void providerInvalidTokenReadRetriesOnce(){reply(200,envelope("CF-00401","{}"));reply(200,envelope("CF-00000","{}"));client.accounts("synthetic-cid");assertEquals(2,tokens);}
 @Test void repeatedInvalidTokenStops(){reply(401,MARKER);reply(401,MARKER);fail(AUTHENTICATION,()->client.accounts("synthetic-cid"));assertEquals(2,tokens);}
 @Test void invalidTokenMutationIsNeverReplayed(){reply(401,MARKER);try(var login=login()){fail(AUTHENTICATION,()->client.create(login));}assertEquals(1,tokens);assertEquals(2,requests.size());}
 @Test void additionalAuthStopsWithoutAutomaticContinuation(){reply(200,envelope("CF-03002","{\"continue2Way\":true,\"twoWayInfo\":{}}"));fail(AUTH_REQUIRED,()->client.accounts("synthetic-cid"));assertEquals(2,requests.size());}
 @ParameterizedTest @ValueSource(ints={403,429,500,302}) void httpErrorsNeverBecomeSuccess(int status){reply(status,MARKER);var expected=status==403?ACCESS_DENIED:status==429?RATE_LIMITED:TRANSPORT;fail(expected,()->client.accounts("synthetic-cid"));assertEquals(2,requests.size());}
 @ParameterizedTest @ValueSource(strings={"{}","not-json","{\"result\":{\"code\":\"CF-00000\"}}","{\"result\":{\"code\":\"CF-00000\"},\"data\":null}"}) void missingDataIsNotEmptySuccess(String body){reply(200,body);fail(INVALID_RESPONSE,()->client.accounts("synthetic-cid"));}
 @Test void rawJsonPlusPercentArePreserved(){reply(200,envelope("CF-00000","{\"syntheticValue\":\"A+B%25\"}"));var result=client.accounts("synthetic-cid");assertEquals("A+B%25",result.data().path("syntheticValue").asText());assertFalse(result.toString().contains("A+B"));}
 @Test void oversizedResponseFailsSafely(){reply(200,"X".repeat(2*1024*1024+1));fail(TRANSPORT,()->client.accounts("synthetic-cid"));}
 @Test void cardAndApprovalPathsHaveNoInventedDefaults()throws Exception{
  reply(200,envelope("CF-00000","[]"));client.cards("synthetic-cid","9999","");assertEquals("/v1/kr/card/p/account/card-list",requests.get(1).path);
  reply(200,envelope("CF-00000","[]"));client.approvals("synthetic-cid","9999","",LocalDate.of(2026,10,1),LocalDate.of(2026,10,19),Map.of());
  assertEquals("/v1/kr/card/p/account/approval-list",requests.get(2).path);assertEquals("20261001",decoded(2).path("startDate").asText());assertFalse(decoded(2).has("inquiryType"));assertFalse(decoded(2).has("orderBy"));
 }
 @Test void unknownOptionsAndReversedDatesFailBeforeNetwork(){fail(CONFIGURATION,()->client.approvals("synthetic-cid","9999","",LocalDate.now(),LocalDate.now(),Map.of("inventedField","1")));fail(CONFIGURATION,()->client.approvals("synthetic-cid","9999","",LocalDate.now(),LocalDate.now().minusDays(1),Map.of()));assertTrue(requests.isEmpty());}
 @Test void statusAndObjectStringsDoNotExposeSecrets(){assertFalse(client.readiness().toString().contains(MARKER));assertFalse(client.toString().contains(MARKER));assertFalse(new CodefSettings(properties()).toString().contains(MARKER));assertEquals(false,client.readiness().get("realConnectionVerified"));}
 @Test void explicitOauthCheckDoesNotInvokeFinancialEndpoints(){var check=client.verifyAuthentication();assertEquals(true,check.get("oauthVerified"));assertEquals(false,check.get("financialInstitutionAuthenticated"));assertEquals(1,tokens);assertEquals(1,requests.size());client.verifyAuthentication();assertEquals(1,tokens);}
 @ParameterizedTest @ValueSource(ints={400,401,403,429,500}) void oauthFailureStatusesAreSeparatedWithoutReturningBodies(int status){oauthReply=new Reply(status,MARKER,0);var error=assertThrows(CodefFailure.class,()->client.verifyAuthentication());assertEquals(status,error.httpStatus);assertEquals(status==400||status==401?AUTHENTICATION:status==403?ACCESS_DENIED:status==429?RATE_LIMITED:TRANSPORT,error.kind);assertFalse(error.getMessage().contains(MARKER));assertEquals(1,requests.size());}
 @Test void registrationCheckRequiresMatchingCidAndExactAccountFields(){
  reply(200,envelope("CF-00000","{\"connectedId\":\"synthetic-cid\",\"accountList\":[{\"countryCode\":\"KR\",\"businessType\":\"CD\",\"clientType\":\"P\",\"organizationCode\":\"0301\",\"loginType\":\"1\"}]}"));assertTrue(client.registered("synthetic-cid","0301"));
  reply(200,envelope("CF-00000","{\"connectedId\":\"synthetic-cid\",\"accountList\":[]}"));assertFalse(client.registered("synthetic-cid","0301"));
  reply(200,envelope("CF-00000","{\"connectedId\":\"other\",\"accountList\":[]}"));fail(INVALID_RESPONSE,()->client.registered("synthetic-cid","0301"));
 }
}
