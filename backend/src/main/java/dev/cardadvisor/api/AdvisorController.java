package dev.cardadvisor.api;
import dev.cardadvisor.domain.RecommendationEngine.*;import dev.cardadvisor.api.AdvisorService.*;
import jakarta.servlet.http.*;import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/v1")
public class AdvisorController {
 private final AdvisorService service;public AdvisorController(AdvisorService service){this.service=service;}
 private UUID user(HttpServletRequest r){return (UUID)r.getAttribute("userId");}
 @GetMapping("/health") public Map<String,Object> health(){return Map.of("status","UP","mode","LOCAL_DEVELOPMENT","realFinancialIntegration",false);}
 @GetMapping("/dashboard") public Object dashboard(HttpServletRequest r,@RequestParam String month){return service.dashboard(user(r),month);}
 @GetMapping("/cards") public Object cards(HttpServletRequest r){return service.cards(user(r));}
 @GetMapping("/cards/performance") public Object performance(HttpServletRequest r,@RequestParam String month){return service.performance(user(r),month);}
 @PutMapping("/cards/{id}/months/{month}") public Object state(HttpServletRequest r,@PathVariable UUID id,@PathVariable String month,@RequestBody MonthStateInput body){return service.saveMonthState(user(r),id,month,body);}
 @PostMapping("/demo/setup") public Object demo(HttpServletRequest r){return service.demo(user(r));}
 @DeleteMapping("/me") public Object delete(HttpServletRequest r,@RequestHeader("X-Confirm-Delete") String confirm){if(!"DELETE_ACCOUNT".equals(confirm))throw new ApiError(422,"CONFIRMATION_REQUIRED");return service.deleteUser(user(r));}
 @PostMapping("/cards") public Object card(HttpServletRequest r,@RequestBody Card c){return service.saveCard(user(r),c);}
 @GetMapping("/budgets/{month}") public Object budget(HttpServletRequest r,@PathVariable String month){return service.budget(user(r),month);}
 @PutMapping("/budgets/{month}") public Object saveBudget(HttpServletRequest r,@PathVariable String month,@RequestBody BudgetInput b){return service.saveBudget(user(r),month,b);}
 @GetMapping("/transactions") public Object entries(HttpServletRequest r,@RequestParam String month){return service.entries(user(r),month);}
 @GetMapping("/transactions/{id}") public Object entryDetail(HttpServletRequest r,@PathVariable UUID id){return service.entry(user(r),id);}
 @RequestMapping(value="/transactions/{id}/classification",method={RequestMethod.PUT,RequestMethod.PATCH}) public Object classify(HttpServletRequest r,@PathVariable UUID id,@RequestBody ClassificationInput body){return service.classify(user(r),id,body);}
 @GetMapping("/transactions/{id}/classification-history") public Object history(HttpServletRequest r,@PathVariable UUID id){return service.classificationHistory(user(r),id);}
 @GetMapping("/budgets/{month}/summary") public Object budgetSummary(HttpServletRequest r,@PathVariable String month){return service.budgetSummary(user(r),month);}
 @PostMapping("/transactions/manual") public Object entry(HttpServletRequest r,@RequestBody EntryInput e){return service.addEntry(user(r),e);}
 @PutMapping("/transactions/{id}/card") public Object link(HttpServletRequest r,@PathVariable UUID id,@RequestBody LinkCardInput body){return service.linkCard(user(r),id,body);}
 @PostMapping("/transactions/{id}/cancellations") public Object cancel(HttpServletRequest r,@PathVariable UUID id,@RequestBody CancelInput c){return service.cancel(user(r),id,c);}
 @PostMapping("/checkout-sessions") public Object checkout(HttpServletRequest r,@RequestBody Checkout c){return service.checkout(user(r),c);}
 @PutMapping("/checkout-sessions/{id}") public Object update(HttpServletRequest r,@PathVariable UUID id,@RequestBody UpdateCheckout c){return service.updateCheckout(user(r),id,c);}
 @PostMapping("/recommendations") public Object recommend(HttpServletRequest r,@RequestBody RecommendInput i){return service.recommend(user(r),i);}
 @GetMapping("/recommendations/{id}") public Object recommendation(HttpServletRequest r,@PathVariable UUID id){return service.recommendation(user(r),id);}
}
