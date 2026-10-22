package dev.cardadvisor.auth;
import dev.cardadvisor.api.ApiError;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.nio.file.*;
import java.util.Properties;
@Component public class SmtpAccountMail implements AccountMailGateway {
 private final JavaMailSenderImpl sender;private final String from;
 @org.springframework.beans.factory.annotation.Autowired
 public SmtpAccountMail(@Value("${account.mail-config-file:}") String path){
  JavaMailSenderImpl configured=null;String address=null;
  if(!path.isBlank())try(var stream=Files.newInputStream(Path.of(path))){var p=new Properties();p.load(stream);if(Boolean.parseBoolean(p.getProperty("enabled","false"))){
   String host=p.getProperty("host","").trim();address=p.getProperty("from","").trim();int port=Integer.parseInt(p.getProperty("port","587"));if(host.isBlank()||!address.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")||!java.util.Set.of(465,587).contains(port))throw new IllegalArgumentException();
   configured=new JavaMailSenderImpl();configured.setHost(host);configured.setPort(port);configured.setUsername(p.getProperty("username",""));configured.setPassword(p.getProperty("password",""));configured.setDefaultEncoding("UTF-8");var props=configured.getJavaMailProperties();props.put("mail.smtp.auth","true");props.put("mail.smtp.connectiontimeout","5000");props.put("mail.smtp.timeout","5000");props.put("mail.smtp.writetimeout","5000");props.put("mail.smtp.ssl.checkserveridentity","true");props.put("mail.debug","false");if(port==465)props.put("mail.smtp.ssl.enable","true");else{props.put("mail.smtp.starttls.enable","true");props.put("mail.smtp.starttls.required","true");}
  }}catch(Exception e){throw new IllegalStateException("ACCOUNT_MAIL_CONFIGURATION_INVALID");}
  sender=configured;from=address;
 }
 SmtpAccountMail(JavaMailSenderImpl testSender,String testFrom){sender=testSender;from=testFrom;}
 public boolean ready(){return sender!=null;}
 public void send(String recipient,String purpose,String token){if(!ready())throw new ApiError(503,"ACCOUNT_MAIL_UNAVAILABLE");try{if(!java.util.Set.of("VERIFY_EMAIL","RESET_PASSWORD").contains(purpose)||recipient==null||recipient.contains("\r")||recipient.contains("\n")||token==null||token.contains("\r")||token.contains("\n"))throw new IllegalArgumentException();var message=new SimpleMailMessage();message.setFrom(from);message.setTo(recipient);message.setSubject(purpose.equals("VERIFY_EMAIL")?"카드 어드바이저 이메일 확인":"카드 어드바이저 비밀번호 재설정");message.setText("본인이 요청한 경우에만 앱의 확인 코드 입력창에 아래 코드를 입력하세요. 15분 동안 한 번만 사용할 수 있습니다.\n\n"+token+"\n\n요청하지 않았다면 입력하거나 공유하지 마세요.");sender.send(message);}catch(Exception e){throw new ApiError(503,"ACCOUNT_MAIL_DELIVERY_FAILED");}}
}
