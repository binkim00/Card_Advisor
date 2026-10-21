package dev.cardadvisor.android
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerDesignTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private fun entry(id:String,net:Long)=JSONObject().put("id",id).put("source","CODEF").put("category","SHOPPING").put("purchased_at","2026-10-20").put("amount_won",50000).put("net_won",net).put("included",true).put("merchant_name","테스트 가맹점").put("display_card_name","테스트 카드").put("card_last_digits","123").put("financial_source","SYNTHETIC").put("financial_status","APPLIED")
 @Test fun normalPartialAndFullCancellationKeepAmountsDistinct(){
  val busy=mutableStateOf(false)
  ui.activity.runOnUiThread{ui.activity.setContent{AdvisorTheme{AdvisorShell(true,2,busy,"합성 화면 검사",{},{}){
   LedgerRow(entry("normal",50000),false,true){};LedgerRow(entry("partial",30000),false,true){};LedgerRow(entry("full",0),false,true){}
  }}}}
  ui.onNodeWithTag("ledgerCancellation_normal",useUnmergedTree=true).assertTextEquals("결제")
  ui.onNodeWithTag("ledgerCard_normal",useUnmergedTree=true).assertTextEquals("테스트 카드 · 끝자리 123")
  ui.onNodeWithTag("ledgerMerchant_normal",useUnmergedTree=true).assertTextEquals("테스트 가맹점")
  ui.onNodeWithTag("ledgerCancellation_partial",useUnmergedTree=true).performScrollTo().assertTextEquals("부분 취소")
  ui.onNodeWithTag("ledgerNet_partial",useUnmergedTree=true).assertTextEquals("30,000원")
  ui.onNodeWithTag("ledgerCancellation_full",useUnmergedTree=true).performScrollTo().assertTextEquals("전체 취소")
  ui.onNodeWithTag("ledgerNet_full",useUnmergedTree=true).assertTextEquals("0원")
  val dir=File(ui.activity.getExternalFilesDir(null),"ledger-design-preview").apply{mkdirs()}
  ui.onRoot().captureToImage().asAndroidBitmap().let{b->File(dir,"ledger-cancellations.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
 }
 @Test fun missingLabelsAreExplicitAndRowActionStillWorks(){
  var opened=0
  val item=entry("manual",50000).put("source","MANUAL").put("merchant_name",JSONObject.NULL).put("display_card_name",JSONObject.NULL).put("card_name",JSONObject.NULL).put("card_last_digits","INVALID")
  ui.activity.runOnUiThread{ui.activity.setContent{AdvisorTheme{Column{LedgerRow(item,false,true){opened++}}}}}
  ui.onNodeWithTag("ledgerMerchant_manual",useUnmergedTree=true).assertTextEquals("수동 구매")
  ui.onNodeWithTag("ledgerCard_manual",useUnmergedTree=true).assertTextEquals("카드 확인 필요")
  ui.onNodeWithTag("transaction_manual").performClick();ui.runOnIdle{org.junit.Assert.assertEquals(1,opened)}
 }
}
