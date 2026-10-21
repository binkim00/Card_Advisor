package dev.cardadvisor.account;
import dev.cardadvisor.domain.ActualCardRules;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
@RestController @RequestMapping("/v1")
public class CardCatalogController {
 private final CardCatalogService service;public CardCatalogController(CardCatalogService service){this.service=service;}
 @GetMapping("/card-products") public Object products(){return ActualCardRules.PRODUCTS;}
 @PostMapping("/cards/from-product") public Object register(HttpServletRequest r,@RequestBody CardCatalogService.Register in){return service.register((UUID)r.getAttribute("userId"),in);}
}
