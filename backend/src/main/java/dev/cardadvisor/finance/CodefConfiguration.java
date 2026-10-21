package dev.cardadvisor.finance;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*;
import java.util.Properties;
import static dev.cardadvisor.finance.CodefFailure.Kind.CONFIGURATION;

@Configuration
public class CodefConfiguration {
 @Bean public ConnectedIdCipher connectedIdCipher(@Value("${codef.storage-key-file:}") String path){return ConnectedIdCipher.fromFile(path);}
 @Bean public CodefClient codefClient(@Value("${codef.config-file:}") String path){
  var properties=new Properties();
  if(!path.isBlank()){
   try(var reader=Files.newBufferedReader(Path.of(path))){properties.load(reader);}
   catch(Exception e){throw new CodefFailure(CONFIGURATION);}
  }
  return new CodefClient(new CodefSettings(properties));
 }
}
