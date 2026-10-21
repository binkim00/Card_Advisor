package dev.cardadvisor.api;
import dev.cardadvisor.auth.Passwords;import java.util.*;import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.annotation.Transactional;
@RestController @RequestMapping("/v1/auth")
public class AuthController {
 private final JdbcTemplate db;private final dev.cardadvisor.auth.SessionService sessions;private final dev.cardadvisor.auth.RequestLimits limits;public AuthController(JdbcTemplate db,dev.cardadvisor.auth.SessionService sessions,dev.cardadvisor.auth.RequestLimits limits){this.db=db;this.sessions=sessions;this.limits=limits;}
 public record Credentials(String email,String password){}
 private void validate(Credentials c){if(c.email==null||!c.email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")||c.email.length()>254||c.password==null||c.password.length()<12||c.password.length()>128)throw new ApiError(422,"INVALID_CREDENTIAL_FORMAT");}
 @PostMapping("/register") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @Transactional
 public Map<String,Object> register(@RequestBody Credentials c){validate(c);limits.require("register:"+c.email.toLowerCase(Locale.ROOT),5,3600);UUID id=UUID.randomUUID();db.update("INSERT INTO app_users(id,email,password_hash) VALUES (?,?,?)",id,c.email.toLowerCase(Locale.ROOT),Passwords.hash(c.password));return session(id);}
 @PostMapping("/login")
 public Map<String,Object> login(@RequestBody Credentials c){validate(c);limits.require("login:"+c.email.toLowerCase(Locale.ROOT),10,60);var rows=db.queryForList("SELECT id,password_hash FROM app_users WHERE email=?",c.email.toLowerCase(Locale.ROOT));
 if(rows.isEmpty()||!Passwords.verify(c.password,(String)rows.get(0).get("password_hash")))throw new ApiError(401,"INVALID_CREDENTIALS");return session((UUID)rows.get(0).get("id"));}
 @PostMapping("/logout") public Map<String,Boolean> logout(HttpServletRequest r){sessions.logout(r.getHeader("Authorization").substring(7));return Map.of("loggedOut",true);}
 public record RefreshInput(String refreshToken){}
 @PostMapping("/refresh") public Map<String,Object> refresh(@RequestBody RefreshInput in){var result=sessions.rotate(in.refreshToken());if(result.containsKey("rejected"))throw new ApiError(401,"REFRESH_REJECTED");return result;}
 private Map<String,Object> session(UUID id){return sessions.issue(id);}
}
