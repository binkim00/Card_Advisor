package dev.cardadvisor.finance;

import com.fasterxml.jackson.databind.JsonNode;
import dev.cardadvisor.api.ApiError;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Accept only reviewed fields; never retain PAN, owner names, expiry dates or raw JSON. */
public final class HeldCardNormalizer {
 public record Held(String key,String name,String mask,String type){}
 private String text(JsonNode n,String field,int max,boolean required){
  if(!n.path(field).isTextual()){if(!required&&n.path(field).isMissingNode())return "";throw invalid();}
  String s=n.path(field).asText().strip();
  if(s.length()>max||required&&s.isBlank()||s.chars().anyMatch(c->Character.isISOControl(c)))throw invalid();
  return s;
 }
 private ApiError invalid(){return new ApiError(422,"INVALID_HELD_CARD_RESPONSE");}
 public List<Held> normalize(String organization,JsonNode data){
  if(data==null||!data.isArray()||data.size()>200)throw invalid();
  var result=new ArrayList<Held>();var keys=new HashSet<String>();
  for(JsonNode n:data){
   if(!n.isObject())throw invalid();
   String name=text(n,"resCardName",120,true),raw=text(n,"resCardNo",30,true).replace("-","").replace(" ","");
   String mask;
   if(raw.matches("[0-9]{4}[*Xx]{4,11}[0-9]{4}")||raw.matches("[*Xx]{8,15}[0-9]{4}"))mask="************"+raw.substring(raw.length()-4);
   else if("0301".equals(organization)&&raw.matches("[*Xx]{13}[0-9]{3}"))mask="*************"+raw.substring(raw.length()-3);
   else throw invalid(); // Unmasked numbers and unreviewed formats are not importable.
   String type=text(n,"resCardType",40,false);
   String key;
   try{key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((mask+"\n"+name+"\n"+type).getBytes(StandardCharsets.UTF_8)));}
   catch(Exception e){throw new IllegalStateException("HELD_CARD_KEY_FAILED");}
   if(!keys.add(key))throw new ApiError(422,"AMBIGUOUS_HELD_CARD_RESPONSE");
   result.add(new Held(key,name,mask,type));
  }
  return List.copyOf(result);
 }
}
