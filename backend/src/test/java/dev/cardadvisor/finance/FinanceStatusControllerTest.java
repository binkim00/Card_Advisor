package dev.cardadvisor.finance;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Properties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import dev.cardadvisor.auth.AuthFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class FinanceStatusControllerTest {
 @Test void statusIsExplicitlyDisabledAndUnverified()throws Exception{
  var db=mock(JdbcTemplate.class);
  when(db.queryForList(anyString(),any(Object.class))).thenReturn(List.of(Map.of("user_id",UUID.randomUUID())));
  var mvc=MockMvcBuilders.standaloneSetup(new FinanceStatusController(new CodefClient(new CodefSettings(new Properties())),new ConnectedIdCipher(new Properties()),new org.springframework.mock.env.MockEnvironment())).addFilters(new AuthFilter(db)).build();
  mvc.perform(get("/v1/finance/codef/status")).andExpect(status().isUnauthorized());
  mvc.perform(get("/v1/finance/codef/status").header("Authorization","Bearer synthetic-app-token")).andExpect(status().isOk()).andExpect(jsonPath("$.provider").value("CODEF"))
   .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.realConnectionVerified").value(false))
   .andExpect(jsonPath("$.userConnectionApiReady").value(false)).andExpect(jsonPath("$.clientSecret").doesNotExist());
 }
}
