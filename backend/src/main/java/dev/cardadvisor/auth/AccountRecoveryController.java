package dev.cardadvisor.auth;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
@RestController @RequestMapping("/v1") public class AccountRecoveryController {
 private final AccountRecoveryService service;public AccountRecoveryController(AccountRecoveryService s){service=s;}
 public record Email(String email){} public record ActionToken(String token){} public record Reset(String token,String newPassword){}
 private UUID user(HttpServletRequest r){return (UUID)r.getAttribute("userId");}
 @GetMapping("/me/email-verification") public Object status(HttpServletRequest r){return service.status(user(r));}
 @PostMapping("/me/email-verification/request") public Object request(HttpServletRequest r){return service.requestVerification(user(r));}
 @PostMapping("/me/email-verification/confirm") public Object verify(HttpServletRequest r,@RequestBody ActionToken in){return service.verify(user(r),in.token);}
 @PostMapping("/auth/password-reset/request") public Object resetRequest(@RequestBody Email in){return service.requestReset(in.email);}
 @PostMapping("/auth/password-reset/confirm") public Object reset(@RequestBody Reset in){return service.reset(in.token,in.newPassword);}
}
