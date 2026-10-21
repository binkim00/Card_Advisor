package dev.cardadvisor.finance;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/v1/finance/connections/{id}/card-mappings")
public class FinancialCardMappingController {
 private final FinancialCardMappingService service;
 public FinancialCardMappingController(FinancialCardMappingService service){this.service=service;}
 @GetMapping public Object list(HttpServletRequest r,@PathVariable UUID id){return service.list((UUID)r.getAttribute("userId"),id);}
 @PutMapping public Object save(HttpServletRequest r,@PathVariable UUID id,@RequestBody FinancialCardMappingService.Input in){return service.save((UUID)r.getAttribute("userId"),id,in);}
}
