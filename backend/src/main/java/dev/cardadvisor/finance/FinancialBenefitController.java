package dev.cardadvisor.finance;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/v1") public class FinancialBenefitController {
 private final FinancialBenefitService service;public FinancialBenefitController(FinancialBenefitService s){service=s;}
 private UUID user(HttpServletRequest r){return (UUID)r.getAttribute("userId");}
 @GetMapping("/finance/transactions/{id}/benefit-review") public Object get(HttpServletRequest r,@PathVariable UUID id){return service.get(user(r),id);}
 @PutMapping("/finance/transactions/{id}/benefit-review") public Object save(HttpServletRequest r,@PathVariable UUID id,@RequestBody FinancialBenefitService.Input in){return service.save(user(r),id,in);}
 @GetMapping("/cards/{id}/financial-benefits") public Object summary(HttpServletRequest r,@PathVariable UUID id,@RequestParam String month){return service.summary(user(r),id,month);}
 @PutMapping("/cards/{id}/financial-benefits") public Object month(HttpServletRequest r,@PathVariable UUID id,@RequestParam String month,@RequestBody FinancialBenefitService.MonthInput in){return service.saveMonth(user(r),id,month,in);}
}
