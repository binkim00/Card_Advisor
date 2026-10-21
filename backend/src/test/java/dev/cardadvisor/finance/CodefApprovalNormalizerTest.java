package dev.cardadvisor.finance;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.time.format.DateTimeFormatter;
import static org.junit.jupiter.api.Assertions.*;
class CodefApprovalNormalizerTest {
 static final ObjectMapper JSON=new ObjectMapper();
 static final LocalDate DATE=LocalDate.now(ZoneId.of("Asia/Seoul")).withDayOfMonth(1);
 static ObjectNode row(String approval,String amount,String cancel,String refund){return JSON.createObjectNode()
  .put("resUsedDate",DATE.format(DateTimeFormatter.BASIC_ISO_DATE)).put("resUsedTime","120117")
  .put("resCardNo","1234567812345678").put("resApprovalNo",approval).put("resUsedAmount",amount)
  .put("resCancelYN",cancel).put("resCancelAmount",refund).put("resAccountCurrency","KRW")
  .put("resPaymentType","1").put("resInstallmentMonth","");}
 CodefApprovalNormalizer.Approval parse(ObjectNode row){return new CodefApprovalNormalizer().normalize("0301",row,DATE,DATE).get(0);}
 @Test void kbThreeDigitMaskIsNotMissingOrInvented(){var n=row("A1","50000","0","").put("resCardNo","*************123");var a=parse(n);assertNull(a.issue());assertEquals("*************123",a.cardMask());assertNotNull(a.identity());assertEquals("MISSING_IDENTITY",new CodefApprovalNormalizer().legacyNormalize("0301",n,DATE,DATE).get(0).issue());assertEquals("MISSING_IDENTITY",new CodefApprovalNormalizer().normalize("0313",n,DATE,DATE).get(0).issue());assertEquals("MISSING_IDENTITY",parse(n.put("resCardNo","**************12")).issue());}
 @Test void allowlistedDisplayLabelsOnly(){var n=row("A1","1000","0","").put("resMemberStoreName","테스트 가맹점").put("resCardName","가상 테스트 카드").put("resMemberStoreAddr","PRIVATE_CANARY_ADDRESS");var a=parse(n);assertEquals("테스트 가맹점",a.merchantName());assertEquals("가상 테스트 카드",a.cardLabel());assertFalse(a.toString().contains("테스트"));assertNull(parse(n.put("resCardName","1234567812345678")).cardLabel());}
 @Test void objectArrayMask(){var a=parse(row("A1","50000","0",""));assertNull(a.issue());assertEquals(50000,a.amountWon());assertEquals(0,a.cancelledWon());assertEquals("1234********5678",a.cardMask());assertEquals(1,new CodefApprovalNormalizer().normalize("0313",JSON.createArrayNode().add(row("A1","50000","0","")),DATE,DATE).size());assertFalse(a.toString().contains("1234"));}
 @Test void cumulativePartialAndFull(){var a=parse(row("A1","50000","2","20000"));assertEquals(20000,a.cancelledWon());assertNull(a.issue());assertEquals(50000,parse(row("A1","50000","1","50000")).cancelledWon());}
 @Test void separateKbSlip(){var a=parse(row("A1","-20000","1",""));assertEquals("CANCEL_EVENT",a.kind());assertNull(a.amountWon());assertEquals(20000,a.cancelledWon());assertNull(a.issue());}
 @Test void missingNeverZero(){var a=parse(row("A1","50000","2",""));assertNull(a.cancelledWon());assertEquals("UNKNOWN_OR_INVALID_REFUND",a.issue());a=parse(row("A1","","0",""));assertNull(a.amountWon());assertNull(a.cancelledWon());}
 @Test void installmentAndOther(){var a=parse(row("A1","90000","0","").put("resPaymentType","2").put("resInstallmentMonth","3"));assertEquals(90000,a.amountWon());assertEquals(3,a.installments());assertNull(a.issue());assertEquals("UNKNOWN_INSTALLMENTS",parse(row("A1","90000","0","").put("resPaymentType","2")).issue());assertNull(parse(row("A1","90000","0","").put("resPaymentType","3")).installments());}
 @Test void missingIdentityForeignFractionAndRejected(){assertEquals("MISSING_IDENTITY",parse(row("","50000","0","")).issue());assertNull(parse(row("A","20.50","0","").put("resAccountCurrency","USD").put("resKRWAmt","30000")).amountWon());assertEquals("REJECTED",parse(row("A","50000","3","")).kind());assertNotNull(parse(row("A","1.2","0","")).issue());}
 @Test void badInstitutionAndEnvelope(){assertThrows(IllegalArgumentException.class,()->new CodefApprovalNormalizer().normalize("9999",row("A","1","0",""),DATE,DATE));assertThrows(IllegalArgumentException.class,()->new CodefApprovalNormalizer().normalize("0301",JSON.createObjectNode().set("rows",JSON.createArrayNode()),DATE,DATE));assertThrows(IllegalArgumentException.class,()->new CodefApprovalNormalizer().normalize("0301",JSON.createArrayNode().add("bad"),DATE,DATE));}
}
