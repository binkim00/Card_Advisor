package dev.cardadvisor.finance;
import dev.cardadvisor.api.ApiError;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.jdbc.core.JdbcTemplate;import com.fasterxml.jackson.databind.ObjectMapper;import java.util.*;
@Service
public class FinancialReconciliationService{
 private final JdbcTemplate db;private final CodefClient provider;private final ConnectedIdCipher cipher;private final ObjectMapper json;
 public FinancialReconciliationService(JdbcTemplate d,CodefClient p,ConnectedIdCipher c,ObjectMapper j){db=d;provider=p;cipher=c;json=j;}
 public record Input(Long expectedVersion){}
 @Transactional public Map<String,Object> check(UUID user,UUID id,UUID request,Input input){
  if(request==null||input.expectedVersion()==null)throw new ApiError(422,"INVALID_RECONCILIATION");
  if(db.queryForList("SELECT id FROM app_users WHERE id=? FOR UPDATE",user).isEmpty())throw new ApiError(401,"SESSION_EXPIRED");
  var rows=db.queryForList("SELECT * FROM financial_connections WHERE user_id=? AND id=? AND archived_at IS NULL",user,id);if(rows.isEmpty())throw new ApiError(404,"FINANCIAL_CONNECTION_NOT_FOUND");
  var old=db.queryForList("SELECT * FROM financial_connection_checks WHERE user_id=? AND request_id=?",user,request);
  if(!old.isEmpty()){var row=old.get(0);if(!id.equals(row.get("connection_id"))||input.expectedVersion()!=((Number)row.get("expected_version")).longValue())throw new ApiError(409,"IDEMPOTENCY_CONFLICT");try{return json.readValue(row.get("result").toString(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}catch(Exception e){throw new IllegalStateException("STORED_CHECK_INVALID");}}
  var c=rows.get(0);if(input.expectedVersion()!=((Number)c.get("version")).longValue())throw new ApiError(409,"VERSION_CONFLICT");
  if(!Set.of("UNKNOWN","AUTH_REQUIRED").contains(c.get("state")))throw new ApiError(409,"CONNECTION_NOT_PENDING_REVIEW");
  if(!provider.environment().equals(c.get("environment"))||!provider.scope().equals(c.get("provider_scope")))throw new ApiError(409,"PROVIDER_CONFIGURATION_CHANGED");
  String decision="PROVIDER_SUPPORT_REQUIRED",next=(String)c.get("state"),diagnostic=null;boolean queried=false;
  if(c.get("cid_ciphertext")!=null){
   try{
    String cid=cipher.decrypt(new ConnectedIdCipher.Sealed((String)c.get("key_id"),(byte[])c.get("cid_nonce"),(byte[])c.get("cid_ciphertext")),user,id,(String)c.get("environment"),(String)c.get("provider_scope"),(String)c.get("organization"));
    queried=true;boolean present=provider.registered(cid,(String)c.get("organization"));
    String kind=db.queryForObject("SELECT kind FROM financial_connection_operations WHERE user_id=? AND id=?",String.class,user,c.get("last_operation_id"));
    if(!present&&"DISCONNECT".equals(kind)){
     next="DISCONNECTED";decision="DISCONNECTION_CONFIRMED";
     db.update("UPDATE financial_connections SET key_id=NULL,cid_nonce=NULL,cid_ciphertext=NULL,cid_hash=NULL WHERE user_id=? AND id=?",user,id);
     db.update("UPDATE financial_connection_operations SET status='SUCCEEDED',error_code=NULL,provider_code=NULL,updated_at=now() WHERE user_id=? AND id=?",user,c.get("last_operation_id"));
    }else if(present){next="AUTH_REQUIRED";decision="REGISTRATION_PRESENT_REAUTH_REQUIRED";}
    else decision="REGISTRATION_ABSENT_SUPPORT_REQUIRED";
   }catch(CodefFailure e){decision="PROVIDER_CHECK_UNAVAILABLE";diagnostic=e.providerCode;}catch(ApiError e){decision="STORED_ID_UNAVAILABLE";}
  }
  if(!next.equals(c.get("state")))db.update("UPDATE financial_connections SET state=?,last_error=?,version=version+1,updated_at=now() WHERE user_id=? AND id=?",next,"DISCONNECTED".equals(next)?null:decision,user,id);
  var result=new LinkedHashMap<String,Object>();result.put("decision",decision);result.put("state",next);result.put("version",((Number)c.get("version")).longValue()+(next.equals(c.get("state"))?0:1));result.put("providerQueried",queried);result.put("providerCode",diagnostic);result.put("credentialsVerified",false);
  try{db.update("INSERT INTO financial_connection_checks(user_id,request_id,connection_id,expected_version,result) VALUES (?,?,?,?,?::jsonb)",user,request,id,input.expectedVersion(),json.writeValueAsString(result));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("CHECK_ENCODING_FAILED");}
  return result;
 }
}
