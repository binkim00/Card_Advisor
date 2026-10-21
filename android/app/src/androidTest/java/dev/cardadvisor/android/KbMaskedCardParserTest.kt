package dev.cardadvisor.android
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
@RunWith(AndroidJUnit4::class)
class KbMaskedCardParserTest {
 private fun row(card:String)=JSONObject().put("id",UUID.randomUUID().toString()).put("connection_id",UUID.randomUUID().toString())
  .put("used_date","2026-10-01").put("card_mask",card).put("kind","APPROVAL").put("status","APPLIED").put("source","SYNTHETIC")
  .put("version",1).put("amount_won",50000).put("cancelled_won",0).put("installments",1).put("currency","KRW").put("payment_type","1")
  .put("issue",JSONObject.NULL).put("ledger_id",JSONObject.NULL).put("linked_transaction_id",JSONObject.NULL)
 @Test fun supportedMaskedFormatsParse(){for(card in listOf("*************123","1234********5678")){val parsed=FinancialRow.parse(row(card));assertTrue(parsed.card==card);assertFalse(parsed.held)}}
 @Test fun rawAndUnexpectedFormatsFailClosed(){for(card in listOf("1234567812345678","**************12","untrusted")){assertTrue(runCatching{FinancialRow.parse(row(card))}.isFailure)}}
}
