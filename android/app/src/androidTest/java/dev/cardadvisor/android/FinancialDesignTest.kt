package dev.cardadvisor.android

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated synthetic composition only: no financial login, API calls or personal screenshot. */
@RunWith(AndroidJUnit4::class)
class FinancialDesignTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private fun row(id:String="partial",kind:String="APPROVAL",amount:Long?=50000,refund:Long?=20000,status:String="APPLIED",issue:String?=null)=FinancialRow(id,"synthetic","2026-10-20","*************123",kind,amount,refund,"KRW","1",1,status,issue,1,null,null,"SYNTHETIC","테스트 온라인스토어","테스트 KB카드","KB국민 Easy all 티타늄")
 @Test fun cardMerchantAndPartialCancellationAreReadable(){
  val r=row();val busy=mutableStateOf(false)
  ui.activity.runOnUiThread{ui.activity.setContent{AdvisorTheme{AdvisorShell(true,6,busy,"합성 데이터 화면 검사",{},{}){
   DesignPanel{FinanceConnectionHeading(FinanceConnection("synthetic","0301",FinanceState.ACTIVE,1,true,null,"DEMO"));Text("이용내역을 확인하고 내 카드 상품과 연결하세요.")}
   DesignPanel{FinancialTransactionHeading(r);FinancialTransactionStatus(r)}
  }}}}
  ui.onNodeWithTag("financeState_0301").assertTextEquals("연결됨")
  ui.onNodeWithText("테스트 온라인스토어").assertIsDisplayed()
  ui.onNodeWithText("KB국민 Easy all 티타늄").assertIsDisplayed()
  ui.onNodeWithTag("syncAmount_partial").assertTextEquals("50,000원")
  ui.onNodeWithText("일부 취소").assertIsDisplayed();ui.onNodeWithText("취소 후 결제액 30,000원").assertIsDisplayed()
  ui.onNodeWithText("끝자리 123").assertExists();capture("finance-partial.png")
 }
 @Test fun pendingRefundIsNotAnAppliedRefundAndUnknownIsNotZero(){
  val r=row("pending","CANCEL_EVENT",null,10000,"HELD","CANCELLATION_MATCH_REQUIRED")
  val busy=mutableStateOf(false)
  ui.activity.runOnUiThread{ui.activity.setContent{AdvisorTheme{AdvisorShell(true,6,busy,"합성 데이터 화면 검사",{},{}){
   FinanceCounts(listOf(r));DesignPanel{FinancialTransactionHeading(r);FinancialTransactionStatus(r)}
  }}}}
  ui.onNodeWithTag("syncCounts").assertTextEquals("총 1건 · 가계부에 기록 0건 · 확인 필요 1건")
  ui.onNodeWithText("원래 결제 확인 필요").assertIsDisplayed()
  ui.onNodeWithTag("syncStatus_pending").assertTextContains("확인 전에는 환불을 가계부에 적용하지 않습니다.",substring=true)
  ui.onAllNodesWithText("취소 반영됨").assertCountEquals(0);capture("finance-pending.png")
  ui.activity.runOnUiThread{ui.activity.setContent{AdvisorTheme{Column{FinancialTransactionHeading(row("unknown",amount=null,refund=null,status="HELD"))}}}}
  ui.onNodeWithTag("syncAmount_unknown").assertTextEquals("미확인")
  ui.onAllNodesWithText("0원").assertCountEquals(0)
 }
 private fun capture(name:String){val dir=File(ui.activity.getExternalFilesDir(null),"finance-design-preview").apply{mkdirs()};ui.onRoot().captureToImage().asAndroidBitmap().let{b->File(dir,name).outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}}
}
