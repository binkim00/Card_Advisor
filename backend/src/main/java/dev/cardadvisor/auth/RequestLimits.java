package dev.cardadvisor.auth;
import dev.cardadvisor.api.ApiError;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.sql.Timestamp;
@Service
public class RequestLimits {
 private final JdbcTemplate db;public RequestLimits(JdbcTemplate db){this.db=db;}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public boolean take(String key,int maximum,int windowSeconds){
  long window=Instant.now().getEpochSecond()/windowSeconds;
  int count=db.queryForObject("INSERT INTO request_limits(bucket_hash,attempts,expires_at) VALUES (?,1,?) ON CONFLICT(bucket_hash) DO UPDATE SET attempts=LEAST(request_limits.attempts+1,1000000) RETURNING attempts",Integer.class,Passwords.sha(key+":"+window),Timestamp.from(Instant.ofEpochSecond((window+2)*windowSeconds)));
  if(count==1)db.update("DELETE FROM request_limits WHERE expires_at<now()");
  return count<=maximum;
 }
 public void require(String key,int maximum,int seconds){if(!take(key,maximum,seconds))throw new ApiError(429,"RATE_LIMITED");}
}
