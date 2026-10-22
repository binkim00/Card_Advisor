package dev.cardadvisor.finance;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import dev.cardadvisor.api.ApiError;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP API + Flyway + PostgreSQL. The ONLY provider is an in-process loopback server. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"security.rate-limit-scope=synthetic-finance-connections","codef.config-file=","codef.storage-key-file="})
@Import(FinancialConnectionsPostgresTest.LocalProviderConfiguration.class)
@EnabledIfEnvironmentVariable(named="RUN_FINANCE_PG_TESTS",matches="true")
class FinancialConnectionsPostgresTest {
 @LocalServerPort int port;
 @Autowired JdbcTemplate db;@Autowired Stub provider;@Autowired ConnectedIdCipher cipher;
 final ObjectMapper json=new ObjectMapper();final HttpClient http=HttpClient.newHttpClient();
 final List<UUID> users=new ArrayList<>();String a,b;UUID uidA,uidB;
 static final String LOGIN="synthetic-finance-user",PASSWORD="SYNTHETIC_FINANCIAL_PASSWORD";
 @TestConfiguration static class LocalProviderConfiguration {
  @Bean(destroyMethod="close") Stub stub()throws Exception{return new Stub();}
  @Bean @Primary CodefClient syntheticCodef(Stub stub){return stub.client;}
  @Bean @Primary ConnectedIdCipher syntheticCipher(){return new ConnectedIdCipher(ConnectedIdCipherTest.properties());}
 }
 static class Behavior {
  int status=200;String code="CF-00000",cid;boolean partial,failed;CountDownLatch entered,release;JsonNode approvalData;
 }
 static class Stub implements AutoCloseable {
  final HttpServer server;final ExecutorService executor=Executors.newCachedThreadPool();final CodefClient client;
  final Queue<Behavior> behaviors=new ConcurrentLinkedQueue<>();final List<String> calls=new CopyOnWriteArrayList<>();
  Stub()throws Exception{
   var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var keys=generator.generateKeyPair();
   server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(executor);
   server.createContext("/",exchange->{
    try{
     String path=exchange.getRequestURI().getPath();String body;
     if(path.equals("/oauth/token"))body="{\"access_token\":\"synthetic-token\",\"token_type\":\"bearer\",\"expires_in\":600}";
     else{
      calls.add(path);var mapper=new ObjectMapper();JsonNode request=mapper.readTree(URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),StandardCharsets.UTF_8));
      Behavior behavior=behaviors.poll();if(behavior==null)behavior=new Behavior();
      if(behavior.entered!=null){behavior.entered.countDown();behavior.release.await(8,TimeUnit.SECONDS);}
      if(behavior.status!=200){byte[] bytes="synthetic fault".getBytes();exchange.sendResponseHeaders(behavior.status,bytes.length);exchange.getResponseBody().write(bytes);return;}
      if(path.equals("/v1/account/list")){
       var accounts=behavior.failed?List.of():List.of(Map.of("countryCode","KR","businessType","CD","clientType","P","organizationCode","0301","loginType","1"));
       body=mapper.writeValueAsString(Map.of("result",Map.of("code",behavior.code),"data",Map.of("connectedId",request.path("connectedId").asText(),"accountList",accounts)));
      }else if(path.equals("/v1/kr/card/p/account/approval-list")){
       if(!request.path("inquiryType").asText().equals("1")||!request.path("orderBy").asText().equals("1")||!request.path("memberStoreInfoType").asText().equals("0"))throw new IllegalArgumentException("Unexpected synthetic approval query");
       body=mapper.writeValueAsString(Map.of("result",Map.of("code",behavior.code),"data",behavior.approvalData==null?mapper.createArrayNode():behavior.approvalData));
      }else{
      String cid=behavior.cid!=null?behavior.cid:request.has("connectedId")?request.get("connectedId").asText():"synthetic-cid-"+UUID.randomUUID();
      var item=Map.of("countryCode","KR","businessType","CD","clientType","P","organization",request.path("accountList").get(0).path("organization").asText(),"code","CF-00000");
      var data=new LinkedHashMap<String,Object>();data.put("successList",behavior.failed?List.of():List.of(item));data.put("errorList",behavior.failed||behavior.partial?List.of(Map.of("code","CF-99999")):List.of());if(!behavior.failed)data.put("connectedId",cid);
      body=mapper.writeValueAsString(Map.of("result",Map.of("code",behavior.code,"message","synthetic"),"data",data));
      }
     }
     byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
    }catch(Exception ignored){}finally{exchange.close();}
   });server.start();URI base=URI.create("http://127.0.0.1:"+server.getAddress().getPort());var p=new Properties();p.setProperty("enabled","true");p.setProperty("usageApproved","true");p.setProperty("clientId","synthetic-project");p.setProperty("clientSecret","synthetic-secret");p.setProperty("publicKey",Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
   client=new CodefClient(new CodefSettings(p),base,base.resolve("/oauth/token"),Clock.systemUTC(),Duration.ofSeconds(2));
  }
  public void close(){server.stop(0);executor.shutdownNow();}
 }
 record Reply(int status,JsonNode body){}
 Reply request(String token,String method,String path,Object body,UUID key)throws Exception{
  var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/v1"+path)).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json");
  if(token!=null)builder.header("Authorization","Bearer "+token);if(key!=null)builder.header("Idempotency-Key",key.toString());if(path.equals("/me"))builder.header("X-Confirm-Delete","DELETE_ACCOUNT");
  builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
  var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
  assertFalse(response.body().contains(PASSWORD));assertFalse(response.body().contains(LOGIN));assertFalse(response.body().contains("synthetic-cid"));
  return new Reply(response.statusCode(),json.readTree(response.body()));
 }
 JsonNode expect(int status,Reply reply){assertEquals(status,reply.status,"Unexpected API status");return reply.body;}
 String register()throws Exception{var response=expect(201,request(null,"POST","/auth/register",Map.of("email","finance-"+UUID.randomUUID()+"@example.test","password",UUID.randomUUID().toString()),null));users.add(UUID.fromString(response.path("userId").asText()));return response.path("accessToken").asText();}
 @BeforeEach void setup()throws Exception{db.update("DELETE FROM request_limits WHERE bucket_hash=?",dev.cardadvisor.auth.Passwords.sha("auth-ip:synthetic-finance-connections:127.0.0.1:"+(java.time.Instant.now().getEpochSecond()/60)));provider.calls.clear();provider.behaviors.clear();a=register();uidA=users.get(0);b=register();uidB=users.get(1);}
 @AfterEach void clean(){for(UUID user:users){db.update("DELETE FROM app_users WHERE id=?",user);db.update("DELETE FROM deleted_user_tombstones WHERE user_id=?",user);}for(UUID user:users)assertEquals(0L,db.queryForObject("SELECT count(*) FROM app_users WHERE id=?",Long.class,user));}
 Map<String,Object> connect(String org){return Map.of("organization",org,"loginId",LOGIN,"password",PASSWORD,"consent",true,"consentVersion","FINANCE_LINK_V1");}
 Map<String,Object> reauth(long version){return Map.of("expectedVersion",version,"loginId",LOGIN,"password",PASSWORD,"consent",true,"consentVersion","FINANCE_LINK_V1");}
 JsonNode create(String token,String org)throws Exception{return expect(200,request(token,"POST","/finance/connections",connect(org),UUID.randomUUID()));}
 UUID id(JsonNode response){return UUID.fromString(response.path("connection").path("id").asText());}
 long version(JsonNode response){return response.path("connection").path("version").asLong();}
 Map<String,Object> stored(UUID user,UUID id){return db.queryForMap("SELECT * FROM financial_connections WHERE user_id=? AND id=?",user,id);}
 void state(String expected,JsonNode response){assertEquals(expected,response.path("connection").path("state").asText());}
 void outcome(String expected,JsonNode response){assertEquals(expected,response.path("operation").path("status").asText());}
 Behavior fault(){var behavior=new Behavior();behavior.status=500;provider.behaviors.add(behavior);return behavior;}

 @Test void providerDiagnosticIsPersistedIsolatedAndClearedOnRetry()throws Exception{
  var rejected=new Behavior();rejected.code="CF-99999";provider.behaviors.add(rejected);var first=create(a,"9999");state("FAILED",first);UUID id=id(first);assertEquals("CF-99999",first.at("/connection/provider_code").asText());assertEquals("CF-99999",first.at("/operation/provider_code").asText());assertEquals("CF-99999",stored(uidA,id).get("provider_code"));expect(404,request(b,"GET","/finance/connections/"+id,null,null));
  var read=expect(200,request(a,"GET","/finance/connections/"+id,null,null));assertEquals("CF-99999",read.path("provider_code").asText());var retry=expect(200,request(a,"POST","/finance/connections/"+id+"/retry",reauth(version(first)),UUID.randomUUID()));state("ACTIVE",retry);assertTrue(retry.at("/connection/provider_code").isNull());assertEquals("CF-99999",db.queryForObject("SELECT provider_code FROM financial_connection_operations WHERE id=?",String.class,UUID.fromString(first.at("/operation/id").asText())));
 }
 @Test void authenticationConsentAndClientConnectedIdAreRejected()throws Exception{
  expect(401,request(null,"GET","/finance/connections",null,null));var invalid=new HashMap<>(connect("9999"));invalid.put("consent",false);expect(422,request(a,"POST","/finance/connections",invalid,UUID.randomUUID()));
  expect(422,request(a,"POST","/finance/connections",connect("9999"),null));invalid=new HashMap<>(connect("9999"));invalid.put("connectedId","attacker-selected");expect(422,request(a,"POST","/finance/connections",invalid,UUID.randomUUID()));assertTrue(provider.calls.isEmpty());
  var status=expect(200,request(a,"GET","/finance/codef/status",null,null));assertTrue(status.path("userConnectionApiReady").asBoolean());assertFalse(status.path("realConnectionVerified").asBoolean());
 }
 @Test void lifecycleOwnershipEncryptionAndAccountDeletion()throws Exception{
  var first=create(a,"9999");state("ACTIVE",first);outcome("SUCCEEDED",first);UUID id=id(first);var row=stored(uidA,id);
  assertEquals(12,((byte[])row.get("cid_nonce")).length);assertFalse(new String((byte[])row.get("cid_ciphertext"),StandardCharsets.UTF_8).contains("synthetic-cid"));
  assertTrue(cipher.decrypt(new ConnectedIdCipher.Sealed((String)row.get("key_id"),(byte[])row.get("cid_nonce"),(byte[])row.get("cid_ciphertext")),uidA,id,(String)row.get("environment"),(String)row.get("provider_scope"),"9999").startsWith("synthetic-cid"));
  expect(404,request(b,"GET","/finance/connections/"+id,null,null));expect(404,request(b,"GET","/finance/operations/"+first.path("operation").path("id").asText(),null,null));
  assertEquals(0,expect(200,request(b,"GET","/finance/connections",null,null)).size());expect(404,request(b,"POST","/finance/connections/"+id+"/reauthorize",reauth(version(first)),UUID.randomUUID()));expect(404,request(b,"POST","/finance/connections/"+id+"/disconnect",Map.of("expectedVersion",version(first)),UUID.randomUUID()));
  expect(409,request(a,"DELETE","/me",null,null));
  var updated=expect(200,request(a,"POST","/finance/connections/"+id+"/reauthorize",reauth(version(first)),UUID.randomUUID()));state("ACTIVE",updated);assertFalse(Arrays.equals((byte[])row.get("cid_nonce"),(byte[])stored(uidA,id).get("cid_nonce")));
  var removed=expect(200,request(a,"POST","/finance/connections/"+id+"/disconnect",Map.of("expectedVersion",version(updated)),UUID.randomUUID()));state("DISCONNECTED",removed);assertNull(stored(uidA,id).get("cid_ciphertext"));assertNull(stored(uidA,id).get("cid_hash"));
  expect(200,request(a,"DELETE","/me",null,null));assertEquals(0L,db.queryForObject("SELECT count(*) FROM financial_connection_operations WHERE user_id=?",Long.class,uidA));assertEquals(3,provider.calls.size());
 }
 @Test void exactDuplicateAndChangedPayloadAreDistinguishedPerUser()throws Exception{
  UUID key=UUID.randomUUID();var body=connect("9999");var first=expect(200,request(a,"POST","/finance/connections",body,key));var duplicate=expect(200,request(a,"POST","/finance/connections",body,key));assertEquals(id(first),id(duplicate));assertTrue(duplicate.path("replayed").asBoolean());assertEquals(1,provider.calls.size());
  var changed=new HashMap<>(body);changed.put("password",PASSWORD+"changed");expect(409,request(a,"POST","/finance/connections",changed,key));expect(409,request(a,"POST","/finance/connections",body,UUID.randomUUID()));
  expect(200,request(b,"POST","/finance/connections",body,key));assertEquals(2,provider.calls.size());assertEquals(1L,db.queryForObject("SELECT count(*) FROM financial_connection_operations WHERE user_id=?",Long.class,uidA));
 }
 @Test void concurrentDuplicateSeesDurablePendingAndSendsOnlyOnce()throws Exception{
  UUID key=UUID.randomUUID();var behavior=new Behavior();behavior.entered=new CountDownLatch(1);behavior.release=new CountDownLatch(1);provider.behaviors.add(behavior);
  var executor=Executors.newSingleThreadExecutor();Future<Reply> first=executor.submit(()->request(a,"POST","/finance/connections",connect("9999"),key));
  try{assertTrue(behavior.entered.await(5,TimeUnit.SECONDS));var repeat=expect(202,request(a,"POST","/finance/connections",connect("9999"),key));outcome("PENDING",repeat);assertTrue(repeat.path("replayed").asBoolean());expect(409,request(a,"POST","/finance/connections",connect("9999"),UUID.randomUUID()));expect(409,request(a,"DELETE","/me",null,null));assertEquals(1,provider.calls.size());behavior.release.countDown();state("ACTIVE",expect(200,first.get(5,TimeUnit.SECONDS)));}
  finally{behavior.release.countDown();executor.shutdownNow();}
 }
 @Test void unknownCreationRemainsDurableAndCannotBeRetriedOrDeleted()throws Exception{
  fault();UUID key=UUID.randomUUID();var first=expect(202,request(a,"POST","/finance/connections",connect("9999"),key));state("UNKNOWN",first);outcome("UNKNOWN",first);assertNull(stored(uidA,id(first)).get("cid_ciphertext"));
  outcome("UNKNOWN",expect(202,request(a,"POST","/finance/connections",connect("9999"),key)));expect(409,request(a,"POST","/finance/connections",connect("9999"),UUID.randomUUID()));expect(409,request(a,"POST","/finance/connections/"+id(first)+"/disconnect",Map.of("expectedVersion",version(first)),UUID.randomUUID()));expect(409,request(a,"DELETE","/me",null,null));assertEquals(1,provider.calls.size());
 }
 @Test void unknownReauthorizationAndDisconnectPreserveEncryptedId()throws Exception{
  for(String action:List.of("reauthorize","disconnect")){
   var first=create(a,action.equals("reauthorize")?"9999":"9998");var before=(byte[])stored(uidA,id(first)).get("cid_ciphertext");fault();UUID key=UUID.randomUUID();Object body=action.equals("reauthorize")?reauth(version(first)):Map.of("expectedVersion",version(first));
   var response=expect(202,request(a,"POST","/finance/connections/"+id(first)+"/"+action,body,key));state("UNKNOWN",response);assertArrayEquals(before,(byte[])stored(uidA,id(first)).get("cid_ciphertext"));int count=provider.calls.size();expect(202,request(a,"POST","/finance/connections/"+id(first)+"/"+action,body,key));assertEquals(count,provider.calls.size());
  }
 }
 @Test void partialResultIsReviewRequiredAndKeepsEncryptedReference()throws Exception{var behavior=new Behavior();behavior.partial=true;provider.behaviors.add(behavior);var result=expect(202,request(a,"POST","/finance/connections",connect("9999"),UUID.randomUUID()));state("UNKNOWN",result);assertNotNull(stored(uidA,id(result)).get("cid_ciphertext"));expect(409,request(a,"DELETE","/me",null,null));}
 @Test void ownershipFieldsArePrivateIdempotentAndOwnerScoped()throws Exception{
  String number="9".repeat(16);var body=new LinkedHashMap<String,Object>(connect("0301"));body.put("cardOwnership",Map.of("cardNo",number,"cardPassword","12","confirmed",true));UUID key=UUID.randomUUID();var first=expect(200,request(a,"POST","/finance/connections",body,key));state("ACTIVE",first);assertFalse(first.toString().contains(number));var replay=expect(200,request(a,"POST","/finance/connections",body,key));assertTrue(replay.path("replayed").asBoolean());assertEquals(1,provider.calls.size());
  var update=new LinkedHashMap<String,Object>(reauth(version(first)));update.put("cardOwnership",body.get("cardOwnership"));expect(404,request(b,"POST","/finance/connections/"+id(first)+"/reauthorize",update,UUID.randomUUID()));expect(200,request(a,"POST","/finance/connections/"+id(first)+"/reauthorize",update,UUID.randomUUID()));assertEquals(2,provider.calls.size());
  for(String value:db.queryForList("SELECT row_to_json(c)::text FROM financial_connections c WHERE user_id=? UNION ALL SELECT row_to_json(o)::text FROM financial_connection_operations o WHERE user_id=?",String.class,uidA,uidA)){assertFalse(value.contains(number));assertFalse(value.contains("cardPassword"));assertFalse(value.contains("cardOwnership"));}
  body.put("organization","0313");expect(422,request(a,"POST","/finance/connections",body,UUID.randomUUID()));body.put("organization","0301");body.put("cardOwnership",Map.of("cardNo",number,"cardPassword","1234","confirmed",true));expect(422,request(a,"POST","/finance/connections",body,UUID.randomUUID()));assertEquals(2,provider.calls.size());
 }
 @Test void omittedOwnershipKeepsLegacyRequestFingerprint()throws Exception{var input=new FinancialConnectionService.ConnectInput("0301",LOGIN,PASSWORD.toCharArray(),true,"FINANCE_LINK_V1",null);assertFalse(json.writeValueAsString(input).contains("cardOwnership"));Arrays.fill(input.password(),'\0');}
 @Test void additionalAuthIsExplicitAndKnownReferenceAllowsNewReauth()throws Exception{
  var first=create(a,"9999");var behavior=new Behavior();behavior.code="CF-03002";provider.behaviors.add(behavior);
  var required=expect(202,request(a,"POST","/finance/connections/"+id(first)+"/reauthorize",reauth(version(first)),UUID.randomUUID()));state("AUTH_REQUIRED",required);outcome("AUTH_REQUIRED",required);
  int calls=provider.calls.size();expect(409,request(a,"POST","/finance/connections/"+id(first)+"/reauthorize",reauth(version(required)),UUID.randomUUID()));expect(409,request(a,"POST","/finance/connections/"+id(first)+"/disconnect",Map.of("expectedVersion",version(required)),UUID.randomUUID()));assertEquals(calls,provider.calls.size());db.update("UPDATE financial_connection_operations SET updated_at=now()-interval '4 minutes' WHERE id=?",UUID.fromString(required.at("/operation/id").asText()));
  var result=expect(200,request(a,"POST","/finance/connections/"+id(first)+"/reauthorize",reauth(version(required)),UUID.randomUUID()));state("ACTIVE",result);
 }
 @Test void providerCannotAssignAnAlreadyOwnedConnectedIdToAnotherUser()throws Exception{
  var one=new Behavior();one.cid="synthetic-cid-collision";provider.behaviors.add(one);state("ACTIVE",create(a,"9999"));var two=new Behavior();two.cid=one.cid;provider.behaviors.add(two);
  var other=expect(202,request(b,"POST","/finance/connections",connect("9999"),UUID.randomUUID()));state("UNKNOWN",other);assertEquals("PROVIDER_ID_OWNERSHIP_CONFLICT",other.path("operation").path("error_code").asText());assertNull(stored(uidB,id(other)).get("cid_ciphertext"));
 }
 @Test void versionConflictsAndCrossOwnerForeignKeysBlockChanges()throws Exception{
  var first=create(a,"9999");expect(409,request(a,"POST","/finance/connections/"+id(first)+"/reauthorize",reauth(0),UUID.randomUUID()));assertEquals(1,provider.calls.size());
  assertThrows(DataIntegrityViolationException.class,()->db.update("INSERT INTO financial_connection_operations(id,user_id,connection_id,idempotency_key,kind,fingerprint,fingerprint_key_id,status,previous_state) VALUES (?,?,?,?,'DISCONNECT',?,'k1','PENDING','ACTIVE')",UUID.randomUUID(),uidB,id(first),UUID.randomUUID(),new byte[32]));
 }
 @Test void stalePendingAfterLostWorkerBecomesUnknownWithoutProviderCall()throws Exception{
  var first=create(a,"9999");UUID op=UUID.fromString(first.path("operation").path("id").asText());
  db.update("UPDATE financial_connection_operations SET status='PENDING',created_at=now()-interval '3 minutes' WHERE id=?",op);db.update("UPDATE financial_connections SET state='REAUTHENTICATING' WHERE user_id=? AND id=?",uidA,id(first));
  var fetched=expect(200,request(a,"GET","/finance/connections/"+id(first),null,null));assertEquals("UNKNOWN",fetched.path("state").asText());assertEquals("UNKNOWN",db.queryForObject("SELECT status FROM financial_connection_operations WHERE id=?",String.class,op));assertEquals(1,provider.calls.size());
 }
 @Test void tamperedCiphertextIsNeverSentToProvider()throws Exception{
  var first=create(a,"9999");byte[] tampered=(byte[])stored(uidA,id(first)).get("cid_ciphertext");tampered[0]^=1;db.update("UPDATE financial_connections SET cid_ciphertext=? WHERE user_id=? AND id=?",tampered,uidA,id(first));
  var result=expect(202,request(a,"POST","/finance/connections/"+id(first)+"/reauthorize",reauth(version(first)),UUID.randomUUID()));state("UNKNOWN",result);assertEquals(1,provider.calls.size());
 }
 @Test void explicitFailureCanBeRetriedWithNewKeyWithoutStoringLogin()throws Exception{
  var failure=new Behavior();failure.failed=true;provider.behaviors.add(failure);var first=expect(200,request(a,"POST","/finance/connections",connect("9999"),UUID.randomUUID()));state("FAILED",first);outcome("FAILED",first);
  var second=create(a,"9999");assertEquals(id(first),id(second));state("ACTIVE",second);
  var all=db.queryForList("SELECT row_to_json(c)::text AS value FROM financial_connections c WHERE user_id=? UNION ALL SELECT row_to_json(o)::text FROM financial_connection_operations o WHERE user_id=?",uidA,uidA);
  for(var row:all){String value=(String)row.get("value");assertFalse(value.contains(PASSWORD));assertFalse(value.contains(LOGIN));assertFalse(value.contains("synthetic-cid"));}
 }
 @Test void reauthorizeAndDisconnectRetriesDoNotRepeatProviderMutation()throws Exception{
  var first=create(a,"9999");UUID id=id(first),key=UUID.randomUUID();var body=reauth(version(first));
  var updated=expect(200,request(a,"POST","/finance/connections/"+id+"/reauthorize",body,key));var repeat=expect(200,request(a,"POST","/finance/connections/"+id+"/reauthorize",body,key));assertTrue(repeat.path("replayed").asBoolean());assertEquals(2,provider.calls.size());
  UUID deleteKey=UUID.randomUUID();var deletion=Map.of("expectedVersion",version(updated));expect(200,request(a,"POST","/finance/connections/"+id+"/disconnect",deletion,deleteKey));repeat=expect(200,request(a,"POST","/finance/connections/"+id+"/disconnect",deletion,deleteKey));state("DISCONNECTED",repeat);assertTrue(repeat.path("replayed").asBoolean());assertEquals(3,provider.calls.size());
 }
 @Test void timeoutAfterProviderReceivedRequestRemainsUnknownInPostgres()throws Exception{
  var behavior=new Behavior();behavior.entered=new CountDownLatch(1);behavior.release=new CountDownLatch(1);provider.behaviors.add(behavior);UUID key=UUID.randomUUID();
  try{var first=expect(202,request(a,"POST","/finance/connections",connect("9999"),key));state("UNKNOWN",first);assertEquals(0,behavior.entered.getCount());assertEquals("UNKNOWN",stored(uidA,id(first)).get("state"));expect(202,request(a,"POST","/finance/connections",connect("9999"),key));assertEquals(1,provider.calls.size());}
  finally{behavior.release.countDown();}
 }
 @Test void reconnectRecordsTheNewConsentInsteadOfReusingOldTimestamp()throws Exception{
  var first=create(a,"9999");expect(200,request(a,"POST","/finance/connections/"+id(first)+"/disconnect",Map.of("expectedVersion",version(first)),UUID.randomUUID()));
  db.update("UPDATE financial_connections SET consented_at='2000-01-01T00:00:00Z' WHERE user_id=? AND id=?",uidA,id(first));var second=create(a,"9999");assertEquals(id(first),id(second));assertTrue(((java.sql.Timestamp)stored(uidA,id(first)).get("consented_at")).toInstant().isAfter(Instant.parse("2026-01-01T00:00:00Z")),"Reconnect must record the newly accepted consent");
 }

 @Test void failedCreateCanRetryWithFreshVersionAndReplayIsNotSentAgain()throws Exception{
  var bad=new Behavior();bad.failed=true;provider.behaviors.add(bad);var failed=create(a,"9999");state("FAILED",failed);UUID connection=id(failed),key=UUID.randomUUID();long v=version(failed);
  expect(404,request(b,"POST","/finance/connections/"+connection+"/retry",reauth(v),UUID.randomUUID()));
  expect(409,request(a,"POST","/finance/connections/"+connection+"/retry",reauth(v+1),UUID.randomUUID()));assertEquals(1,provider.calls.size());
  var retried=expect(200,request(a,"POST","/finance/connections/"+connection+"/retry",reauth(v),key));state("ACTIVE",retried);assertEquals(connection,id(retried));
  expect(200,request(a,"POST","/finance/connections/"+connection+"/retry",reauth(v),key));assertEquals(2,provider.calls.size());
 }
 @Test void failedRecordArchiveRetainsAuditAndCanBeRecreated()throws Exception{
  var bad=new Behavior();bad.failed=true;provider.behaviors.add(bad);UUID originalKey=UUID.randomUUID();var failed=expect(200,request(a,"POST","/finance/connections",connect("9999"),originalKey));UUID id=id(failed);long v=version(failed);
  expect(404,request(b,"POST","/finance/connections/"+id+"/archive",Map.of("expectedVersion",v),null));
  expect(409,request(a,"POST","/finance/connections/"+id+"/archive",Map.of("expectedVersion",v+1),null));
  var removed=expect(200,request(a,"POST","/finance/connections/"+id+"/archive",Map.of("expectedVersion",v),null));assertFalse(removed.get("externalDisconnectRequested").asBoolean());
  assertEquals(0,expect(200,request(a,"GET","/finance/connections",null,null)).size());expect(404,request(a,"GET","/finance/connections/"+id,null,null));
  expect(200,request(a,"POST","/finance/connections/"+id+"/archive",Map.of("expectedVersion",v),null));assertEquals(1,provider.calls.size());
  var replay=expect(200,request(a,"POST","/finance/connections",connect("9999"),originalKey));assertTrue(replay.at("/connection/archived").asBoolean());assertEquals(1,provider.calls.size());
  assertEquals(1L,db.queryForObject("SELECT count(*) FROM financial_connection_operations WHERE user_id=? AND connection_id=?",Long.class,uidA,id));
  var again=create(a,"9999");state("ACTIVE",again);assertEquals(id,id(again));assertFalse(again.at("/connection/archived").asBoolean());
 }
 @Test void unknownActiveAndAdditionalAuthCannotBeArchivedOrRetried()throws Exception{
  var active=create(a,"9999");UUID id=id(active);long v=version(active);
  expect(409,request(a,"POST","/finance/connections/"+id+"/archive",Map.of("expectedVersion",v),null));
  expect(409,request(a,"POST","/finance/connections/"+id+"/retry",reauth(v),UUID.randomUUID()));
  fault();var unknown=expect(202,request(a,"POST","/finance/connections/"+id+"/reauthorize",reauth(v),UUID.randomUUID()));state("UNKNOWN",unknown);
  expect(409,request(a,"POST","/finance/connections/"+id+"/archive",Map.of("expectedVersion",version(unknown)),null));
  expect(409,request(a,"POST","/finance/connections/"+id+"/retry",reauth(version(unknown)),UUID.randomUUID()));assertEquals(2,provider.calls.size());
 }
}
