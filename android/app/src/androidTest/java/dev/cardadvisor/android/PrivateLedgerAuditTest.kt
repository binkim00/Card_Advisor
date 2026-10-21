package dev.cardadvisor.android
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.runner.RunWith
import java.time.*
import java.util.Locale

/** Actual account, GET-only except normal token refresh. Never export values, tree, tokens or images. */
@RunWith(AndroidJUnit4::class)
class PrivateLedgerAuditTest {
 @get:Rule val ui=createEmptyComposeRule()
 private fun report(key:String,value:Int){InstrumentationRegistry.getInstrumentation().sendStatus(2,android.os.Bundle().apply{putString("audit_"+key,value.toString())})}
 private fun stage(value:String){InstrumentationRegistry.getInstrumentation().sendStatus(2,android.os.Bundle().apply{putString("auditStep",value)})}
 private fun JSONObject.label(key:String)=optString(key).takeIf{it.isNotBlank()&&it!="null"}
 private fun visibleEquals(tag:String,expected:String){
  val node=ui.onNodeWithTag(tag,useUnmergedTree=true);node.assertIsDisplayed()
  val actual=node.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(""){it.text}
  Assert.assertTrue("Private ledger display mismatch; values withheld",expected==actual)
 }
 @Test fun savedMappingReadOnly()=runBlocking{
  val context=InstrumentationRegistry.getInstrumentation().targetContext;val api=ApiClient.persistent(context)
  Assert.assertTrue("Saved login unavailable; values withheld",api.restoreSession());stage("session_restored")
  val connections=JSONArray(api.request("/v1/finance/connections"));var saved:JSONObject?=null;var connectionId:String?=null
  for(i in 0 until connections.length()){val c=connections.getJSONObject(i);val mapping=JSONObject(api.request("/v1/finance/connections/"+c.getString("id")+"/card-mappings"));val groups=mapping.getJSONArray("groups");if(saved==null&&(0 until groups.length()).any{!groups.getJSONObject(it).isNull("cardId")}){saved=mapping;connectionId=c.getString("id")}}
  Assert.assertTrue("No saved actual mapping; values withheld",saved!=null)
  val mapping=saved!!;val groups=mapping.getJSONArray("groups");val index=(0 until groups.length()).first{!groups.getJSONObject(it).isNull("cardId")};val group=groups.getJSONObject(index)
  val cards=JSONArray(api.request("/v1/cards"));val card=(0 until cards.length()).map{cards.getJSONObject(it)}.first{it.getString("id")==group.getString("cardId")}
  val products=JSONArray(api.request("/v1/card-products"));val product=(0 until products.length()).map{products.getJSONObject(it)}.first{it.getString("ruleVersion")==card.getString("source")}
  Assert.assertTrue("Saved mapping version invalid; values withheld",group.getLong("version")>0)
  report("linked_groups",(0 until groups.length()).count{!groups.getJSONObject(it).isNull("cardId")});stage("saved_api_mapping_found")
  var scenario:ActivityScenario<PrivateLedgerAuditActivity>?=null
  fun click(text:String){val n=ui.onNode(hasText(text) and hasClickAction());ui.waitUntil(20000){runCatching{n.assertIsEnabled()}.isSuccess};n.performScrollTo().performClick()}
  fun openSaved(){
   click("설정");click("내 실제 카드 등록")
   val status=ui.onNodeWithTag("productMapping_"+product.getString("id"),useUnmergedTree=true)
   ui.waitUntil(15000){runCatching{status.assertTextContains("금융 카드 연결 저장됨",substring=true)}.isSuccess};status.performScrollTo().assertIsDisplayed()
   ui.onNodeWithTag("productFinance_"+product.getString("id")).performScrollTo().performClick()
   val refresh=ui.onNodeWithTag("mappingRefresh");ui.waitUntil(15000){runCatching{refresh.assertIsEnabled()}.isSuccess}
   ui.onNodeWithTag("mappingState_$index").performScrollTo();visibleEquals("mappingState_$index","연결한 상품: "+card.getString("name"));visibleEquals("mappingVersion_$index","서버 저장 버전 "+group.getLong("version"))
  }
  try{
   scenario=ActivityScenario.launch(PrivateLedgerAuditActivity::class.java);openSaved();stage("saved_mapping_visible")
   ui.onNodeWithTag("mappingRefresh").performScrollTo().performClick();ui.waitUntil(15000){ui.onAllNodesWithText("저장된 연결을 확인했습니다.").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithTag("mappingState_$index").performScrollTo();visibleEquals("mappingState_$index","연결한 상품: "+card.getString("name"));stage("saved_mapping_refreshed")
   scenario!!.recreate();openSaved();stage("saved_mapping_reopened")
   val after=JSONObject(api.request("/v1/finance/connections/$connectionId/card-mappings"));Assert.assertTrue("Read-only mapping changed; values withheld",after.toString()==mapping.toString())
   Assert.assertTrue("Login not retained; values withheld",api.sessionActive.value&&SecureSessionStore(context).load()!=null);stage("login_retained")
  }finally{scenario?.close()}
 }
 @Test fun readOnlyActualAccountLedger()=runBlocking{
  val context=InstrumentationRegistry.getInstrumentation().targetContext;val api=ApiClient.persistent(context)
  Assert.assertTrue("Saved login unavailable; credentials withheld",api.restoreSession());stage("session_restored")
  val current=YearMonth.now(ZoneId.of("Asia/Seoul")).toString();val months=sortedSetOf(current)
  val connections=JSONArray(api.request("/v1/finance/connections"))
  for(i in 0 until connections.length()){
   val rows=JSONArray(api.request("/v1/finance/connections/"+connections.getJSONObject(i).getString("id")+"/transactions"))
   for(j in 0 until rows.length()){val row=rows.getJSONObject(j);if(row.optString("source")=="PROVIDER_REPORTED")row.label("used_date")?.let{months.add(YearMonth.from(LocalDate.parse(it)).toString())}}
  }
  var checked=0;var missingMerchant=0;var missingCard=0;var invalidMoney=0;var duplicates=0
  var currentRows=JSONArray()
  for(month in months){
   val rows=JSONArray(api.request("/v1/transactions?month=$month"));val ids=mutableSetOf<String>()
   for(i in 0 until rows.length()){val row=rows.getJSONObject(i);checked++;if(!ids.add(row.getString("id")))duplicates++
    if(row.label("merchant_name")==null)missingMerchant++
    if(row.label("display_card_name")==null)missingCard++
    if(row.isNull("amount_won")||row.isNull("net_won")||row.getLong("net_won")<0||row.getLong("net_won")>row.getLong("amount_won"))invalidMoney++
   }
   if(month==current)currentRows=rows
  }
  report("stored_rows",checked);report("missing_merchant",missingMerchant);report("missing_card",missingCard);report("invalid_money",invalidMoney);report("duplicate_rows",duplicates);stage("stored_api_checked")
  var scenario:ActivityScenario<PrivateLedgerAuditActivity>?=null
  try{
   scenario=ActivityScenario.launch(PrivateLedgerAuditActivity::class.java)
   val nav=ui.onNode(hasText("가계부") and hasClickAction());ui.waitUntil(20000){runCatching{nav.assertIsEnabled()}.isSuccess};nav.performClick()
   if(currentRows.length()>0){val first=currentRows.getJSONObject(0).getString("id");ui.waitUntil(20000){runCatching{ui.onNodeWithTag("transaction_$first").assertIsEnabled()}.isSuccess}}
   stage("current_month_opened")
   for(i in 0 until currentRows.length()){
    val row=currentRows.getJSONObject(i);val id=row.getString("id");ui.onNodeWithTag("transaction_$id").performScrollTo()
    val merchant=row.label("merchant_name")?:if(row.getString("source")=="MANUAL")"수동 구매"else "가맹점 확인 필요"
    val card=row.label("display_card_name")?:if(row.label("financial_status")!=null)"카드 확인 필요"else row.label("card_name")?:"카드 확인 필요"
    val digits=row.label("card_last_digits")?.takeIf{it.matches(Regex("[0-9]{3,4}"))}
    visibleEquals("ledgerMerchant_$id",merchant);visibleEquals("ledgerCard_$id",card+(digits?.let{" · 끝자리 $it"}?:""));visibleEquals("ledgerNet_$id",String.format(Locale.KOREA,"%,d원",row.getLong("net_won")))
    val cancelled=row.getLong("amount_won")-row.getLong("net_won");visibleEquals("ledgerCancellation_$id",if(cancelled<=0)"결제"else if(row.getLong("net_won")==0L)"전체 취소"else "부분 취소")
   }
   report("visible_rows",currentRows.length());stage("visible_values_matched")
   val after=JSONArray(api.request("/v1/transactions?month=$current"));Assert.assertTrue("Read-only ledger changed; values withheld",after.toString()==currentRows.toString())
   Assert.assertTrue("Login not retained; credentials withheld",api.sessionActive.value&&SecureSessionStore(context).load()!=null);stage("login_retained")
  }finally{scenario?.close()}
 }
}
