package dev.cardadvisor.account;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
@RestController @RequestMapping("/v1/me")
public class UserSettingsController {
 private final UserSettingsService service;
 public UserSettingsController(UserSettingsService service){this.service=service;}
 private UUID user(HttpServletRequest r){return (UUID)r.getAttribute("userId");}
 @GetMapping public Object profile(HttpServletRequest r){return service.profile(user(r));}
 @GetMapping("/preferences") public Object preferences(HttpServletRequest r){return service.preferences(user(r));}
 @PutMapping("/consents/{purpose}") public Object consent(HttpServletRequest r,@PathVariable String purpose,@RequestBody UserSettingsService.ConsentInput body){return service.consent(user(r),purpose,body);}
 @PostMapping("/deletion") public Object delete(HttpServletRequest r,@RequestBody UserSettingsService.DeleteInput body){return service.delete(user(r),body);}
}
