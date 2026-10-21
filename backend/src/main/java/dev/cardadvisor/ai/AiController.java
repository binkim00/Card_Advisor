package dev.cardadvisor.ai;
import org.springframework.web.bind.annotation.*;import jakarta.servlet.http.HttpServletRequest;import java.util.*;
@RestController @RequestMapping("/v1")
public class AiController {
 private final AiAssistanceService service;public AiController(AiAssistanceService service){this.service=service;}
 private UUID user(HttpServletRequest r){return (UUID)r.getAttribute("userId");}
 @PostMapping("/recommendations/{id}/explanation") public Object explain(HttpServletRequest r,@PathVariable UUID id){return service.explain(user(r),id);}
 @GetMapping("/insights") public Object insights(HttpServletRequest r,@RequestParam String month){return service.analyze(user(r),month);}
 @PostMapping("/transactions/{id}/classification-suggestion") public Object classify(HttpServletRequest r,@PathVariable UUID id){return service.classify(user(r),id);}
}
