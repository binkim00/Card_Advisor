package dev.cardadvisor.android

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyCardsUiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 @Test fun emptyOrSyntheticCardsShowRegistrationInsteadOfFixedCards(){
  var added=0
  val demo=JSONObject().put("name","[가상] A 카드").put("benefitRuleVersion","SYNTHETIC_BENEFIT_V1")
  ui.activity.runOnUiThread{ui.activity.setContent{AdvisorTheme{Column(Modifier.verticalScroll(rememberScrollState())){
   DashboardCards(JSONArray().put(demo),{},true,onAdd={added++})
  }}}}
  ui.onNodeWithText("[가상] A 카드").assertDoesNotExist()
  ui.onNodeWithText("첫 카드를 등록해 보세요").assertExists()
  ui.onNodeWithTag("dashboardAddCard").performScrollTo().performClick()
  ui.runOnIdle{assertEquals(1,added)}
 }
 @Test fun registeredCardsChangeWithOwnerAndExcludeDemoProducts(){
  fun render(name:String){
   val owned=JSONObject().put("id","synthetic-user-card").put("name",name).put("source","OFFICIAL_TEST_RULE")
   val demo=JSONObject().put("id","synthetic-demo").put("name","[가상] B 카드").put("source","SYNTHETIC_BENEFIT_V1")
   ui.activity.runOnUiThread{ui.activity.setContent{AdvisorTheme{Column{RegisteredCards(JSONArray().put(owned).put(demo),emptyMap())}}}}
  }
  render("사용자 A의 카드")
  ui.onNodeWithText("사용자 A의 카드").assertExists()
  ui.onNodeWithText("[가상] B 카드").assertDoesNotExist()
  ui.onNodeWithText("KB국민 Easy all 티타늄").assertDoesNotExist()
  render("사용자 B의 카드")
  ui.onNodeWithText("사용자 A의 카드").assertDoesNotExist()
  ui.onNodeWithText("사용자 B의 카드").assertExists()
 }
}
