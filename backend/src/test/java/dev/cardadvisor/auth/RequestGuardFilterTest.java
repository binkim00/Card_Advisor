package dev.cardadvisor.auth;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class RequestGuardFilterTest {
 @Test void productionBudgetIsUnchangedAndTestScopeIsSeparate()throws Exception{for(String scope:new String[]{"","synthetic-account"}){var limits=mock(RequestLimits.class);String key="auth-ip:"+(scope.isEmpty()?"":scope+":")+"127.0.0.1";when(limits.take(key,60,60)).thenReturn(true);var filter=new RequestGuardFilter(limits,scope);var request=new MockHttpServletRequest("POST","/v1/auth/password-reset/request");request.setRemoteAddr("127.0.0.1");var response=new MockHttpServletResponse();filter.doFilter(request,response,new MockFilterChain());verify(limits).take(key,60,60);assertEquals(200,response.getStatus());}assertThrows(IllegalArgumentException.class,()->new RequestGuardFilter(mock(RequestLimits.class),"invalid scope"));}
}
