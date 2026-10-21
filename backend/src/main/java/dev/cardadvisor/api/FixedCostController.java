package dev.cardadvisor.api;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/v1/fixed-costs")
public class FixedCostController {
 private final FixedCostService service;
 public FixedCostController(FixedCostService service){this.service=service;}
 private UUID user(HttpServletRequest r){return (UUID)r.getAttribute("userId");}
 @GetMapping public Object view(HttpServletRequest r,@RequestParam String month){return service.view(user(r),month);}
 @PostMapping public Object save(HttpServletRequest r,@RequestBody FixedCostService.PlanInput body){return service.save(user(r),body);}
 @PutMapping("/{id}/months/{month}/links") public Object link(HttpServletRequest r,@PathVariable UUID id,@PathVariable String month,@RequestBody FixedCostService.LinkInput body){return service.link(user(r),id,month,body);}
}
