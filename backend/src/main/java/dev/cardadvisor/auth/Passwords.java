package dev.cardadvisor.auth;
import java.security.*;import java.util.*;import javax.crypto.*;import javax.crypto.spec.*;
public final class Passwords {
 private static final SecureRandom RANDOM=new SecureRandom();
 public static String randomToken(){byte[] b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 public static String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 public static String hash(String password){byte[] salt=new byte[16];RANDOM.nextBytes(salt);return HexFormat.of().formatHex(salt)+":"+derive(password,salt);}
 public static boolean verify(String password,String stored){String[] parts=stored.split(":");return MessageDigest.isEqual(HexFormat.of().parseHex(parts[1]),HexFormat.of().parseHex(derive(password,HexFormat.of().parseHex(parts[0]))));}
 private static String derive(String password,byte[] salt){PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,210000,256);try{return HexFormat.of().formatHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());}catch(Exception e){throw new IllegalStateException(e);}finally{spec.clearPassword();}}
}
