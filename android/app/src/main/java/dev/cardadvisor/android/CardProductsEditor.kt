package dev.cardadvisor.android
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.*
import org.json.*

@Composable internal fun CardProductsEditor(api:ApiClient,onBusyChanged:(Boolean)->Unit,onChanged:suspend()->Unit,onEditMonth:()->Unit,onOpenFinance:(String)->Unit={}){
 val scope=rememberCoroutineScope { Dispatchers.Main.immediate }
 var products by remember{mutableStateOf(JSONArray())};var cards by remember{mutableStateOf(JSONArray())}
 var linkedCounts by remember{mutableStateOf<Map<String,Int>?>(null)}
 var busy by remember{mutableStateOf(false)};var notice by remember{mutableStateOf("공식 출처의 상품 목록을 불러옵니다.")};var stale by remember{mutableStateOf(true)}
 fun action(block:suspend()->Unit){if(busy)return;busy=true;onBusyChanged(true);scope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:Exception){stale=true;notice=e.message?:"상품을 불러오지 못했습니다."}finally{if(currentCoroutineContext().isActive){busy=false;onBusyChanged(false)}}}}
 suspend fun load(){
  val p=JSONArray(api.request("/v1/card-products"));val c=JSONArray(api.request("/v1/cards"));products=p;cards=c;stale=false
  linkedCounts=try{val counts=mutableMapOf<String,Int>();val connections=JSONArray(api.request("/v1/finance/connections"));for(i in 0 until connections.length()){val connection=connections.getJSONObject(i);val groups=JSONObject(api.request("/v1/finance/connections/"+connection.getString("id")+"/card-mappings")).getJSONArray("groups");for(j in 0 until groups.length()){val group=groups.getJSONObject(j);if(!group.isNull("cardId")){val id=group.getString("cardId");counts[id]=(counts[id]?:0)+1}}};counts}catch(e:CancellationException){throw e}catch(e:Exception){null}
  notice="상품 등록과 금융 카드 연결은 별도입니다. 연결 상태를 확인하고 저장해 주세요."
 }
 LaunchedEffect(Unit){action{load()}}
 Text("내 실제 카드",style=MaterialTheme.typography.titleLarge)
 Text("국내 원화·네이버플러스 스토어/무신사·일반 상품·일시불·단일 승인 범위입니다. 조건과 입력에 따른 예상이며 카드사 확정 혜택이 아닙니다. 행사·해외·기타 가맹점은 포함하지 않습니다.")
 Text(notice,Modifier.testTag("productsNotice"));if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
 OutlinedButton(enabled=!busy,onClick={action{load()}},modifier=Modifier.testTag("productsRefresh")){Text("상품·내 카드 새로고침")}
 for(i in 0 until products.length()){
  val p=products.getJSONObject(i);val added=(0 until cards.length()).any{cards.getJSONObject(it).getString("source")==p.getString("ruleVersion")}
  val registered=(0 until cards.length()).map{cards.getJSONObject(it)}.firstOrNull{it.getString("source")==p.getString("ruleVersion")}
  DesignPanel{
   Row(horizontalArrangement=Arrangement.spacedBy(androidx.compose.ui.unit.Dp(16f)),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){ProductThumbnail(p.getString("name")); Text(p.getString("name"),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))}
   Text(p.getString("scope"));Text("규칙 버전 "+p.getString("ruleVersion"),style=MaterialTheme.typography.bodySmall)
   Text(if(p.getBoolean("noPerformanceRequirement"))"전월 실적 조건 없음" else "기본 실적 기준 %,d원 · 현재 자격과 다음 달 실적은 별도".format(p.getLong("targetWon")))
   Button(enabled=!busy&&!stale&&!added,onClick={action{
    api.request("/v1/cards/from-product","POST",JSONObject().put("productId",p.getString("id")).put("expectedRuleVersion",p.getString("ruleVersion")))
    load();onChanged();notice="상품을 등록했습니다. 아직 금융 카드에 연결된 것은 아닙니다. 금융 카드 연결하기에서 선택·저장해 주세요."
   }},modifier=Modifier.testTag("productAdd_"+p.getString("id"))){Text(if(added)"상품 등록 완료" else "내 카드로 추가")}
   if(added){
    val count=registered?.let{linkedCounts?.get(it.getString("id"))?:if(linkedCounts!=null)0 else null}
    Text(when{count==null->"금융 카드 연결 상태 확인 필요";count==0->"금융 카드 미연결 · 상품만 등록된 상태";else->"금융 카드 연결 저장됨 · ${count}개 조회 카드"},Modifier.testTag("productMapping_"+p.getString("id")))
    Button(enabled=!busy,onClick={onOpenFinance(if(p.getString("id").startsWith("KB_"))"0301"else "0313")},modifier=Modifier.testTag("productFinance_"+p.getString("id"))){Text("금융 카드 연결하기")}
   }
  }
 }
 OutlinedButton(enabled=!busy,onClick=onEditMonth){Text("현재 자격·월별 미기록 실적 입력")}
}

@Composable internal fun ActualCardSummary(c:JSONObject){
 val source=c.getString("benefitRuleVersion");val noThreshold=source.startsWith("OFFICIAL_HANA_TRAVELOG_")
 Text(c.getString("name"),style=MaterialTheme.typography.titleMedium)
 if(noThreshold)Text("전월 실적 조건 없음 · 국내 일반 결제 0.3% 예상 적립") else {
  Text("이번 혜택 자격: "+if(c.isNull("currentQualified"))"미확인" else if(c.getBoolean("currentQualified"))"충족" else "미충족")
  Text("기준 기간 "+c.getString("qualificationPeriod")+" · "+c.getString("currentQualificationSource"))
  Text("다음 달용 실적: "+if(c.isNull("performanceWon"))"미확인" else "%,d원".format(c.getLong("performanceWon")))
  Text("미기록 기준과 확인된 연결 거래를 합산 · 사용자 확인 약관 추정")
  Text("목표 부족액: "+if(c.isNull("gapWon"))"미확인" else "%,d원".format(c.getLong("gapWon")))
 }
 if(c.isNull("ledgerBenefitUsedWon"))Text("연결 구매 혜택: 미확인 · 금융 거래별 조건 확인이 필요합니다.") else Text("연결 구매 순예상적립: %,d원".format(c.getLong("ledgerBenefitUsedWon")))
 Text(if(noThreshold)"국내 일반 적립 월 한도 없음" else if(c.isNull("remainingCapWon"))"예상 잔여 한도: 미확인" else "예상 잔여 한도: %,d원".format(c.getLong("remainingCapWon")))
 Text("원구매 기준 참고값이며 실제 적립·환수된 월과 다를 수 있습니다. 카드사 명세가 우선합니다.",style=MaterialTheme.typography.bodySmall)
 Text("실적 취소 기준: "+c.getString("cancellationPolicy"),style=MaterialTheme.typography.bodySmall)
}
