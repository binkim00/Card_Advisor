package dev.cardadvisor.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

internal val ledgerCategories=linkedMapOf("SHOPPING" to "쇼핑","FOOD" to "식비","TRANSPORT" to "교통","HOUSING" to "주거·통신","HEALTH" to "의료","LEISURE" to "여가","OTHER" to "기타","SUBSCRIPTION" to "구독","EDUCATION" to "교육","UNCLASSIFIED" to "미분류")
internal fun categoryLabel(code:String)=ledgerCategories[code]?:"기존 분류 ($code)"
private fun money(value:Long)="%,d원".format(value)

@Composable internal fun BudgetSummaryView(summary:JSONObject){
 Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
  Text("이번 달 예산 지출 "+money(summary.getLong("spentWon")))
  if(summary.optLong("pendingFinancialTransactions",0)>0)Text("보류 금융 거래 ${summary.getLong("pendingFinancialTransactions")}건 · 확인된 지출만 표시합니다.")
  Text("이번 달 남은 예산 "+money(summary.getLong("remainingWon")))
  Text("미반영 고정비 제외 여유 "+money(summary.getLong("availableWon")))
  val categories=summary.getJSONArray("categories")
  for(i in 0 until categories.length()){val c=categories.getJSONObject(i)
   Text(categoryLabel(c.getString("category"))+" 지출 "+money(c.getLong("spentWon"))+" · "+if(c.isNull("remainingWon"))"예산 미설정" else "남은 예산 "+money(c.getLong("remainingWon")))
  }
 }}
}

