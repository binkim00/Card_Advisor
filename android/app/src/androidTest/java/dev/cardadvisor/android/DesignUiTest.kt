package dev.cardadvisor.android

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated presentation checks: no account, real API, finance service or database is used. */
@RunWith(AndroidJUnit4::class)
class DesignUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun loginFieldsAndActionsRemainReachable() {
        compose.onNodeWithTag("email").assertExists(); capture("login.png")
        compose.onNodeWithTag("email").performScrollTo().performTextInput("design@example.test")
        compose.onNodeWithTag("password").performScrollTo().performTextInput("SyntheticDesignOnly")
        compose.onNodeWithText("가입").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("로그인").performScrollTo().assertIsEnabled()
        // Do not submit: this test deliberately makes no API calls.
    }
    @Test fun blueShellNavigationBusyStateAndOfficialArtwork() {
        val selected = mutableIntStateOf(7)
        val busy = mutableStateOf(false)
        var edits = 0
        val budget=JSONObject().put("totalWon",1000000).put("spentWon",380000)
            .put("remainingWon",620000).put("fixedRemainingWon",120000).put("availableWon",500000)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                AdvisorTheme {
                    AdvisorShell(true,selected.intValue,busy,"",{selected.intValue=it},{edits++}) {
                        when(selected.intValue) {
                            7 -> { HomeBudget(budget,busy.value,{edits++},{edits++}); CompareCallout(!busy.value){selected.intValue=1}; DashboardCards(org.json.JSONArray().put(JSONObject().put("name","하나 트래블GO 체크").put("currentQualified",JSONObject.NULL).put("performanceWon",JSONObject.NULL).put("gapWon",JSONObject.NULL).put("remainingCapWon",JSONObject.NULL)),{edits++},!busy.value) }
                            1 -> DesignPanel { Text("추천 화면 검사"); Button(onClick={edits++}){Text("입력 수정 검사")} }
                            2 -> DesignPanel { Text("가계부 화면 검사"); Text("부분 취소 후 30,000원") }
                            else -> DesignPanel { Text("설정 화면 검사") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("620,000원").assertIsDisplayed()
        compose.onNode(hasText("홈") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("설정") and hasClickAction()).assertIsNotSelected()
        capture("home-top.png")
        compose.onNodeWithText("하나 트래블GO 체크").performScrollTo().assertIsDisplayed()

        capture("home-cards.png")
        // Existing test helpers call performScrollTo even for the pinned navigation.
        compose.onNodeWithText("추천").performScrollTo().performClick()
        compose.onNodeWithText("추천 화면 검사").assertIsDisplayed()
        compose.onNodeWithText("입력 수정 검사").performClick()
        compose.onNode(hasText("가계부") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("부분 취소 후 30,000원").assertIsDisplayed()
        compose.onNode(hasText("설정") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("설정 화면 검사").assertIsDisplayed()
        compose.runOnIdle { busy.value=true }
        compose.onNodeWithText("홈").assertIsNotEnabled()
        // Busy state is observed by pinned navigation directly.
        compose.runOnIdle { busy.value=false }
        compose.onNodeWithText("홈").performScrollTo().performClick()
        compose.onNodeWithText("620,000원").assertIsDisplayed()
        compose.onNodeWithText("예산 관리 ›").performClick()
        compose.runOnIdle { assertEquals(2,edits) }
    }
    @Test fun recommendationAndLedgerPreview(){
        val selected=mutableIntStateOf(1);val busy=mutableStateOf(false)
        val result=JSONObject().put("recommendedCardId","synthetic-card").put("candidates",org.json.JSONArray().put(JSONObject().put("cardId","synthetic-card").put("name","KB국민 Easy all 티타늄").put("benefitWon",1500).put("gapAfterWon",70000).put("source","TERMS_ESTIMATE").put("targetReached",false)))
            .put("overallAfterWon",570000).put("categoryAfterWon",170000).put("availableAfterWon",450000).put("budgetWarning",false)
        compose.activity.runOnUiThread{compose.activity.setContent{AdvisorTheme{AdvisorShell(true,selected.intValue,busy,"",{selected.intValue=it},{},primaryAction=if(selected.intValue==1){{Button(onClick={},modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text("금액 확인 후 카드 비교")}}}else null){
            if(selected.intValue==1){CheckoutOverview("50000","0","0",false){};RecommendationResults(result)}else{
                LedgerOverview(JSONObject().put("spentWon",380000).put("remainingWon",620000),"2026-10")
                LedgerRow(JSONObject().put("id","synthetic").put("category","SHOPPING").put("source","MANUAL").put("purchased_at","2026-10-20").put("amount_won",50000).put("net_won",30000),false,true){}
            }
        }}}}
        compose.onNodeWithText("이번 혜택 1,500원").assertIsDisplayed();capture("recommendation.png")
        compose.onNode(hasText("가계부") and hasClickAction()).performClick()
        compose.onNodeWithText("취소 반영 20,000원").assertIsDisplayed();capture("ledger.png")
    }
    @Test fun missingBudgetDoesNotBecomeZero() {
        compose.activity.runOnUiThread {
            compose.activity.setContent { AdvisorTheme { HomeBudget(null,false,{},{}) } }
        }
        compose.onNodeWithText("예산을 불러와 주세요").assertExists()
        compose.onAllNodesWithText("0원").assertCountEquals(0)
    }
    @Test fun primaryActionStaysVisibleWhileContentScrolls() {
        val busy=mutableStateOf(false);var clicks=0
        compose.activity.runOnUiThread { compose.activity.setContent { AdvisorTheme {
            AdvisorShell(true,1,busy,"",{},{},primaryAction={
                Button(enabled=!busy.value,onClick={clicks++},modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("dockedCompare")){Text("금액 확인 후 카드 비교")}
            }) {
                CheckoutOverview("50000","0","0",false){}
                DesignPanel { Text("비교 기준",style=MaterialTheme.typography.titleMedium);Text("이번 혜택과 다음 달 실적을 함께 비교해요.") }
                repeat(5){ DesignPanel { Text("입력한 결제 조건 ${it+1}");Text("표시 공간이 길어져도 주요 버튼은 같은 위치에 있습니다.") } }
            }
        } } }
        compose.onNodeWithTag("dockedCompare").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1,clicks) }
        capture("compare-action-docked.png")
        compose.onNodeWithText("입력한 결제 조건 5").performScrollTo()
        compose.onNodeWithTag("dockedCompare").assertIsDisplayed()
        compose.runOnIdle { busy.value=true }
        compose.onNodeWithTag("dockedCompare").assertIsNotEnabled()
        compose.onNodeWithText("설정").assertIsNotEnabled()
    }
    @Test fun groupedSettingsPreserveNavigationAndLogout() {
        val busy=mutableStateOf(false);var destination=0;var logouts=0
        compose.activity.runOnUiThread { compose.activity.setContent { AdvisorTheme {
            AdvisorShell(true,0,busy,"",{destination=it},{logouts++}) {
                SettingsGroup("카드와 금융") {
                    ServiceMenu("내 실제 카드 등록","보유 카드 상품 관리"){destination=9}
                    ServiceMenu("금융 연결 관리","카드사 연결 · 이용내역 가져오기"){destination=6}
                    ServiceMenu("월별 실적·자격 수정","이번 혜택 자격과 다음 달 실적"){destination=3}
                }
                SettingsGroup("소비 관리") {
                    ServiceMenu("월별 예산 수정","전체 예산 · 카테고리별 한도"){destination=4}
                    ServiceMenu("반복 고정비 관리","정기 지출 일정과 거래 연결"){destination=5}
                }
                SettingsGroup("앱 설정") { ServiceMenu("동의·계정 관리","선택 기능 동의 · 계정 삭제"){destination=8} }
            }
        } } }
        capture("settings.png")
        compose.onNodeWithText("금융 연결 관리").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(6,destination) }
        compose.onNodeWithText("나가기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1,logouts) }
    }
    private fun capture(name: String) {
        // Only this test's synthetic app composition, never the device screen or accessibility tree.
        val dir=File(compose.activity.getExternalFilesDir(null),"design-preview").apply{mkdirs()}
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
}
