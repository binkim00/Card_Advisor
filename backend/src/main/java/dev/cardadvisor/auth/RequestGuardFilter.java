package dev.cardadvisor.auth;
import jakarta.servlet.*;import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;import java.util.Set;
@Component @Order(Ordered.HIGHEST_PRECEDENCE+50)
public class RequestGuardFilter extends OncePerRequestFilter {
 private final RequestLimits limits;private final String rateScope;public RequestGuardFilter(RequestLimits limits,@org.springframework.beans.factory.annotation.Value("${security.rate-limit-scope:}")String rateScope){if(!rateScope.matches("[A-Za-z0-9_-]{0,64}"))throw new IllegalArgumentException("INVALID_RATE_LIMIT_SCOPE");this.limits=limits;this.rateScope=rateScope;}
 protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws ServletException,IOException{
  if(!r.getRequestURI().startsWith("/v1/")){chain.doFilter(r,s);return;}
  s.setHeader("Cache-Control","no-store");s.setHeader("X-Content-Type-Options","nosniff");
  if(r.getContentLengthLong()>65536){reject(s,413,"REQUEST_TOO_LARGE");return;}
  if(Set.of("/v1/auth/register","/v1/auth/login","/v1/auth/refresh","/v1/auth/password-reset/request","/v1/auth/password-reset/confirm").contains(r.getRequestURI())&&!limits.take("auth-ip:"+(rateScope.isEmpty()?"":rateScope+":")+r.getRemoteAddr(),60,60)){
   s.setHeader("Retry-After","60");reject(s,429,"RATE_LIMITED");return;
  }
  chain.doFilter(r,s);
 }
 private void reject(HttpServletResponse r,int code,String error)throws IOException{r.setStatus(code);r.setContentType("application/json");r.getWriter().write("{\"error\":{\"code\":\""+error+"\"}}");}
}
