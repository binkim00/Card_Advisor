package dev.cardadvisor.account;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;import org.springframework.web.bind.annotation.RequestBody;import java.nio.file.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class ApiContractPostgresTest extends ServiceCompletionPostgresTest {
 @Autowired RequestMappingHandlerMapping routes;
 @Test void documentationCoversRegisteredApiRoutesAndBodies()throws Exception{
  var document=json.readTree(Files.readString(Path.of("../docs/api/openapi.json")));var paths=document.path("paths");var missing=new TreeSet<String>();
  for(var e:routes.getHandlerMethods().entrySet())if(e.getValue().getBeanType().getPackageName().startsWith("dev.cardadvisor"))for(String path:e.getKey().getPatternValues())if(path.startsWith("/v1"))for(var method:e.getKey().getMethodsCondition().getMethods()){
   var op=paths.path(path.substring(3)).path(method.name().toLowerCase(Locale.ROOT));String label=method+" "+path;
   if(op.isMissingNode()){missing.add(label);continue;}
   if(Arrays.stream(e.getValue().getMethodParameters()).anyMatch(p->p.hasParameterAnnotation(RequestBody.class))&&!op.has("requestBody"))missing.add(label+" requestBody");
   for(var parameter:e.getValue().getMethodParameters())if(parameter.hasParameterAnnotation(RequestBody.class)&&parameter.getParameterType().isRecord()){
    var schema=op.path("requestBody").path("content").path("application/json").path("schema");if(schema.has("$ref"))schema=document.at(schema.path("$ref").asText().substring(1));
    var described=new TreeSet<String>();schema.path("properties").fieldNames().forEachRemaining(described::add);var actual=new TreeSet<String>();for(var component:parameter.getParameterType().getRecordComponents())actual.add(component.getName());if(!actual.equals(described))missing.add(label+" fields:"+actual+" documented:"+described);
   }
   var names=new HashSet<String>();op.path("parameters").forEach(p->{if(p.path("in").asText().equals("path")&&p.path("required").asBoolean())names.add(p.path("name").asText());});
   java.util.regex.Pattern.compile("\\{(\\w+)\\}").matcher(path).results().forEach(m->{if(!names.contains(m.group(1)))missing.add(label+" path:"+m.group(1));});
  }
  assertEquals(Set.of(),missing);
 }
}
