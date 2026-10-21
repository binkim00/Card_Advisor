package dev.cardadvisor.finance;
import dev.cardadvisor.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/v1/finance")
public class FinancialSyncController {
 private final FinancialSyncService service;
 public FinancialSyncController(FinancialSyncService service){this.service=service;}
 private UUID user(HttpServletRequest r){return (UUID)r.getAttribute("userId");}
 @PostMapping("/connections/{id}/sync") public Object sync(HttpServletRequest r,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody FinancialSyncService.Input in){UUID k;try{k=UUID.fromString(key);}catch(Exception e){throw new ApiError(422,"IDEMPOTENCY_KEY_REQUIRED");}return service.sync(user(r),id,k,in);}
 @GetMapping("/sync-runs/{id}") public Object run(HttpServletRequest r,@PathVariable UUID id){return service.run(user(r),id);}
 @GetMapping("/connections/{id}/sync-runs") public Object runs(HttpServletRequest r,@PathVariable UUID id){return service.runs(user(r),id);}
 @GetMapping("/connections/{id}/transactions") public Object list(HttpServletRequest r,@PathVariable UUID id){return service.transactions(user(r),id);}
 @PostMapping("/transactions/{id}/resolve") public Object resolve(HttpServletRequest r,@PathVariable UUID id,@RequestBody FinancialSyncService.Resolution in){return service.resolve(user(r),id,in);}
}
