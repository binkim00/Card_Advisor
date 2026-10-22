package dev.cardadvisor.finance;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.io.IOException;
import static dev.cardadvisor.finance.CodefFailure.Kind.*;

/** CODEF protocol adapter. No logging/persistence. Successful lookup is NOT a ledger sync. */
public final class CodefClient {
 private static final int MAX_BYTES=2*1024*1024;
 private final CodefSettings settings;
 private final HttpClient http;
 private final ObjectMapper json=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
  .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
 private final URI base,oauth;
 private final Clock clock;
 private final Duration timeout;
 private String accessToken;
 private Instant expires=Instant.MIN;

 public CodefClient(CodefSettings settings){
  this(settings,settings.environment.uri,URI.create("https://oauth.codef.io/oauth/token"),Clock.systemUTC(),Duration.ofSeconds(30));
 }
 // Loopback injection is package-private and used by contract tests, never configuration.
 CodefClient(CodefSettings settings,URI base,URI oauth,Clock clock,Duration timeout){
  this.settings=settings;this.base=base;this.oauth=oauth;this.clock=clock;this.timeout=timeout;
  http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
 }
 public void requireAvailable(){enabled();}
 public Map<String,Object> verifyAuthentication(){token();return Map.of("oauthVerified",true,"environment",environment(),"financialInstitutionAuthenticated",false);}
 public String environment(){return settings.environment.name();}
 public String scope(){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(settings.clientId.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new CodefFailure(CONFIGURATION);}}
 private void enabled(){if(!settings.enabled)throw new CodefFailure(DISABLED);}
 public Map<String,Object> readiness(){return Map.of("provider","CODEF","enabled",settings.enabled,
  "environment",settings.environment.name(),"realConnectionVerified",false,"userConnectionApiReady",false,
  "approvalMappingVerified",false,"automaticSyncEnabled",false,
  "syntheticProvider","http".equals(base.getScheme())&&"127.0.0.1".equals(base.getHost()));}

