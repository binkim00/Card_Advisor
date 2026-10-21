package dev.cardadvisor.finance;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
@RestController @RequestMapping("/v1")
public class FinancialPerformanceController {
 private final FinancialPerformanceService service;public FinancialPerformanceController(FinancialPerformanceService service){this.service=service;}
 private UUID user(HttpServletRequest r){return (UUID)r.getAttribute("userId");}
 @GetMapping("/finance/transactions/{id}/performance-review") public Object get(HttpServletRequest r,@PathVariable UUID id){return service.get(user(r),id);}
 @PutMapping("/finance/transactions/{id}/performance-review") public Object save(HttpServletRequest r,@PathVariable UUID id,@RequestBody FinancialPerformanceService.Input in){return service.save(user(r),id,in);}
 @GetMapping("/cards/{id}/financial-performance") public Object summary(HttpServletRequest r,@PathVariable UUID id,@RequestParam String month){return service.summary(user(r),id,month);}
}
