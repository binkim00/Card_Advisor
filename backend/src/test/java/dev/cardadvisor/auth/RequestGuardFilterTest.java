package dev.cardadvisor.auth;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class RequestGuardFilterTest {
 @Test void productionBudgetIsUnchangedAndTestScopeIsSeparate()throws Exception{for(String scope:new String[]{"","synthetic-account"}){var limits=mock(RequestLimits.class);String key="auth-ip:"+(scope.isEmpty()?"":scope+":")+"127.0.0.1";when(limits.take(key,60,60)).thenReturn(true);var filter=new RequestGuardFilter(limits,scope);var request=new MockHttpServletRequest("POST","/v1/auth/password-reset/request");request.setRemoteAddr("127.0.0.1");var response=new MockHttpServletResponse();filter.doFilter(request,response,new MockFilterChain());verify(limits).take(key,60,60);assertEquals(200,response.getStatus());}assertThrows(IllegalArgumentException.class,()->new RequestGuardFilter(mock(RequestLimits.class),"invalid scope"));}
 @Test void unknownLengthBodyIsBoundedAndAcceptedBodyIsPreserved()throws Exception{
  for(int size:new int[]{65535,65536,65537}){
   byte[] body=new byte[size];java.util.Arrays.fill(body,(byte)'x');
   var request=new MockHttpServletRequest("POST","/v1/transactions"){
    @Override public long getContentLengthLong(){return -1;} @Override public int getContentLength(){return -1;}
   };request.setContent(body);var response=new MockHttpServletResponse();var reached=new java.util.concurrent.atomic.AtomicBoolean();
   new RequestGuardFilter(mock(RequestLimits.class),"").doFilter(request,response,(r,s)->{reached.set(true);assertArrayEquals(body,r.getInputStream().readAllBytes());});
   assertEquals(size<=65536,reached.get());assertEquals(size<=65536?200:413,response.getStatus());assertEquals("no-store",response.getHeader("Cache-Control"));
  }
 }
 @Test void authLimitRejectsBeforeBodyAndUsesSafeResponse()throws Exception{
  var response=new MockHttpServletResponse();new RequestGuardFilter(mock(RequestLimits.class),"").doFilter(new MockHttpServletRequest("POST","/v1/auth/login"),response,(r,s)->fail());
  assertEquals(429,response.getStatus());assertEquals("60",response.getHeader("Retry-After"));assertEquals("{\"error\":{\"code\":\"RATE_LIMITED\"}}",response.getContentAsString());
 }
}
