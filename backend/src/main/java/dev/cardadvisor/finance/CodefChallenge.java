package dev.cardadvisor.finance;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Only the documented continuation identifiers are retained; provider messages are discarded. */
public final class CodefChallenge {
 private static final ObjectMapper JSON=new ObjectMapper();
 private final String method;private final ObjectNode info;
 private CodefChallenge(String method,ObjectNode info){this.method=method;this.info=info;}
 public static CodefChallenge parse(JsonNode data){
  if(data==null||!data.isObject()||!data.path("method").isTextual())return null;
  String method=data.path("method").asText();
  if(!Set.of("smsAuthNo","emailAuthNo","simpleAuth").contains(method))return null;
  ObjectNode info=JSON.createObjectNode();
  for(String field:List.of("jobIndex","threadIndex","jti","twoWayTimestamp")){
   JsonNode value=data.get(field);
   if(value==null||!(value.isTextual()||value.isIntegralNumber())||value.asText().isBlank()||value.asText().length()>512||value.asText().chars().anyMatch(Character::isISOControl))return null;
   if(!field.equals("jti")&&!value.asText().matches("[0-9]{1,20}"))return null;
   info.set(field,value.deepCopy());
  }
  return new CodefChallenge(method,info);
 }
 public static CodefChallenge restore(String stored){try{return Objects.requireNonNull(parse(JSON.readTree(stored)));}catch(Exception e){throw new CodefFailure(CodefFailure.Kind.INVALID_RESPONSE);}}
 public String method(){return method;}
 public String sealedPayload(){ObjectNode out=info.deepCopy();out.put("method",method);return out.toString();}
 Map<String,Object> parameters(char[] answer){
  if(answer==null||answer.length<1||answer.length>32)throw new CodefFailure(CodefFailure.Kind.CONFIGURATION);
  String value=new String(answer);
  if(method.equals("simpleAuth")?!value.equals("1"):!value.matches("[A-Za-z0-9]{1,32}"))throw new CodefFailure(CodefFailure.Kind.CONFIGURATION);
  return Map.of("is2Way",true,"twoWayInfo",info.deepCopy(),method,value);
 }
 @Override public String toString(){return "CodefChallenge[redacted]";}
}
