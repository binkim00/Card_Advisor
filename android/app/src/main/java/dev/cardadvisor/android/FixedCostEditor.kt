package dev.cardadvisor.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.*
import java.time.*
import java.util.UUID

private fun fixedMoney(value:Long)="%,d원".format(value)
@Composable internal fun FixedCostEditor(api:ApiClient,onBusyChanged:(Boolean)->Unit,onInvalidated:()->Unit,onChanged:suspend()->Unit,onOpenBudget:(String)->Unit){
 val scope=rememberCoroutineScope { kotlinx.coroutines.Dispatchers.Main.immediate };val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
 var month by remember{mutableStateOf(YearMonth.now(ZoneId.of("Asia/Seoul")).toString())};var loadedMonth by remember{mutableStateOf<String?>(null)}
 var view by remember{mutableStateOf<JSONObject?>(null)};var budget by remember{mutableStateOf<JSONObject?>(null)};var entries by remember{mutableStateOf(JSONArray())}
 var busy by remember{mutableStateOf(false)};var stale by remember{mutableStateOf(false)};var notice by remember{mutableStateOf("고정비를 불러옵니다.")}
 var form by remember{mutableStateOf(false)};var planId by remember{mutableStateOf(UUID.randomUUID().toString())};var version by remember{mutableLongStateOf(0)}
 var name by remember{mutableStateOf("")};var amount by remember{mutableStateOf("")};var day by remember{mutableStateOf("")};var category by remember{mutableStateOf("OTHER")};var active by remember{mutableStateOf(true)}
 var paymentMonth by remember{mutableStateOf(month)};var loadedPaymentMonth by remember{mutableStateOf(month)}
 val paymentParts=remember{mutableStateMapOf<String,String>()}
 var picking by remember{mutableStateOf<JSONObject?>(null)}
 fun action(block:suspend()->Unit){if(busy)return;busy=true;onBusyChanged(true);focus.clearFocus();keyboard?.hide();notice="처리 중입니다."
  scope.launch{try{block()}catch(e:CancellationException){Diagnostics.event(DiagnosticEvent.FIXED_CANCELLED);throw e}catch(e:JSONException){
   Diagnostics.failure(DiagnosticSite.FIXED_OPERATION,e)
   currentCoroutineContext().ensureActive();stale=true;onInvalidated()
   notice="서버 응답을 확인할 수 없습니다. 최신 고정비를 다시 불러와 주세요."
  }catch(e:ApiException){
   Diagnostics.failure(DiagnosticSite.FIXED_OPERATION,e)
   currentCoroutineContext().ensureActive()
   if(e.status==409){stale=true;onInvalidated();notice=if(e.code=="TRANSACTION_ALREADY_LINKED"||e.code=="FIXED_ALLOCATION_EXCEEDS_TRANSACTION")"연결 금액 합계가 원래 거래 금액을 넘습니다. 다른 일정의 배분액도 확인해 주세요." else "다른 곳에서 고정비 또는 예산이 변경되었습니다. 입력은 유지했습니다. 최신 값을 불러와 주세요."}
   else notice=e.message?:"처리하지 못했습니다."
  }catch(e:Exception){Diagnostics.failure(DiagnosticSite.FIXED_OPERATION,e);currentCoroutineContext().ensureActive();notice=e.message?:"입력을 확인해 주세요."}finally{
   // The composition owns this operation. Cancellation must not publish to a removed screen.
   if(currentCoroutineContext().isActive){busy=false;onBusyChanged(false)}
  }}
 }
 suspend fun load(){
  Diagnostics.event(DiagnosticEvent.FIXED_LOAD_START)
  stale=true
  require(Regex("[0-9]{4}-[0-9]{2}").matches(month)&&runCatching{YearMonth.parse(month)}.isSuccess){"월은 YYYY-MM 형식으로 입력해 주세요."}
  val v=JSONObject(api.request("/v1/fixed-costs?month=$month"));val es=JSONArray(api.request("/v1/transactions?month=$month"))
  val b=try{JSONObject(api.request("/v1/budgets/$month"))}catch(e:ApiException){if(e.status==404)null else throw e}
  validateFixedCostResponse(v,es,b,month)
  view=v;entries=es;paymentMonth=month;loadedPaymentMonth=month;paymentParts.clear();budget=b;loadedMonth=month;stale=false;form=false;picking=null;notice="$month 고정비를 불러왔습니다."
  Diagnostics.event(DiagnosticEvent.FIXED_LOAD_OK)
 }
 suspend fun committed(){onInvalidated();stale=true;onChanged();load();notice="고정비 변경을 저장하고 다시 불러왔습니다."}
 fun edit(p:JSONObject?){
  form=true;picking=null;planId=p?.getString("id")?:UUID.randomUUID().toString();version=p?.getLong("version")?:0
  name=p?.getString("name")?:"";amount=p?.getLong("amountWon")?.toString()?:"";day=p?.getInt("dayOfMonth")?.toString()?:"";category=p?.getString("category")?:"OTHER";active=p?.getBoolean("active")?:true
 }
 fun number(value:String,min:Long,max:Long):Long{val n=if(Regex("[0-9]+").matches(value))value.toLongOrNull() else null;require(n!=null&&n in min..max){"예상액은 1원~1억 원, 예정일은 1~31의 정수로 입력해 주세요."};return n}
 DisposableEffect(Unit){Diagnostics.event(DiagnosticEvent.FIXED_ENTER);onDispose{Diagnostics.event(DiagnosticEvent.FIXED_EXIT)}}
 LaunchedEffect(Unit){action{load()}}
 Text("반복 고정비 관리",style=MaterialTheme.typography.titleLarge)
 Text("월별 예상 지출을 예약합니다. 거래 연결은 기록을 추가하거나 실제 결제를 실행하지 않습니다.")
 OutlinedTextField(month,{month=it},enabled=!busy,label={Text("확인·변경할 월 · YYYY-MM")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("fixedMonth"))
 OutlinedButton(enabled=!busy,onClick={action{onInvalidated();load();onChanged()}}){Text("최신 고정비 불러오기 (입력 초기화)")}
 Text(notice,Modifier.testTag("fixedNotice"),color=if(stale)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
 if(stale&&view!=null)Text("아래는 마지막 조회값입니다. 최신 값을 다시 불러온 뒤 저장하거나 연결할 수 있습니다.")
 if(loadedMonth!=month)Text("선택한 월의 저장값을 먼저 불러와 주세요.")
 if(loadedMonth==month){view?.let{v->
  val usable=!busy&&!stale
  Text("$month 반복 일정 잔여 "+fixedMoney(v.getLong("scheduledRemainingWon")))
  Text("직접 입력 잔액 "+if(v.isNull("manualRemainingWon"))"미설정" else fixedMoney(v.getLong("manualRemainingWon")))
  Text("현재 적용 미반영 고정비 "+if(v.isNull("appliedRemainingWon"))"예산 미설정" else fixedMoney(v.getLong("appliedRemainingWon")),Modifier.testTag("fixedApplied"))
  Text("적용 기준: "+when(v.getString("mode")){"SCHEDULED"->"반복 일정";"MANUAL"->"직접 입력";else->"미설정"})
  Text("두 기준을 합산하지 않습니다. 반복 일정으로 전환하기 전에 이번 달의 모든 미반영 고정비를 등록해 주세요. 직접 입력값은 보존됩니다.")
  if(budget==null)OutlinedButton(enabled=!busy,onClick={onOpenBudget(month)}){Text("월별 예산 설정 열기")}
  else OutlinedButton(enabled=usable,onClick={action{
   val request=JSONObject(budget.toString()).put("fixedCostMode",if(v.getString("mode")=="SCHEDULED")"MANUAL" else "SCHEDULED")
   api.request("/v1/budgets/$month","PUT",request);committed()
  }}){Text(if(v.getString("mode")=="SCHEDULED")"직접 입력 기준으로 전환" else "반복 일정 기준으로 전환")}
  Button(enabled=usable,onClick={edit(null)}){Text("고정비 일정 추가")}
  if(form){
   Text(if(version==0L)"새 월 반복 일정" else "선택한 일정 수정",style=MaterialTheme.typography.titleMedium)
   Text("$month 부터 적용합니다. 이전 달은 유지하며, 이미 별도로 설정한 이후 월의 변경은 유지합니다. 29~31일이 없는 달은 말일입니다.")
   OutlinedTextField(name,{name=it},enabled=!busy,label={Text("고정비 이름")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("fixedName"))
   OutlinedTextField(amount,{amount=it},enabled=!busy,label={Text("매월 예상액 · 원")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("fixedAmount"))
   OutlinedTextField(day,{day=it},enabled=!busy,label={Text("매월 예정일 · 1~31")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("fixedDay"))
   Row{Text("이달부터 일정 사용");Switch(active,{active=it},enabled=!busy,modifier=Modifier.testTag("fixedActive"))}
   Text("분류 · 가계부 거래의 분류는 별도로 유지됩니다.")
   for((code,label) in ledgerCategories){FilterChip(selected=category==code,onClick={category=code},enabled=!busy,label={Text(label)},modifier=Modifier.testTag("fixedCategory_$code"))}
   Button(enabled=usable,onClick={action{
    require(name.isNotBlank()&&name.length<=80){"고정비 이름은 1~80자로 입력해 주세요."}
    api.request("/v1/fixed-costs","POST",JSONObject().put("id",planId).put("name",name).put("amountWon",number(amount,1,100_000_000)).put("dayOfMonth",number(day,1,31)).put("category",category).put("active",active).put("effectiveMonth",month).put("version",version));committed()
   }}){Text("고정비 일정 저장")}
   TextButton(enabled=!busy,onClick={form=false}){Text("일정 편집 닫기")}
  }
  val items=v.getJSONArray("items")
  if(items.length()==0)Text("이달에 등록된 반복 일정이 없습니다.")
  for(i in 0 until items.length()){val item=items.getJSONObject(i);val id=item.getString("id")
   Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text(item.getString("name")+(if(item.getBoolean("active"))"" else " · 중지"),style=MaterialTheme.typography.titleMedium)
    Text("예정일 "+item.getString("dueDate")+" · "+categoryLabel(item.getString("category")))
    Text("예상 "+fixedMoney(item.getLong("amountWon"))+" · 반영 "+fixedMoney(item.getLong("includedNetWon"))+" · 잔여 "+fixedMoney(item.getLong("remainingWon")),Modifier.testTag("fixedBalance_$id"))
    Text("금액이 예상보다 적으면 차액을 남겨둡니다. 취소·예산 제외는 예약액을 복원합니다. 추가 지출이 없으면 예상액을 수정하세요.")
    OutlinedButton(enabled=usable,onClick={edit(item)},modifier=Modifier.testTag("fixedEdit_$id")){Text(item.getString("name")+" 일정 수정")}
    OutlinedButton(enabled=usable&&item.getBoolean("active"),onClick={picking=item;form=false},modifier=Modifier.testTag("fixedPick_$id")){Text(item.getString("name")+" 거래 연결")}
    val payments=item.getJSONArray("payments")
    for(j in 0 until payments.length()){val payment=payments.getJSONObject(j);val tx=payment.getString("transactionId")
     Text((payment.optString("purchasedAt").takeIf{it.isNotBlank()}?.let{it+" · "}?:"")+"연결 거래 "+fixedMoney(payment.getLong("netWon"))+" · "+if(payment.getBoolean("included"))"예산 포함" else "예산 제외")
     TextButton(enabled=usable,onClick={action{api.request("/v1/fixed-costs/$id/months/$month/links","PUT",JSONObject().put("transactionId",tx).put("linked",false).put("version",item.getLong("version")));committed()}},modifier=Modifier.testTag("fixedUnlink_$tx")){Text("거래 연결 해제")}
    }
   }}
  }
  picking?.let{selected->
   Text(selected.getString("name")+"에 연결할 $month 거래",style=MaterialTheme.typography.titleMedium)
   Text("거래 일부를 나누거나 다른 달의 결제를 연결할 수 있습니다. 가계부의 구매월은 유지합니다. 부분 취소는 배분 비율대로 반영합니다.")
   OutlinedTextField(paymentMonth,{paymentMonth=it},enabled=!busy,label={Text("결제가 기록된 월 · YYYY-MM")},modifier=Modifier.testTag("fixedPaymentMonth"))
   OutlinedButton(enabled=usable,onClick={action{require(runCatching{YearMonth.parse(paymentMonth)}.isSuccess){"결제 월을 확인해 주세요."};val loaded=JSONArray(api.request("/v1/transactions?month=$paymentMonth"));entries=loaded;loadedPaymentMonth=paymentMonth;paymentParts.clear();notice="선택한 달의 결제 내역을 불러왔습니다."}}){Text("다른 달 결제 불러오기")}
   if(entries.length()==0)Text("해당 월의 구매 기록이 없습니다. 가계부에서 실제 사용 내역을 먼저 기록해 주세요.")
   for(i in 0 until entries.length()){val e=entries.getJSONObject(i);val tx=e.getString("id")
    Text(e.getString("purchased_at")+" · "+categoryLabel(e.getString("category"))+" · "+fixedMoney(e.getLong("net_won"))+" · "+if(e.getBoolean("included"))"예산 포함" else "예산 제외")
    OutlinedTextField(paymentParts[tx]?:e.getLong("amount_won").toString(),{paymentParts[tx]=it},enabled=!busy,label={Text("이 일정에 연결할 원래 금액 · 원")},singleLine=true,modifier=Modifier.testTag("fixedPart_$tx"))
    Button(enabled=usable&&paymentMonth==loadedPaymentMonth,onClick={action{val part=number(paymentParts[tx]?:e.getLong("amount_won").toString(),1,e.getLong("amount_won"));api.request("/v1/fixed-costs/${selected.getString("id")}/months/$month/links","PUT",JSONObject().put("transactionId",tx).put("linked",true).put("version",selected.getLong("version")).put("allocatedWon",part));committed()}},modifier=Modifier.testTag("fixedLink_$tx")){Text("이 거래를 연결")}
   }
   TextButton(enabled=!busy,onClick={picking=null}){Text("거래 선택 닫기")}
  }
 }}
}
