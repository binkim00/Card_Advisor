package dev.cardadvisor.android

import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Compose -> API -> disposable PostgreSQL, with a forced loopback provider. */
@RunWith(AndroidJUnit4::class)
class HeldCardsFlowTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private fun click(tag:String){
  ui.waitUntil(20000){ui.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
  val node=ui.onNodeWithTag(tag);if(tag!="heldSave")node.performScrollTo();node.assertIsEnabled().performClick()
 }
 private fun await(tag:String){ui.waitUntil(20000){ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
 @Test fun lookupConfirmRegisterUnknownAndReviewedProduct(){
  val args=InstrumentationRegistry.getArguments();val base=requireNotNull(args.getString("baseUrl"));require(Regex("http://127.0.0.1:[0-9]+").matches(base))
  val api=ApiClient(base).apply{token=requireNotNull(args.getString("accessToken"))}
  val mine=mutableStateOf(JSONArray());val closed=mutableStateOf(false)
  ui.activity.runOnUiThread{ui.activity.setContent{AdvisorTheme{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
   if(closed.value)RegisteredCards(mine.value,emptyMap())else HeldCardsEditor(api,{},onChanged={
    check(Looper.myLooper()==Looper.getMainLooper());mine.value=JSONArray(api.request("/v1/cards"))
   },onClose={closed.value=true},onConnect={error("Synthetic connection must already exist")})
  }}}}
  await("heldFetch");click("heldFetch");await("heldSelect_0");click("heldSelect_0")
  ui.onNodeWithTag("heldSave").assertIsNotEnabled();click("heldConfirm");click("heldSave");await("heldRegistered_0")
  ui.waitUntil(20000){mine.value.length()==1};ui.runOnIdle{assertTrue(mine.value.getJSONObject(0).isNull("rateBp"))}
  click("heldFetch");await("heldRegistered_0");click("heldSelect_1");click("heldProduct_0")
  ui.onNodeWithTag("heldSave").assertIsNotEnabled();click("heldConfirm");click("heldSave");await("heldRegistered_1")
  ui.waitUntil(20000){mine.value.length()==2};ui.onNodeWithText("내 카드로 돌아가기").performScrollTo().performClick()
  ui.onNodeWithText("합성 A 미지원").assertExists();ui.onNodeWithText("KB국민 Easy all 티타늄 · AUTO").assertExists()
  ui.runOnIdle{assertTrue(mine.value.length()==2)}
  val events=Diagnostics.requestTraceSnapshot().lineSequence().filter{it.isNotBlank()}.map{JSONObject(it)}.filter{it.optString("event")=="HTTP"&&it.optString("phase")=="RESUMED"}.toList()
  assertTrue(events.isNotEmpty()&&events.all{it.getString("thread")=="MAIN"})
 }
}
