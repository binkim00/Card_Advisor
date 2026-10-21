package dev.cardadvisor.account;

import dev.cardadvisor.api.*;
import dev.cardadvisor.auth.Passwords;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @Transactional
public class UserSettingsService {
 public static final String POLICY="2026-10-19-v1";
 private final JdbcTemplate db; private final AdvisorService advisor;
 public UserSettingsService(JdbcTemplate db,AdvisorService advisor){this.db=db;this.advisor=advisor;}
 public Map<String,Object> profile(UUID u){
  var users=db.queryForList("SELECT email,created_at FROM app_users WHERE id=?",u);
  if(users.isEmpty())throw new ApiError(401,"SESSION_EXPIRED");
  var result=new LinkedHashMap<String,Object>(users.get(0));
  result.put("preferences",preferences(u));return result;
 }
 public Map<String,Object> preferences(UUID u){
  var versions=db.queryForList("SELECT version FROM user_preferences WHERE user_id=?",u);
  var consents=db.queryForList("SELECT purpose,accepted,policy_version,updated_at FROM user_consents WHERE user_id=? ORDER BY purpose",u);
  return Map.of("version",versions.isEmpty()?0L:versions.get(0).get("version"),"policyVersion",POLICY,"consents",consents);
 }
 public boolean allowed(UUID u,String purpose){
  return db.queryForObject("SELECT count(*) FROM user_consents WHERE user_id=? AND purpose=? AND accepted=true AND policy_version=?",Long.class,u,purpose,POLICY)==1;
 }
 public record ConsentInput(Boolean accepted,String policyVersion,Long expectedVersion){}
 public Map<String,Object> consent(UUID u,String purpose,ConsentInput input){
  if(!Set.of("AI","CHECKOUT_DETECTION").contains(purpose)||input.accepted()==null||!POLICY.equals(input.policyVersion())||input.expectedVersion()==null)throw new ApiError(422,"CONSENT_POLICY_REQUIRED");
  db.queryForObject("SELECT revision FROM app_users WHERE id=? FOR UPDATE",Long.class,u);
  db.update("INSERT INTO user_preferences(user_id) VALUES (?) ON CONFLICT DO NOTHING",u);
  long version=db.queryForObject("SELECT version FROM user_preferences WHERE user_id=?",Long.class,u);
  if(version!=input.expectedVersion())throw new ApiError(409,"VERSION_CONFLICT");
  var old=db.queryForList("SELECT accepted,policy_version FROM user_consents WHERE user_id=? AND purpose=?",u,purpose);
  if(old.size()==1&&Objects.equals(old.get(0).get("accepted"),input.accepted())&&POLICY.equals(old.get(0).get("policy_version")))return preferences(u);
  db.update("INSERT INTO user_consents(user_id,purpose,accepted,policy_version) VALUES (?,?,?,?) ON CONFLICT(user_id,purpose) DO UPDATE SET accepted=EXCLUDED.accepted,policy_version=EXCLUDED.policy_version,updated_at=now()",u,purpose,input.accepted(),POLICY);
  db.update("INSERT INTO consent_events(user_id,purpose,accepted,policy_version) VALUES (?,?,?,?)",u,purpose,input.accepted(),POLICY);
  db.update("UPDATE user_preferences SET version=version+1 WHERE user_id=?",u);
  db.update("UPDATE app_users SET revision=revision+1 WHERE id=?",u);
  return preferences(u);
 }
 public record DeleteInput(String password,String confirmation){}
 public Object delete(UUID u,DeleteInput input){
  if(input.password()==null||input.password().length()>128||!"계정 삭제".equals(input.confirmation()))throw new ApiError(422,"DELETE_CONFIRMATION_REQUIRED");
  String hash=db.queryForObject("SELECT password_hash FROM app_users WHERE id=? FOR UPDATE",String.class,u);
  if(!Passwords.verify(input.password(),hash))throw new ApiError(401,"REAUTHENTICATION_FAILED");
  return advisor.deleteUser(u);
 }
}
