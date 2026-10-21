package dev.cardadvisor.ai;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.net.*;import java.net.http.*;import java.nio.file.*;import java.time.Duration;import java.util.*;

/** Official Responses API, typed selection-only output. Runtime secrets never become diagnostics. */
@Component
public class OpenAiGateway {
 private final ObjectMapper json;private final HttpClient http;private final URI endpoint;
 private final String model;private final String key;private final boolean enabled;
 @org.springframework.beans.factory.annotation.Autowired
 public OpenAiGateway(ObjectMapper json,@Value("${openai.config-file:}")String path){
  this.json=json;this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();this.endpoint=URI.create("https://api.openai.com/v1/responses");
  var p=new Properties();if(!path.isBlank())try(var input=Files.newInputStream(Path.of(path))){p.load(input);}catch(Exception e){throw new IllegalStateException("OPENAI_CONFIG_UNREADABLE");}
  model=p.getProperty("model","");String secret="";
  if(!p.getProperty("apiKeyFile","").isBlank())try{secret=Files.readString(Path.of(p.getProperty("apiKeyFile"))).strip();}catch(Exception e){throw new IllegalStateException("OPENAI_KEY_FILE_UNREADABLE");}
  key=secret;enabled=Boolean.parseBoolean(p.getProperty("enabled","false"))&&!key.isBlank()&&model.matches("[A-Za-z0-9._-]{1,80}");
 }
 OpenAiGateway(ObjectMapper json,URI loopback){
  if(!Set.of("127.0.0.1","localhost","::1").contains(loopback.getHost()))throw new IllegalArgumentException("TEST_ENDPOINT_MUST_BE_LOOPBACK");
  this.json=json;this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();this.endpoint=loopback;this.model="synthetic-contract-model";this.key="SYNTHETIC_KEY";this.enabled=true;
 }
 static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
  private final java.util.concurrent.CompletableFuture<byte[]> body=new java.util.concurrent.CompletableFuture<>();
  private final java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
  private java.util.concurrent.Flow.Subscription subscription;
  public java.util.concurrent.CompletionStage<byte[]> getBody(){return body;}
  public void onSubscribe(java.util.concurrent.Flow.Subscription s){subscription=s;s.request(1);}
  public void onNext(List<java.nio.ByteBuffer> chunks){
   for(var chunk:chunks){if(out.size()+chunk.remaining()>65536){subscription.cancel();body.completeExceptionally(new IllegalStateException("AI_RESPONSE_TOO_LARGE"));return;}byte[] b=new byte[chunk.remaining()];chunk.get(b);out.writeBytes(b);}
   subscription.request(1);
  }
  public void onError(Throwable error){body.completeExceptionally(new IllegalStateException("AI_RESPONSE_FAILED"));}
  public void onComplete(){body.complete(out.toByteArray());}
 }
 public boolean ready(){return enabled;}
 public String version(){return model.isBlank()?"disabled":model;}
 public JsonNode select(String task,JsonNode facts,List<String> allowed)throws Exception{
  if(!enabled)throw new IllegalStateException("AI_DISABLED");
  ObjectNode schema=json.createObjectNode();schema.put("type","object");schema.put("additionalProperties",false);
  var ids=schema.putObject("properties").putObject("ids");ids.put("type","array");var items=ids.putObject("items");items.put("type","string");var enums=items.putArray("enum");allowed.forEach(enums::add);schema.putArray("required").add("ids");
  var request=json.createObjectNode();request.put("model",model);request.put("store",false);request.put("max_output_tokens",400);
  request.put("instructions","Select up to three relevant allowed IDs for task "+task+". Use only the supplied facts. Never infer a guaranteed future benefit or encourage extra spending. Do not create new numbers or text.");
  request.put("input",facts.toString());var format=request.putObject("text").putObject("format");format.put("type","json_schema");format.put("name","advisor_selection_v1");format.put("strict",true);format.set("schema",schema);
  var wire=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+key).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(request.toString())).build();
  var pending=http.sendAsync(wire,info->new LimitedBody());
  try{
   var response=pending.get(8,java.util.concurrent.TimeUnit.SECONDS);
   if(response.statusCode()!=200)throw new IllegalStateException("AI_PROVIDER_UNAVAILABLE");
   byte[] bytes=response.body();
   JsonNode body=json.readTree(bytes);if(!"completed".equals(body.path("status").asText()))throw new IllegalStateException("AI_INCOMPLETE");
   String output=null;
   for(var item:body.path("output"))if("message".equals(item.path("type").asText()))for(var c:item.path("content")){
    if("refusal".equals(c.path("type").asText()))throw new IllegalStateException("AI_REFUSED");
    if("output_text".equals(c.path("type").asText())){if(output!=null)throw new IllegalStateException("AI_AMBIGUOUS_RESPONSE");output=c.path("text").asText();}
   }
   if(output==null)throw new IllegalStateException("AI_EMPTY_RESPONSE");return json.readTree(output);
  }finally{if(!pending.isDone())pending.cancel(true);}
 }
}
