package dev.cardadvisor.auth;
import java.io.*;import java.util.*;import jakarta.servlet.*;import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;import org.springframework.jdbc.core.JdbcTemplate;
@Component
public class AuthFilter extends OncePerRequestFilter {
 private final JdbcTemplate db; public AuthFilter(JdbcTemplate db){this.db=db;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException {
  String path=req.getRequestURI();
  if(!path.startsWith("/v1/")||Set.of("/v1/auth/register","/v1/auth/login","/v1/auth/refresh","/v1/health","/v1/auth/password-reset/request","/v1/auth/password-reset/confirm").contains(path)){chain.doFilter(req,res);return;}
  String header=req.getHeader("Authorization");
  if(header==null||!header.startsWith("Bearer ")){deny(res);return;}
  var users=db.queryForList("SELECT user_id FROM sessions WHERE token_hash=? AND expires_at>now()",Passwords.sha(header.substring(7)));
  if(users.isEmpty()){deny(res);return;}
  req.setAttribute("userId",users.get(0).get("user_id"));chain.doFilter(req,res);
 }
 private void deny(HttpServletResponse res)throws IOException{res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"error\":{\"code\":\"SESSION_EXPIRED\"}}");}
}
