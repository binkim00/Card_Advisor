package dev.cardadvisor.auth;
import dev.cardadvisor.api.ApiError;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class SessionService {
 private final JdbcTemplate db;public SessionService(JdbcTemplate db){this.db=db;}
 @Transactional public Map<String,Object> issue(UUID user){db.queryForObject("SELECT revision FROM app_users WHERE id=? FOR UPDATE",Long.class,user);return issue(user,UUID.randomUUID());}
 private Map<String,Object> issue(UUID user,UUID family){
  String access=Passwords.randomToken(),refresh=Passwords.randomToken();
  db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES (?,?,now()+interval '1 hour')",Passwords.sha(access),user);
  db.update("INSERT INTO refresh_sessions(token_hash,user_id,family_id,access_token_hash,expires_at) VALUES (?,?,?,?,now()+interval '30 days')",Passwords.sha(refresh),user,family,Passwords.sha(access));
  return Map.of("userId",user,"accessToken",access,"refreshToken",refresh,"expiresInSeconds",3600);
 }
 /** Return the rejection after committing family revocation, so rollback cannot restore a reused token. */
 @Transactional public Map<String,Object> rotate(String raw){
  if(raw==null||raw.length()>128)return Map.of("rejected",true);
  var owners=db.queryForList("SELECT user_id FROM refresh_sessions WHERE token_hash=?",Passwords.sha(raw));
  if(owners.isEmpty())return Map.of("rejected",true);
  db.queryForObject("SELECT revision FROM app_users WHERE id=? FOR UPDATE",Long.class,owners.get(0).get("user_id"));
  var rows=db.queryForList("SELECT *,expires_at>now() AS live FROM refresh_sessions WHERE token_hash=? FOR UPDATE",Passwords.sha(raw));
  if(rows.isEmpty())return Map.of("rejected",true);
  var old=rows.get(0);UUID family=(UUID)old.get("family_id");
  if(old.get("used_at")!=null||old.get("revoked_at")!=null||!Boolean.TRUE.equals(old.get("live"))){revoke(family);return Map.of("rejected",true);}
  db.update("UPDATE refresh_sessions SET used_at=now() WHERE token_hash=?",Passwords.sha(raw));
  db.update("DELETE FROM sessions WHERE token_hash=?",old.get("access_token_hash"));
  return issue((UUID)old.get("user_id"),family);
 }
 @Transactional public void logout(String access){
  String hash=Passwords.sha(access);
  for(var r:db.queryForList("SELECT DISTINCT family_id FROM refresh_sessions WHERE access_token_hash=?",hash))revoke((UUID)r.get("family_id"));
  db.update("DELETE FROM sessions WHERE token_hash=?",hash);
 }
 private void revoke(UUID family){
  db.update("DELETE FROM sessions WHERE token_hash IN(SELECT access_token_hash FROM refresh_sessions WHERE family_id=?)",family);
  db.update("UPDATE refresh_sessions SET revoked_at=now() WHERE family_id=?",family);
 }
}
