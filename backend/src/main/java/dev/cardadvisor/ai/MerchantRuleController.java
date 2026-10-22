package dev.cardadvisor.ai;
import org.springframework.web.bind.annotation.*;import jakarta.servlet.http.HttpServletRequest;import java.util.UUID;
@RestController @RequestMapping("/v1/transactions/{id}/classification-rule") public class MerchantRuleController {
 private final MerchantRuleService service;public MerchantRuleController(MerchantRuleService service){this.service=service;}
 @GetMapping public Object get(HttpServletRequest request,@PathVariable UUID id){return service.get((UUID)request.getAttribute("userId"),id);}
 @PutMapping public Object put(HttpServletRequest request,@PathVariable UUID id,@RequestBody MerchantRuleService.Input in){return service.save((UUID)request.getAttribute("userId"),id,in);}
}
