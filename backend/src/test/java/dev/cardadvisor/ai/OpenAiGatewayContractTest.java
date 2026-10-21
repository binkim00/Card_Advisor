package dev.cardadvisor.ai;
import org.junit.jupiter.api.*;import com.sun.net.httpserver.*;import com.fasterxml.jackson.databind.*;
import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
class OpenAiGatewayContractTest {
 final ObjectMapper json=new ObjectMapper();HttpServer server;final AtomicReference<JsonNode> request=new AtomicReference<>();String reply;
 @BeforeEach void start()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/v1/responses",e->{try{request.set(json.readTree(e.getRequestBody()));byte[] body=reply.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);}finally{e.close();}});server.start();}
 @AfterEach void stop(){server.stop(0);}
 OpenAiGateway gateway(){return new OpenAiGateway(json,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/responses"));}
 @Test void officialWireAndValidatedSelection()throws Exception{
  reply="{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"ids\\\":[\\\"BUDGET\\\"]}\"}]}]}";
  var result=gateway().select("EXPLANATION",json.readTree("[{\"id\":\"BUDGET\",\"values\":{\"remainingWon\":40000}}]"),List.of("BUDGET"));
  assertEquals(List.of("BUDGET"),AiAssistanceService.validateSelection(result,List.of("BUDGET")));
  assertFalse(request.get().get("store").asBoolean());assertEquals("json_schema",request.get().at("/text/format/type").asText());assertTrue(request.get().at("/text/format/strict").asBoolean());
  assertFalse(request.get().has("user"));assertFalse(request.get().has("tools"));
 }
 @Test void inventedNumbersFactsAndDuplicateIdsAreRejected()throws Exception{
  for(String raw:List.of("{\"ids\":[\"UNKNOWN\"]}","{\"ids\":[\"BUDGET\"],\"benefitWon\":999999}","{\"ids\":[\"BUDGET\",\"BUDGET\"]}","{\"ids\":[]}"))assertThrows(IllegalArgumentException.class,()->AiAssistanceService.validateSelection(json.readTree(raw),List.of("BUDGET")));
 }
 @Test void incompleteAndRefusalNeverBecomeExplanation(){reply="{\"status\":\"incomplete\",\"output\":[]}";assertThrows(Exception.class,()->gateway().select("EXPLANATION",json.createArrayNode(),List.of("A")));}
}
