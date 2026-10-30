package dev.cardadvisor.android
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith
import java.util.UUID

/** Same APK/HTTPS endpoint as the phone, no API override or local mock. */
@RunWith(AndroidJUnit4::class)
class PrivateServerPreviewFlowTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private fun tag(name:String){ui.waitUntil(20000){ui.onAllNodes(hasTestTag(name) and isEnabled()).fetchSemanticsNodes().isNotEmpty()};val n=ui.onNodeWithTag(name);if(name!="heldSave")n.performScrollTo();n.performClick()}
 private fun click(text:String){ui.waitUntil(20000){ui.onAllNodes(hasText(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()};ui.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()}
 private fun await(text:String){ui.waitUntil(20000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 @Test fun phoneApkUsesDeployedPrivatePreview(){
  val run=requireNotNull(InstrumentationRegistry.getArguments().getString("runId"));require(Regex("[a-f0-9-]{36}").matches(run))
  ui.onNodeWithTag("email").performScrollTo().performTextInput("phone-preview-$run@example.test")
  ui.onNodeWithTag("password").performScrollTo().performTextInput("Synthetic-"+UUID.randomUUID())
  click("가입");await("가입했습니다. 내 카드를 등록해 시작해 보세요.")
  tag("dashboardAddCard");tag("importMyCards");tag("heldPreviewSetup");tag("heldFetch");tag("heldSelect_0")
  ui.onNodeWithTag("heldSave").assertIsNotEnabled();tag("heldConfirm");tag("heldSave")
  ui.waitUntil(20000){ui.onAllNodesWithTag("heldRegistered_0").fetchSemanticsNodes().isNotEmpty()}
  click("내 카드로 돌아가기")
  await("보유 카드 등록됨 · 혜택 규칙 미확인, 추천·실적 계산 제외");ui.onNodeWithText("[체험]",substring=true).assertExists()
  ui.onNodeWithText("보유 카드 등록됨 · 혜택 규칙 미확인, 추천·실적 계산 제외").assertExists()
 }
}