@Composable internal fun ClassificationEditor(api:ApiClient,initial:JSONObject,onBusyChanged:(Boolean)->Unit,onCommitted:()->Unit,onRefresh:suspend()->Unit,onSaved:()->Unit,onClose:()->Unit){
 val scope=rememberCoroutineScope { kotlinx.coroutines.Dispatchers.Main.immediate }
 var saved by remember(initial.getString("id")){mutableStateOf(initial)}
 var category by remember{mutableStateOf(initial.getString("category"))}
 var split by remember{mutableStateOf(initial.optJSONObject("allocations_won")?.length()?.let{it>1}?:false)}
 val allocations=remember{mutableStateMapOf<String,String>().apply{val a=initial.optJSONObject("allocations_won");ledgerCategories.keys.forEach{put(it,if(a?.has(it)==true)a.getLong(it).toString() else "")}}}
 var included by remember{mutableStateOf(initial.getBoolean("included"))}
 var busy by remember{mutableStateOf(false)};var conflict by remember{mutableStateOf(false)}
 var merchantRule by remember{mutableStateOf<JSONObject?>(null)}
 var notice by remember{mutableStateOf("부분 취소 후 남은 지출을 예산에 반영합니다. 카드 승인액·환불액·실적·혜택은 변경하지 않습니다.")}
 fun adopt(value:JSONObject){saved=value;val a=value.optJSONObject("allocations_won");split=(a?.length()?:0)>1;ledgerCategories.keys.forEach{allocations[it]=if(a?.has(it)==true)a.getLong(it).toString() else ""};category=value.getString("category");included=value.getBoolean("included");conflict=false}
 var merchantAction by remember{mutableStateOf(false)}
 fun action(task:suspend()->Unit){if(busy)return;busy=true;onBusyChanged(true);notice="처리 중입니다."
  scope.launch{try{task()}catch(e:CancellationException){throw e}catch(e:ApiException){
   if(e.status==409&&e.code=="VERSION_CONFLICT"&&merchantAction){notice="가맹점 분류가 다른 곳에서 바뀌었습니다. 가맹점 분류 새로고침 후 다시 확인해 주세요."}
   else if(e.status==409&&e.code=="VERSION_CONFLICT"){conflict=true;onCommitted();notice="다른 곳에서 분류가 변경되었습니다. 내 입력은 유지했습니다. 최신 값을 확인해 주세요."}
   else notice=e.message?:"저장하지 못했습니다."
  }catch(e:Exception){notice=e.message?:"연결을 확인해 주세요."}finally{busy=false;merchantAction=false;onBusyChanged(false)}}
 }
 LaunchedEffect(saved.getString("id")){try{merchantRule=JSONObject(api.request("/v1/transactions/${saved.getString("id")}/classification-rule"))}catch(e:CancellationException){throw e}catch(_:Exception){}}
 AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text("거래 분류 수정")},text={
  Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text("남은 지출 "+money(saved.getLong("net_won")))
   Text("분류 버전 "+saved.getLong("classification_version"))
   if(!ledgerCategories.containsKey(category))Text("현재 분류: "+categoryLabel(category))
   for(pair in ledgerCategories.entries.chunked(2)){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    for((code,label) in pair)FilterChip(selected=category==code,enabled=!busy,onClick={category=code},label={Text(label)},modifier=Modifier.testTag("classify_$code"))
   }}
   Row{Checkbox(split,{split=it},enabled=!busy,modifier=Modifier.testTag("splitBudget"));Text("여러 카테고리로 나누기")}
   if(split){
    Text("취소 전 원래 지출 ${money(saved.getLong("amount_won"))}을 나눠 입력하세요. 부분 취소는 같은 비율로 차감하며 원 단위 나머지는 큰 소수부터 배분합니다.")
    ledgerCategories.forEach{(code,label)->OutlinedTextField(allocations[code]?:"",{allocations[code]=it},label={Text(label+" 배분액 (원)")},enabled=!busy,singleLine=true,modifier=Modifier.testTag("allocation_$code"))}
   }
   Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Text("예산에 포함");Switch(checked=included,onCheckedChange={included=it},enabled=!busy,modifier=Modifier.testTag("budgetIncluded"))}
   Text(if(included)"이번 달 전체·분류별 예산 지출에 포함합니다." else "예산 지출에서 제외합니다. 원장과 카드 실적·혜택에는 남습니다.")
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   OutlinedButton(enabled=!busy&&!conflict,onClick={action{
    val proposal=JSONObject(api.request("/v1/transactions/${saved.getString("id")}/classification-suggestion","POST"))
    if(proposal.getLong("classificationVersion")!=saved.getLong("classification_version")){conflict=true;notice="분류가 변경됐습니다. 최신 값을 조회해 주세요."}
    else{category=proposal.getString("category");notice="제안한 분류를 선택했습니다. 확인 후 분류 저장을 눌러야 적용됩니다."}
   }},modifier=Modifier.testTag("classificationSuggest")){Text("분류 제안 확인")}
   merchantRule?.takeIf{it.optBoolean("available")}?.let{rule->
    Text(if(rule.isNull("category"))"이 가맹점에 기억한 분류가 없습니다." else "기억한 분류: "+categoryLabel(rule.getString("category")))
    Text("같은 가맹점 이름의 다음 거래에 제안만 합니다. 이전 거래·실적·혜택은 바뀌지 않습니다.")
    OutlinedButton(enabled=!busy&&!split,onClick={action{
     merchantAction=true;merchantRule=JSONObject(api.request("/v1/transactions/${saved.getString("id")}/classification-rule","PUT",JSONObject().put("category",category).put("confirmed",true).put("expectedVersion",rule.getLong("version"))))
     notice="선택한 분류를 기억했습니다. 현재 거래는 분류 저장을 눌러야 변경됩니다."
    }},modifier=Modifier.testTag("rememberMerchant")){Text("이 분류를 가맹점에 기억")}
    if(!rule.isNull("category"))TextButton(enabled=!busy,onClick={action{
     merchantAction=true;merchantRule=JSONObject(api.request("/v1/transactions/${saved.getString("id")}/classification-rule","PUT",JSONObject().put("category",JSONObject.NULL).put("confirmed",true).put("expectedVersion",rule.getLong("version"))))
     notice="가맹점 분류 기억을 해제했습니다. 기존 거래는 유지됩니다."
    }},modifier=Modifier.testTag("forgetMerchant")){Text("기억한 분류 해제")}
    TextButton(enabled=!busy,onClick={action{merchantRule=JSONObject(api.request("/v1/transactions/${saved.getString("id")}/classification-rule"));notice="기억한 분류를 다시 조회했습니다."}}){Text("가맹점 분류 새로고침")}
   }
   Text(notice,Modifier.testTag("classificationNotice"))
   if(conflict)TextButton(enabled=!busy,onClick={action{
    adopt(JSONObject(api.request("/v1/transactions/"+saved.getString("id"))));onCommitted();onRefresh();notice="최신 분류를 불러왔습니다. 다시 수정할 수 있습니다."
   }}){Text("최신 분류 불러오기 (입력 초기화)")}
  }
 },confirmButton={Button(enabled=!busy&&!conflict,onClick={action{
  val body=JSONObject().put("category",category).put("included",included).put("version",saved.getLong("classification_version"))
  if(split){val parts=JSONObject();var sum=0L;for((key,value) in allocations){if(value.isBlank())continue;val n=value.toLongOrNull();require(n!=null&&n in 0..saved.getLong("amount_won")){"배분액은 0 이상의 정수로 입력해 주세요."};sum+=n;parts.put(key,n)};require(sum==saved.getLong("amount_won")){"배분 합계가 원래 지출과 같아야 합니다."};require(parts.has(category)){"대표 카테고리의 배분액도 입력해 주세요."};body.put("allocationsWon",parts)}
  api.request("/v1/transactions/${saved.getString("id")}/classification","PUT",body)
  onCommitted();conflict=true
  adopt(JSONObject(api.request("/v1/transactions/"+saved.getString("id"))));onRefresh();onSaved()
 }}){Text("분류 저장")}},dismissButton={TextButton(enabled=!busy,onClick=onClose){Text("닫기")}})
}
