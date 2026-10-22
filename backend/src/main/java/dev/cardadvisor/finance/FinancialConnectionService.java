package dev.cardadvisor.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.cardadvisor.api.ApiError;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import java.security.MessageDigest;
import java.util.*;

@Service
public class FinancialConnectionService {
 public static final String CONSENT_VERSION="FINANCE_LINK_V1";
 private final JdbcTemplate db;private final CodefClient provider;private final ConnectedIdCipher cipher;
 private final TransactionTemplate tx;private final ObjectMapper json=new ObjectMapper();
 public FinancialConnectionService(JdbcTemplate db,CodefClient provider,ConnectedIdCipher cipher,PlatformTransactionManager manager){this.db=db;this.provider=provider;this.cipher=cipher;tx=new TransactionTemplate(manager);}
 public record ConnectInput(String organization,String loginId,char[] password,Boolean consent,String consentVersion,@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) CardOwnershipInput cardOwnership){@Override public String toString(){return "ConnectInput[redacted]";}}
 public record ReauthorizeInput(Long expectedVersion,String loginId,char[] password,Boolean consent,String consentVersion,@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) CardOwnershipInput cardOwnership){@Override public String toString(){return "ReauthorizeInput[redacted]";}}
 public record DisconnectInput(Long expectedVersion){}
 private record Work(UUID user,UUID connection,UUID operation,String kind,boolean execute){ }
 private void validateLogin(String login,char[] password,Boolean consent,String version){
  if(login==null||login.isBlank()||login.length()>128||password==null||password.length<1||password.length>256)throw new ApiError(422,"INVALID_FINANCIAL_CREDENTIAL_FORMAT");
  if(!Boolean.TRUE.equals(consent)||!CONSENT_VERSION.equals(version))throw new ApiError(422,"FINANCE_CONSENT_REQUIRED");
 }
 private void lock(UUID user){if(db.queryForList("SELECT id FROM app_users WHERE id=? FOR UPDATE",user).isEmpty())throw new ApiError(401,"SESSION_EXPIRED");}
 private Map<String,Object> connection(UUID user,UUID id){var rows=db.queryForList("SELECT * FROM financial_connections WHERE user_id=? AND id=?",user,id);if(rows.isEmpty())throw new ApiError(404,"FINANCIAL_CONNECTION_NOT_FOUND");return rows.get(0);}
 private Map<String,Object> operation(UUID user,UUID id){var rows=db.queryForList("SELECT * FROM financial_connection_operations WHERE user_id=? AND id=?",user,id);if(rows.isEmpty())throw new ApiError(404,"FINANCIAL_OPERATION_NOT_FOUND");return rows.get(0);}
 private void recover(UUID user){
  // Crash recovery does not reissue an external mutation. A late result can still settle the same operation.
  db.update("UPDATE financial_connection_operations SET status='UNKNOWN',error_code='PROCESS_RESULT_UNCONFIRMED',updated_at=now() WHERE user_id=? AND status='PENDING' AND created_at<now()-interval '2 minutes'",user);
  db.update("UPDATE financial_connections c SET state='UNKNOWN',last_error='PROCESS_RESULT_UNCONFIRMED',version=version+1,updated_at=now() FROM financial_connection_operations o WHERE c.user_id=? AND o.user_id=c.user_id AND o.id=c.last_operation_id AND o.status='UNKNOWN' AND c.state IN ('CONNECTING','REAUTHENTICATING','DISCONNECTING')",user);
 }
 private Map<String,Object> publicConnection(Map<String,Object> row){
  var result=new LinkedHashMap<String,Object>();
  for(String field:List.of("id","environment","organization","state","version","consent_version","consented_at","last_operation_id","last_error","provider_code","created_at","updated_at"))result.put(field,row.get(field));
  result.put("archived",row.get("archived_at")!=null);
  result.put("hasConnectedId",row.get("cid_ciphertext")!=null);return result;
 }
 private Map<String,Object> publicOperation(Map<String,Object> row){var result=new LinkedHashMap<String,Object>();for(String field:List.of("id","connection_id","kind","status","error_code","provider_code","created_at","updated_at"))result.put(field,row.get(field));return result;}
 public List<Map<String,Object>> list(UUID user){return tx.execute(status->{lock(user);recover(user);return db.queryForList("SELECT * FROM financial_connections WHERE user_id=? AND archived_at IS NULL ORDER BY created_at,id",user).stream().map(this::publicConnection).toList();});}
 public Map<String,Object> get(UUID user,UUID id){return tx.execute(status->{lock(user);recover(user);var row=connection(user,id);if(row.get("archived_at")!=null)throw new ApiError(404,"FINANCIAL_CONNECTION_NOT_FOUND");return publicConnection(row);});}
 public Map<String,Object> getOperation(UUID user,UUID id){return tx.execute(status->{lock(user);recover(user);return publicOperation(operation(user,id));});}
 public Map<String,Object> create(UUID user,UUID key,ConnectInput input){
  try{
   validateLogin(input.loginId(),input.password(),input.consent(),input.consentVersion());
   if(input.organization()==null||!input.organization().matches("[0-9]{4}"))throw new ApiError(422,"INVALID_INSTITUTION");
   Work work=reserve(user,key,null,"CREATE",null,input.organization(),input);
   if(work.execute())execute(work,input.loginId(),input.password(),input.cardOwnership());return response(work);
  }finally{if(input.password()!=null)Arrays.fill(input.password(),'\0');if(input.cardOwnership()!=null)input.cardOwnership().close();}
 }
 public Map<String,Object> reauthorize(UUID user,UUID id,UUID key,ReauthorizeInput input){
  try{validateLogin(input.loginId(),input.password(),input.consent(),input.consentVersion());Work work=reserve(user,key,id,"REAUTHORIZE",input.expectedVersion(),null,input);
   if(work.execute())execute(work,input.loginId(),input.password(),input.cardOwnership());return response(work);
  }finally{if(input.password()!=null)Arrays.fill(input.password(),'\0');if(input.cardOwnership()!=null)input.cardOwnership().close();}
 }
 public Map<String,Object> retry(UUID user,UUID id,UUID key,ReauthorizeInput input){
  try{validateLogin(input.loginId(),input.password(),input.consent(),input.consentVersion());Work work=reserve(user,key,id,"CREATE",input.expectedVersion(),null,input);if(work.execute())execute(work,input.loginId(),input.password(),input.cardOwnership());return response(work);}
  finally{if(input.password()!=null)Arrays.fill(input.password(),'\0');if(input.cardOwnership()!=null)input.cardOwnership().close();}
 }
 public Map<String,Object> archiveFailed(UUID user,UUID id,DisconnectInput input){return tx.execute(status->{
  lock(user);recover(user);var row=connection(user,id);
  if(row.get("archived_at")!=null)return Map.of("localRecordDeleted",true,"externalDisconnectRequested",false);
  if(input.expectedVersion()==null||input.expectedVersion().longValue()!=((Number)row.get("version")).longValue())throw new ApiError(409,"VERSION_CONFLICT");
  if(!"FAILED".equals(row.get("state"))||row.get("cid_ciphertext")!=null)throw new ApiError(409,"FAILED_RECORD_NOT_REMOVABLE");
  var op=operation(user,(UUID)row.get("last_operation_id"));
  if(!"CREATE".equals(op.get("kind"))||!"FAILED".equals(op.get("status"))||db.queryForObject("SELECT count(*) FROM financial_sync_runs WHERE user_id=? AND connection_id=?",Long.class,user,id)>0)throw new ApiError(409,"FAILED_RECORD_NOT_REMOVABLE");
  db.update("UPDATE financial_connections SET archived_at=now(),version=version+1,updated_at=now() WHERE user_id=? AND id=?",user,id);
  return Map.of("localRecordDeleted",true,"externalDisconnectRequested",false);
 });}
 public Map<String,Object> disconnect(UUID user,UUID id,UUID key,DisconnectInput input){Work work=reserve(user,key,id,"DISCONNECT",input.expectedVersion(),null,input);if(work.execute())execute(work,null,null,null);return response(work);}
 private byte[] fingerprint(String keyId,String kind,UUID id,Object input){
  byte[] bytes=null;
  try{bytes=json.writeValueAsBytes(Arrays.asList(kind,id,input));return cipher.fingerprint(keyId,bytes);}
  catch(ApiError e){throw e;}catch(Exception e){throw new ApiError(422,"INVALID_FINANCE_REQUEST");}finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);}
 }
 private Work reserve(UUID user,UUID key,UUID id,String kind,Long expected,String organization,Object input){
  if(key==null)throw new ApiError(422,"IDEMPOTENCY_KEY_REQUIRED");
  return tx.execute(status->{
   lock(user);recover(user);
   // Ownership precedes idempotency lookup so a key can never expose another user's connection.
   Map<String,Object> row=id==null?null:connection(user,id);
   CardOwnershipInput ownership=input instanceof ConnectInput c?c.cardOwnership():input instanceof ReauthorizeInput r?r.cardOwnership():null;
   if(ownership!=null&&!ownership.validFor(row==null?organization:(String)row.get("organization")))throw new ApiError(422,"INVALID_KB_CARD_OWNERSHIP");
   var previous=db.queryForList("SELECT * FROM financial_connection_operations WHERE user_id=? AND idempotency_key=?",user,key);
   if(!previous.isEmpty()){
    var op=previous.get(0);byte[] hash=fingerprint((String)op.get("fingerprint_key_id"),kind,id,input);
    if(!MessageDigest.isEqual(hash,(byte[])op.get("fingerprint")))throw new ApiError(409,"IDEMPOTENCY_CONFLICT");
    return new Work(user,(UUID)op.get("connection_id"),(UUID)op.get("id"),kind,false);
   }
   cipher.requireConfigured();provider.requireAvailable();String env=provider.environment(),scope=provider.scope();
   if(row==null){
    var rows=db.queryForList("SELECT * FROM financial_connections WHERE user_id=? AND environment=? AND provider_scope=? AND organization=?",user,env,scope,organization);
    if(!rows.isEmpty())row=rows.get(0);
   }else if(!env.equals(row.get("environment"))||!scope.equals(row.get("provider_scope")))throw new ApiError(409,"PROVIDER_CONFIGURATION_CHANGED");
   if(row!=null&&db.queryForObject("SELECT count(*) FROM financial_sync_runs r LEFT JOIN financial_sync_auth a ON a.user_id=r.user_id AND a.run_id=r.id WHERE r.user_id=? AND r.connection_id=? AND r.status IN ('AUTH_REQUIRED','AUTH_PENDING','AUTH_UNKNOWN') AND COALESCE(a.expires_at,r.finished_at+interval '3 minutes')>now()",Long.class,user,row.get("id"))>0)throw new ApiError(409,"SYNC_AUTH_IN_PROGRESS");
   if(row!=null&&db.queryForObject("SELECT count(*) FROM financial_connection_operations WHERE user_id=? AND id=? AND status='AUTH_REQUIRED' AND updated_at>now()-interval '3 minutes'",Long.class,user,row.get("last_operation_id"))>0)throw new ApiError(409,"ACCOUNT_AUTH_IN_PROGRESS");
   String old=row==null?"FAILED":(String)row.get("state");
   if(kind.equals("CREATE")){
    if(id!=null&&(row.get("archived_at")!=null||!"FAILED".equals(old)||expected==null||expected.longValue()!=((Number)row.get("version")).longValue()))throw new ApiError(409,"VERSION_CONFLICT");
    if(!Set.of("FAILED","DISCONNECTED").contains(old)||row!=null&&row.get("cid_ciphertext")!=null)throw new ApiError(409,"CONNECTION_ALREADY_EXISTS_OR_REQUIRES_REVIEW");
   }else{
    if(expected==null||expected.longValue()!=((Number)row.get("version")).longValue())throw new ApiError(409,"VERSION_CONFLICT");
    if(!Set.of("ACTIVE","AUTH_REQUIRED").contains(old)||row.get("cid_ciphertext")==null)throw new ApiError(409,"CONNECTION_REQUIRES_REVIEW");
   }
   UUID cid=row==null?UUID.randomUUID():(UUID)row.get("id"),opid=UUID.randomUUID();String pending=switch(kind){case "CREATE"->"CONNECTING";case "REAUTHORIZE"->"REAUTHENTICATING";default->"DISCONNECTING";};
   if(row==null)db.update("INSERT INTO financial_connections(id,user_id,environment,provider_scope,organization,state,consent_version) VALUES (?,?,?,?,?,?,?)",cid,user,env,scope,organization,pending,CONSENT_VERSION);
   else db.update("UPDATE financial_connections SET state=?,version=version+1,last_error=NULL,provider_code=NULL,archived_at=NULL,updated_at=now() WHERE user_id=? AND id=?",pending,user,cid);
   if(row!=null&&kind.equals("CREATE"))db.update("UPDATE financial_connections SET consent_version=?,consented_at=now() WHERE user_id=? AND id=?",CONSENT_VERSION,user,cid);
   String keyId=cipher.activeKeyId();byte[] hash=fingerprint(keyId,kind,id,input);
   db.update("INSERT INTO financial_connection_operations(id,user_id,connection_id,idempotency_key,kind,fingerprint,fingerprint_key_id,status,previous_state) VALUES (?,?,?,?,?,?,?,'PENDING',?)",opid,user,cid,key,kind,hash,keyId,old);
   db.update("UPDATE financial_connections SET last_operation_id=? WHERE user_id=? AND id=?",opid,user,cid);
   return new Work(user,cid,opid,kind,true);
  });
 }
 private void execute(Work work,String login,char[] password,CardOwnershipInput ownership){
  var row=connection(work.user(),work.connection());CodefClient.AccountResult result;
  try{
   String organization=(String)row.get("organization");String cid=null;
   if(!work.kind().equals("CREATE"))cid=cipher.decrypt(new ConnectedIdCipher.Sealed((String)row.get("key_id"),(byte[])row.get("cid_nonce"),(byte[])row.get("cid_ciphertext")),work.user(),work.connection(),(String)row.get("environment"),(String)row.get("provider_scope"),organization);
   if(work.kind().equals("DISCONNECT"))result=provider.delete(cid,organization);
   else try(var credential=new CodefClient.CardLogin(organization,login,password,ownership)){result=work.kind().equals("CREATE")?provider.create(credential):provider.update(cid,credential);}
  }catch(CodefFailure e){
   String outcome=switch(e.kind){case UNKNOWN_OUTCOME->"UNKNOWN";case AUTH_REQUIRED->"AUTH_REQUIRED";default->"FAILED";};
   finish(work,null,outcome,"CODEF_"+e.kind.name(),e.providerCode);return;
  }catch(ApiError e){finish(work,null,"UNKNOWN","FINANCE_CREDENTIAL_UNAVAILABLE");return;}
  catch(Exception e){finish(work,null,"UNKNOWN","PROVIDER_RESULT_UNCONFIRMED");return;}
  String outcome=result.status==CodefClient.AccountResult.Status.AUTH_REQUIRED?"AUTH_REQUIRED":result.status==CodefClient.AccountResult.Status.SUCCEEDED?"SUCCEEDED":result.status==CodefClient.AccountResult.Status.PARTIAL||result.connectedId()!=null?"UNKNOWN":"FAILED";
  try{finish(work,result,outcome,outcome.equals("SUCCEEDED")?null:"PROVIDER_ACCOUNT_RESULT_REQUIRES_REVIEW");}
  catch(DataIntegrityViolationException e){finish(work,null,"UNKNOWN","PROVIDER_ID_OWNERSHIP_CONFLICT");}
  catch(ApiError e){finish(work,null,"UNKNOWN","FINANCE_RESULT_STORAGE_UNCONFIRMED");}
 }
 private void finish(Work work,CodefClient.AccountResult result,String outcome,String error){finish(work,result,outcome,error,result==null?null:result.providerCode);}
 private void finish(Work work,CodefClient.AccountResult result,String outcome,String error,String providerCode){
  String safeProviderCode=CodefFailure.safeCode(providerCode);
  tx.executeWithoutResult(status->{
   lock(work.user());var row=connection(work.user(),work.connection());var operation=operation(work.user(),work.operation());
   if(!work.operation().equals(row.get("last_operation_id"))||!Set.of("PENDING","UNKNOWN").contains(operation.get("status")))throw new ApiError(409,"OPERATION_SUPERSEDED");
   String state;
   if(outcome.equals("SUCCEEDED"))state=work.kind().equals("DISCONNECT")?"DISCONNECTED":"ACTIVE";
   else if(outcome.equals("UNKNOWN")||outcome.equals("AUTH_REQUIRED"))state=outcome;
   else state=work.kind().equals("CREATE")?"FAILED":(String)operation.get("previous_state");
   if(state.equals("DISCONNECTED"))db.update("UPDATE financial_connections SET key_id=NULL,cid_nonce=NULL,cid_ciphertext=NULL,cid_hash=NULL WHERE user_id=? AND id=?",work.user(),work.connection());
   else if(result!=null&&result.connectedId()!=null){
    String env=(String)row.get("environment"),scope=(String)row.get("provider_scope"),organization=(String)row.get("organization");
    var sealed=cipher.encrypt(result.connectedId(),work.user(),work.connection(),env,scope,organization);
    db.update("UPDATE financial_connections SET key_id=?,cid_nonce=?,cid_ciphertext=?,cid_hash=? WHERE user_id=? AND id=?",sealed.keyId(),sealed.nonce(),sealed.ciphertext(),ConnectedIdCipher.ownershipHash(env,scope,result.connectedId()),work.user(),work.connection());
   }
   db.update("UPDATE financial_connections SET state=?,last_error=?,provider_code=?,version=version+1,updated_at=now() WHERE user_id=? AND id=?",state,error,safeProviderCode,work.user(),work.connection());
   if(work.kind().equals("REAUTHORIZE")&&outcome.equals("SUCCEEDED"))db.update("UPDATE financial_connections SET consent_version=?,consented_at=now() WHERE user_id=? AND id=?",CONSENT_VERSION,work.user(),work.connection());
   db.update("UPDATE financial_connection_operations SET status=?,error_code=?,provider_code=?,updated_at=now() WHERE user_id=? AND id=?",outcome,error,safeProviderCode,work.user(),work.operation());
  });
 }
 private Map<String,Object> response(Work work){return tx.execute(status->{lock(work.user());recover(work.user());return Map.of("connection",publicConnection(connection(work.user(),work.connection())),"operation",publicOperation(operation(work.user(),work.operation())),"replayed",!work.execute());});}
}
