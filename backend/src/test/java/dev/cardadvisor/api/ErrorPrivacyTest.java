package dev.cardadvisor.api;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class ErrorPrivacyTest{
 @Test void parserMessagesAndPrivateInputCannotBecomeErrorCodes(){var errors=new Errors();assertFalse(errors.invalid(new IllegalArgumentException("PRIVATE_CANARY input")).getBody().toString().contains("PRIVATE_CANARY"));assertTrue(errors.invalid(new IllegalArgumentException("AMOUNT_MISMATCH")).getBody().toString().contains("AMOUNT_MISMATCH"));assertTrue(errors.invalid(new IllegalArgumentException()).getBody().toString().contains("INVALID_REQUEST"));}
}
