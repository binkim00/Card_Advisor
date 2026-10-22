package dev.cardadvisor.finance;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/v1/cards/{id}/qualification") public class ActualQualificationController {
 private final ActualQualificationService service;public ActualQualificationController(ActualQualificationService service){this.service=service;}
 @GetMapping public Object get(HttpServletRequest r,@PathVariable UUID id,@RequestParam String month){return service.get((UUID)r.getAttribute("userId"),id,month);}
 @PutMapping public Object save(HttpServletRequest r,@PathVariable UUID id,@RequestParam String month,@RequestBody ActualQualificationService.Input in){return service.save((UUID)r.getAttribute("userId"),id,month,in);}
}