 /** Caller retains responsibility for closing/clearing its own credential buffers. */
 public static final class CardLogin implements AutoCloseable {
  final String organization,id;final char[] password;
  public CardLogin(String organization,String id,char[] password){
   institution(organization);if(id==null||id.isBlank()||password==null||password.length==0)throw new CodefFailure(CONFIGURATION);
   this.organization=organization;this.id=id;this.password=password.clone();
  }
  public void close(){Arrays.fill(password,'\0');}
  @Override public String toString(){return "CardLogin[redacted]";}
 }
 public static final class AccountResult {
  public enum Status { SUCCEEDED, PARTIAL, FAILED }
  public final Status status;private final String connectedId;
  public final String providerCode;
  AccountResult(Status status,String connectedId){this(status,connectedId,null);}
  AccountResult(Status status,String connectedId,String code){this.status=status;this.connectedId=connectedId;this.providerCode=CodefFailure.safeCode(code);}
  /** Server internal only. Must be bound to an authenticated owner and encrypted before persistence. */
  public String connectedId(){return connectedId;}
  @Override public String toString(){return "AccountResult["+status+", redacted]";}
 }
 public static final class UnmappedData {
  private final JsonNode value;
  UnmappedData(JsonNode value){this.value=value;}
  public JsonNode data(){return value.deepCopy();}
  @Override public String toString(){return "UnmappedData[not imported, redacted]";}
 }
 private static void institution(String value){if(value==null||!value.matches("[0-9]{4}"))throw new CodefFailure(CONFIGURATION);}
 private static void connected(String value){if(value==null||value.isBlank()||value.length()>512)throw new CodefFailure(CONFIGURATION);}
 private Map<String,Object> identity(String organization){
  institution(organization);return new LinkedHashMap<>(Map.of("countryCode","KR","businessType","CD","clientType","P","organization",organization,"loginType","1"));
 }
 private Map<String,Object> login(CardLogin login){
  var result=identity(login.organization);result.put("id",login.id);result.put("password",encrypt(login.password));return result;
 }
 private String encrypt(char[] password){
  byte[] bytes=null;
  try{
   // Explicit transformation matches the official Java SDK's RSA default on SunJCE.
   var key=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(settings.publicKey)));
   var cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");cipher.init(Cipher.ENCRYPT_MODE,key);
   ByteBuffer buffer=StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));bytes=new byte[buffer.remaining()];buffer.get(bytes);
   if(buffer.hasArray())Arrays.fill(buffer.array(),(byte)0);
   return Base64.getEncoder().encodeToString(cipher.doFinal(bytes));
  }catch(Exception e){throw new CodefFailure(CONFIGURATION);}finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);}
 }
 public AccountResult create(CardLogin login){enabled();return account("create",null,login.organization,login(login));}
 public AccountResult add(String connectedId,CardLogin login){enabled();connected(connectedId);return account("add",connectedId,login.organization,login(login));}
 public AccountResult update(String connectedId,CardLogin login){enabled();connected(connectedId);return account("update",connectedId,login.organization,login(login));}
 public AccountResult delete(String connectedId,String organization){enabled();connected(connectedId);return account("delete",connectedId,organization,identity(organization));}
 private AccountResult account(String operation,String cid,String organization,Map<String,Object> item){
  var request=new LinkedHashMap<String,Object>();request.put("accountList",List.of(item));if(cid!=null)request.put("connectedId",cid);
  JsonNode data=call("/v1/account/"+operation,request,true);
  JsonNode successes=data.path("successList"),errors=data.path("errorList");
  if(!successes.isArray()||successes.size()>1||(!errors.isMissingNode()&&!errors.isArray())||(successes.isEmpty()&&errors.isEmpty()))throw new CodefFailure(UNKNOWN_OUTCOME);
  boolean matched=false;
  for(JsonNode entry:successes){
   if(!entry.path("organization").asText().equals(organization)||!entry.path("businessType").asText().equals("CD")||!entry.path("clientType").asText().equals("P")||!entry.path("countryCode").asText().equals("KR"))throw new CodefFailure(UNKNOWN_OUTCOME);
   if(entry.has("code")&&!entry.path("code").asText().equals("CF-00000"))throw new CodefFailure(UNKNOWN_OUTCOME);
   matched=true;
  }
  var status=!matched?AccountResult.Status.FAILED:errors.size()>0?AccountResult.Status.PARTIAL:AccountResult.Status.SUCCEEDED;
  String returned=data.path("connectedId").asText("");
  if(matched&&(returned.isBlank()||returned.length()>512||(cid!=null&&!cid.equals(returned))))throw new CodefFailure(UNKNOWN_OUTCOME);
  String diagnostic=null;for(JsonNode entry:errors){String candidate=CodefFailure.safeCode(entry.path("code").asText(null));if(candidate!=null&&!candidate.equals("CF-00000")){diagnostic=candidate;break;}}
  return new AccountResult(status,returned.isBlank()?null:returned,diagnostic);
 }
 public UnmappedData accounts(String cid){connected(cid);return new UnmappedData(call("/v1/account/list",Map.of("connectedId",cid),false));}
 /** Account-list proves registration only, never the validity of the credentials or a past update. */
 public boolean registered(String cid,String organization){
  JsonNode data=accounts(cid).data();if(!data.isObject()||!cid.equals(data.path("connectedId").asText())||!data.path("accountList").isArray())throw new CodefFailure(INVALID_RESPONSE);
  int matches=0;for(JsonNode item:data.path("accountList")){
   for(String key:List.of("countryCode","businessType","clientType","organizationCode","loginType"))if(!item.path(key).isTextual()||item.path(key).asText().isBlank())throw new CodefFailure(INVALID_RESPONSE);
   if("KR".equals(item.path("countryCode").asText())&&"CD".equals(item.path("businessType").asText())&&"P".equals(item.path("clientType").asText())&&organization.equals(item.path("organizationCode").asText())){if(!"1".equals(item.path("loginType").asText()))throw new CodefFailure(INVALID_RESPONSE);matches++;}
  }
  if(matches>1)throw new CodefFailure(INVALID_RESPONSE);return matches==1;
 }
 public UnmappedData cards(String cid,String organization,String birthDate){
  var request=lookup(cid,organization,birthDate);return new UnmappedData(call("/v1/kr/card/p/account/card-list",request,false));
 }
 public UnmappedData approvals(String cid,String organization,String birthDate,LocalDate from,LocalDate to,Map<String,String> options){
  if(from==null||to==null||from.isAfter(to))throw new CodefFailure(CONFIGURATION);
  var allowed=Set.of("orderBy","inquiryType","cardNo","cardName","duplicateCardSelect","duplicateCardIdx","memberStoreInfoType");
  if(options==null||!allowed.containsAll(options.keySet())||options.values().stream().anyMatch(Objects::isNull))throw new CodefFailure(CONFIGURATION);
  var request=lookup(cid,organization,birthDate);request.put("startDate",from.format(DateTimeFormatter.BASIC_ISO_DATE));request.put("endDate",to.format(DateTimeFormatter.BASIC_ISO_DATE));request.putAll(options);
  return new UnmappedData(call("/v1/kr/card/p/account/approval-list",request,false));
 }
 private Map<String,Object> lookup(String cid,String organization,String birthDate){
  connected(cid);institution(organization);if(birthDate==null||!birthDate.matches("(?:[0-9]{6}|[0-9]{8})?"))throw new CodefFailure(CONFIGURATION);
  return new LinkedHashMap<>(Map.of("connectedId",cid,"organization",organization,"birthDate",birthDate));
 }
 private synchronized String token(){
  enabled();if(accessToken!=null&&clock.instant().isBefore(expires))return accessToken;
  String basic=Base64.getEncoder().encodeToString((settings.clientId+":"+settings.clientSecret).getBytes(StandardCharsets.UTF_8));
  var response=send(oauth,"Basic "+basic,"grant_type=client_credentials&scope=read",false);
  if(response.statusCode()!=200){int status=response.statusCode();throw new CodefFailure(status==400||status==401?AUTHENTICATION:status==403?ACCESS_DENIED:status==429?RATE_LIMITED:TRANSPORT,status);}
  JsonNode body=parse(response.body());JsonNode lifetime=body.path("expires_in");
  if(!body.path("access_token").isTextual()||body.path("access_token").asText().isBlank()||!"bearer".equalsIgnoreCase(body.path("token_type").asText())||!lifetime.isIntegralNumber()||!lifetime.canConvertToLong()||lifetime.asLong()<=0)throw new CodefFailure(INVALID_RESPONSE);
  try{expires=clock.instant().plusSeconds(lifetime.asLong()-Math.min(30,lifetime.asLong()/2));}catch(Exception e){throw new CodefFailure(INVALID_RESPONSE);}
  accessToken=body.path("access_token").asText();return accessToken;
 }
 private synchronized void invalidate(String rejected){if(Objects.equals(accessToken,rejected)){accessToken=null;expires=Instant.MIN;}}
 private JsonNode call(String path,Map<String,?> body,boolean mutation){
  enabled();String encoded;
  try{encoded=URLEncoder.encode(json.writeValueAsString(body),StandardCharsets.UTF_8);}catch(Exception e){throw new CodefFailure(CONFIGURATION);}
  for(int attempt=0;attempt<2;attempt++){
   String used=token();var response=send(base.resolve(path),"Bearer "+used,encoded,mutation);
   int status=response.statusCode();
   if(status==401){invalidate(used);if(!mutation&&attempt==0)continue;throw new CodefFailure(AUTHENTICATION);}
   if(status==403)throw new CodefFailure(ACCESS_DENIED);
   if(status==429)throw new CodefFailure(RATE_LIMITED);
   if(status!=200)throw new CodefFailure(mutation?UNKNOWN_OUTCOME:TRANSPORT);
   JsonNode envelope;
   try{envelope=parse(response.body());}catch(CodefFailure e){throw new CodefFailure(mutation?UNKNOWN_OUTCOME:INVALID_RESPONSE);}
   String code=envelope.path("result").path("code").asText("");
   if(code.equals("CF-00401")){invalidate(used);if(!mutation&&attempt==0)continue;throw new CodefFailure(AUTHENTICATION,status,code);}
   if(code.equals("CF-00403"))throw new CodefFailure(ACCESS_DENIED,status,code);
   if(code.equals("CF-03002"))throw new CodefFailure(AUTH_REQUIRED,status,code);
   if(code.isBlank())throw new CodefFailure(mutation?UNKNOWN_OUTCOME:INVALID_RESPONSE);
   if(!code.equals("CF-00000"))throw new CodefFailure(REJECTED,status,code);
   if(!envelope.hasNonNull("data")||!(envelope.get("data").isObject()||envelope.get("data").isArray()))throw new CodefFailure(mutation?UNKNOWN_OUTCOME:INVALID_RESPONSE);
   return envelope.get("data");
  }
  throw new CodefFailure(AUTHENTICATION);
 }
 private JsonNode parse(byte[] bytes){
  try{
   String text=new String(bytes,StandardCharsets.UTF_8).strip();
   // Literal JSON must not be URL-decoded: it could contain '+' or '%'.
   if(text.startsWith("%"))text=URLDecoder.decode(text,StandardCharsets.UTF_8);
   JsonNode result=json.readTree(text);if(result==null||!result.isObject())throw new IOException();return result;
  }catch(Exception e){throw new CodefFailure(INVALID_RESPONSE);}
 }
 private HttpResponse<byte[]> send(URI uri,String authorization,String body,boolean mutation){
  try{
   var request=HttpRequest.newBuilder(uri).timeout(timeout).header("Content-Type","application/x-www-form-urlencoded")
    .header("Accept","application/json").header("Authorization",authorization).POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
   return http.send(request,info->new BoundedBody());
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new CodefFailure(mutation?UNKNOWN_OUTCOME:INTERRUPTED);}
  catch(Exception e){throw new CodefFailure(mutation?UNKNOWN_OUTCOME:TRANSPORT);}
 }
 private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
  final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();Flow.Subscription subscription;long received;
  public CompletionStage<byte[]> getBody(){return delegate.getBody();}
  public void onSubscribe(Flow.Subscription s){subscription=s;delegate.onSubscribe(s);}
  public void onNext(List<ByteBuffer> buffers){for(var buffer:buffers)received+=buffer.remaining();if(received>MAX_BYTES){subscription.cancel();delegate.onError(new IOException("CODEF_RESPONSE_TOO_LARGE"));}else delegate.onNext(buffers);}
  public void onError(Throwable t){delegate.onError(t);}public void onComplete(){delegate.onComplete();}
 }
 @Override public String toString(){return "CodefClient[redacted]";}
}
