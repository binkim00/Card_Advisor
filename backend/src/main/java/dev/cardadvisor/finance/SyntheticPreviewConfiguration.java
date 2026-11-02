package dev.cardadvisor.finance;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit private preview only. Every provider/OAuth request terminates on container loopback. */
@Configuration @Profile("synthetic-preview")
public class SyntheticPreviewConfiguration {
 public static final String CLIENT="card-advisor-private-preview-v1";
 static void requireBlank(String...paths){for(String path:paths)if(path!=null&&!path.isBlank())throw new IllegalStateException("PRIVATE_PREVIEW_REQUIRES_REAL_PROVIDER_CONFIGURATIONS_DISABLED");}
 @Bean(destroyMethod="close") public PreviewGateway previewGateway(
  @Value("${codef.config-file:}")String config,@Value("${codef.storage-key-file:}")String storage){
  requireBlank(config,storage,System.getenv("CODEF_CONFIG_FILE"),System.getenv("CODEF_STORAGE_KEY_FILE"),System.getenv("ACCOUNT_MAIL_CONFIG_FILE"));
  return new PreviewGateway();
 }
 @Bean @Primary public ConnectedIdCipher previewCipher(@Value("${card.preview.key-file:}")String path){
  if(path.isBlank())throw new IllegalStateException("PRIVATE_PREVIEW_KEY_FILE_REQUIRED");
  var cipher=ConnectedIdCipher.fromFile(path);cipher.requireConfigured();return cipher;
 }
 @Bean @Primary public CodefClient previewClient(PreviewGateway gateway){
  return new CodefClient(new CodefSettings(gateway.settings),gateway.base,gateway.base.resolve("/oauth/token"),Clock.systemUTC(),Duration.ofSeconds(5));
 }
 public static final class PreviewGateway implements AutoCloseable {
  final Properties settings=new Properties();final HttpServer server;final ExecutorService executor=Executors.newFixedThreadPool(2);final URI base;
  PreviewGateway(){try{
   var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
   settings.setProperty("enabled","true");settings.setProperty("usageApproved","true");settings.setProperty("environment","SANDBOX");settings.setProperty("clientId",CLIENT);
   settings.setProperty("clientSecret","synthetic-preview-no-external-access");settings.setProperty("publicKey",Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
   server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);base=URI.create("http://127.0.0.1:"+server.getAddress().getPort());
   server.setExecutor(executor);server.createContext("/",exchange->{
    int status=200;byte[] reply;
    try{
     var json=new ObjectMapper();String route=exchange.getRequestURI().getPath();Object body;
     if(route.equals("/oauth/token"))body=Map.of("access_token","synthetic-private-preview-token","token_type","bearer","expires_in",600);
     else{
      byte[] raw=exchange.getRequestBody().readNBytes(65537);if(raw.length>65536)throw new IllegalArgumentException();
      JsonNode request=json.readTree(URLDecoder.decode(new String(raw,StandardCharsets.UTF_8),StandardCharsets.UTF_8));Object data;
      if(route.equals("/v1/account/list"))data=Map.of("connectedId",request.path("connectedId").asText(),"accountList",List.of(Map.of("countryCode","KR","businessType","CD","clientType","P","organizationCode","9999","loginType","1")));
      else if(route.startsWith("/v1/account/")){
       String org=request.path("accountList").path(0).path("organization").asText();if(!"9999".equals(org))throw new IllegalArgumentException();
       String cid=request.path("connectedId").asText("preview-"+UUID.randomUUID());
       data=Map.of("connectedId",cid,"successList",List.of(Map.of("countryCode","KR","businessType","CD","clientType","P","organization","9999","code","CF-00000")),"errorList",List.of());
      }else{
       if(!"9999".equals(request.path("organization").asText())||!request.path("connectedId").asText().startsWith("preview-"))throw new IllegalArgumentException();
       if(route.equals("/v1/kr/card/p/account/card-list"))data=List.of(
        Map.of("resCardName","[체험] 일상 카드","resCardNo","1234********5678","resCardType","가상 체크카드"),
        Map.of("resCardName","[체험] 쇼핑 카드","resCardNo","9876********4321","resCardType","가상 신용카드"));
       else if(route.equals("/v1/kr/card/p/account/approval-list"))data=List.of();else throw new IllegalArgumentException();
      }
      body=Map.of("result",Map.of("code","CF-00000"),"data",data);
     }
     reply=json.writeValueAsBytes(body);
    }catch(Exception ignored){status=503;reply="{\"result\":{\"code\":\"CF-99999\"},\"data\":{}}".getBytes(StandardCharsets.UTF_8);}
    try{exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,reply.length);exchange.getResponseBody().write(reply);}finally{exchange.close();}
   });server.start();
  }catch(Exception e){executor.shutdownNow();throw new IllegalStateException("PRIVATE_PREVIEW_START_FAILED");}}
  @Override public void close(){server.stop(0);executor.shutdownNow();}
 }
}
