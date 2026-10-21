package dev.cardadvisor.android
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.YearMonth
import java.time.ZoneId

@Composable internal fun AssistanceView(value:JSONObject){
 Text(if(value.optString("source")=="OPENAI_VALIDATED")"AI가 선택한 검증된 설명" else "계산 결과에 따른 기본 설명",style=MaterialTheme.typography.titleMedium)
 val paragraphs=value.getJSONArray("paragraphs");for(i in 0 until paragraphs.length())Text(paragraphs.getString(i))
 if(value.optString("status")=="AI_NOT_CONFIGURED")Text("AI 연결 설정 전입니다. 기본 기능은 계속 이용할 수 있습니다.",style=MaterialTheme.typography.bodySmall)
 if(value.optString("status")=="CONSENT_REQUIRED")Text("AI 사용은 동의·계정 관리에서 선택할 수 있습니다.",style=MaterialTheme.typography.bodySmall)
}

@Composable internal fun InsightsEditor(api:ApiClient,onBusyChanged:(Boolean)->Unit,onChanged:suspend()->Unit){
 val scope=rememberCoroutineScope { Dispatchers.Main.immediate }
 var month by remember{mutableStateOf(YearMonth.now(ZoneId.of("Asia/Seoul")).toString())};var loadedMonth by remember{mutableStateOf("")}
 var result by remember{mutableStateOf<JSONObject?>(null)};var busy by remember{mutableStateOf(false)};var stale by remember{mutableStateOf(true)}
 var notice by remember{mutableStateOf("소비와 예산을 분석합니다.")};var confirm by remember{mutableStateOf(false)}
 fun action(task:suspend()->Unit){if(busy)return;busy=true;onBusyChanged(true);scope.launch{try{task()}catch(e:CancellationException){throw e}catch(e:Exception){stale=true;notice=if(e is ApiException&&e.status==409)"예산이 변경돼 적용을 중단했습니다. 최신 분석을 조회해 주세요." else e.message?:"분석을 불러오지 못했습니다."}finally{if(currentCoroutineContext().isActive){busy=false;onBusyChanged(false)}}}}
 suspend fun load(){val value=JSONObject(api.request("/v1/insights?month=$month"));result=value;loadedMonth=month;stale=false;notice="저장된 원장과 예산으로 분석했습니다."}
 LaunchedEffect(Unit){action{load()}}
 Text("소비 분석·예산 제안",style=MaterialTheme.typography.titleLarge)
 OutlinedTextField(month,{month=it.take(7);stale=true},label={Text("분석 월 YYYY-MM")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("insightsMonth"))
 OutlinedButton(enabled=!busy,onClick={action{load()}},modifier=Modifier.testTag("insightsRefresh")){Text("최신 소비 분석")}
 Text(notice,Modifier.testTag("insightsNotice"));if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
 result?.let{value->
  value.optJSONObject("comparison")?.let{c->DesignPanel{
   Text("같은 날짜까지 소비 비교",style=MaterialTheme.typography.titleMedium)
   Text(c.getString("month")+" 1~"+c.getInt("throughDay")+"일 · %,d원".format(c.getLong("knownCurrentWon")))
   Text(c.getString("previousMonth")+" 1~"+c.getInt("previousThroughDay")+"일 · %,d원".format(c.getLong("knownPreviousWon")))
   Text(if(c.isNull("differenceWon"))"보류 거래가 있어 증감액을 확정하지 않습니다." else "기록된 지출 차이 %,d원".format(c.getLong("differenceWon")))
   Text("예산 포함 거래의 취소 후 금액입니다. 월 길이가 다르면 비교 일수가 다를 수 있으며, 미수집 거래가 없는 공식 합계는 아닙니다.")
  }}
  BudgetSummaryView(value.getJSONObject("summary"));AssistanceView(value.getJSONObject("assistance"))
  if(!value.isNull("proposedBudget"))DesignPanel{
   val proposed=value.getJSONObject("proposedBudget")
   Text("예산 배분 제안",style=MaterialTheme.typography.titleMedium)
   Text("전체 예산 %,d원 유지 · 미배정 금액만 사용".format(proposed.getLong("totalWon")))
   val categories=proposed.getJSONObject("categories");for(key in categories.keys())Text(categoryLabel(key)+" 한도 %,d원".format(categories.getLong(key)))
   Button(enabled=!busy&&!stale&&loadedMonth==month,onClick={confirm=true},modifier=Modifier.testTag("insightsApplyOpen")){Text("변경안을 확인하고 적용")}
  }else Text("전체 예산과 필수 지출을 유지하는 안전한 변경안이 없습니다. 예산 화면에서 직접 조정할 수 있습니다.")
 }
 if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("예산 배분 변경 확인")},text={Text("화면의 카테고리별 한도로 저장합니다. 전체 예산과 미반영 고정비는 유지하며, 조회 이후 예산이 바뀌면 저장을 중단합니다.")},confirmButton={Button(onClick={confirm=false;val proposal=requireNotNull(result).getJSONObject("proposedBudget");action{api.request("/v1/budgets/$loadedMonth","PUT",proposal);onChanged();load();notice="확인한 예산 배분을 저장하고 다시 분석했습니다."}},modifier=Modifier.testTag("insightsApplyConfirm")){Text("예산 변경 저장")}},dismissButton={TextButton(onClick={confirm=false}){Text("취소")}})
}
