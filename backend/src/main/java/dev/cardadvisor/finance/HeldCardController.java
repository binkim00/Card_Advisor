package dev.cardadvisor.finance;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/v1/finance/connections/{connection}/held-cards")
public class HeldCardController {
 private final HeldCardService service;
 public HeldCardController(HeldCardService service){this.service=service;}
 private UUID user(HttpServletRequest r){return (UUID)r.getAttribute("userId");}
 @GetMapping public Object list(HttpServletRequest r,@PathVariable UUID connection){return service.list(user(r),connection);}
 @PostMapping("/refresh") public Object refresh(HttpServletRequest r,@PathVariable UUID connection,@RequestBody HeldCardService.Lookup input){return service.lookup(user(r),connection,input);}
 @PostMapping("/{id}/register") public Object register(HttpServletRequest r,@PathVariable UUID connection,@PathVariable UUID id,@RequestBody HeldCardService.Register input){return service.register(user(r),connection,id,input);}
}
