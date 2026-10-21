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
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.YearMonth
import java.time.ZoneId

@Composable internal fun BudgetEditor(api:ApiClient,onBusyChanged:(Boolean)->Unit,onInvalidated:()->Unit,onChanged:suspend()->Unit,initialMonth:String?=null){
 val scope=rememberCoroutineScope { kotlinx.coroutines.Dispatchers.Main.immediate };val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
 var month by remember{mutableStateOf(initialMonth?:YearMonth.now(ZoneId.of("Asia/Seoul")).toString())}
 var loadedMonth by remember{mutableStateOf<String?>(null)}
 var scheduled by remember{mutableStateOf(false)}
 var version by remember{mutableLongStateOf(0)}
 var total by remember{mutableStateOf("")};var fixed by remember{mutableStateOf("")}
 var categories by remember{mutableStateOf<Map<String,String>>(emptyMap())}
 var busy by remember{mutableStateOf(false)};var conflict by remember{mutableStateOf(false)}
 var notice by remember{mutableStateOf("저장된 예산을 불러옵니다.")}
 var confirmedWrite by remember{mutableStateOf(false)}
 fun action(block:suspend()->Unit){
  if(busy)return
  focus.clearFocus();keyboard?.hide();busy=true;onBusyChanged(true);notice="처리 중입니다."
  scope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:ApiException){
   if(e.status==409){conflict=true;onInvalidated();notice="다른 곳에서 예산이 변경되었습니다. 내 입력은 유지했습니다. 최신 값을 확인해 주세요."}
   else notice=if(confirmedWrite)"예산은 저장됐지만 새 현황을 불러오지 못했습니다. 최신 값을 다시 불러와 주세요." else e.message?:"예산을 처리하지 못했습니다."
  }catch(e:Exception){notice=if(confirmedWrite)"예산은 저장됐지만 새 현황을 불러오지 못했습니다. 최신 값을 다시 불러와 주세요." else e.message?:"입력을 확인해 주세요."}
  finally{busy=false;onBusyChanged(false)}}
 }
 suspend fun load(target:String){
  require(Regex("[0-9]{4}-[0-9]{2}").matches(target)){"월은 YYYY-MM 형식으로 입력해 주세요."}
  YearMonth.parse(target)
  val saved=try{JSONObject(api.request("/v1/budgets/$target"))}catch(e:ApiException){if(e.status==404)null else throw e}
  if(saved==null){scheduled=false;total="";fixed="";categories=emptyMap();version=0;notice="$target 저장된 예산이 없습니다. 금액을 입력해 새로 저장해 주세요."}
  else{
   scheduled=saved.optString("fixedCostMode")=="SCHEDULED"
   total=saved.getLong("totalWon").toString();fixed=saved.getLong("fixedRemainingWon").toString();version=saved.getLong("version")
   val limits=saved.getJSONObject("categories");categories=limits.keys().asSequence().associateWith{limits.getLong(it).toString()}
   notice="$target 저장된 예산을 불러왔습니다."
  }
  loadedMonth=target;conflict=false;confirmedWrite=false
 }
 fun amount(value:String):Long{
  require(Regex("[0-9]+").matches(value)){"금액은 빈칸 없이 정수 원으로 입력해 주세요. 미반영 고정비가 없으면 0을 입력해 주세요."}
  val result=value.toLongOrNull();require(result!=null&&result in 0..100_000_000L){"금액은 0원부터 1억 원까지 입력해 주세요."};return result
 }
 LaunchedEffect(Unit){action{load(month)}}
 Text("월별 예산 수정",style=MaterialTheme.typography.titleLarge)
 Text("월별 전체 한도와 카테고리 한도를 직접 설정합니다. 미설정은 0원 한도와 다릅니다.")
 OutlinedTextField(month,{month=it},enabled=!busy,label={Text("예산 월 · YYYY-MM")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("budgetMonth"))
 OutlinedButton(enabled=!busy,onClick={action{load(month);onInvalidated();onChanged()}}){Text("저장된 예산 불러오기 (입력 초기화)")}
 Text(notice,Modifier.testTag("budgetNotice"),color=if(conflict)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
 if(loadedMonth!=null&&loadedMonth!=month)Text("월을 바꿨습니다. 저장된 예산을 먼저 불러와 주세요.")
 if(loadedMonth==month){
  Text("$loadedMonth · "+if(version==0L)"새 예산" else "조회 버전 $version")
  OutlinedTextField(total,{total=it},enabled=!busy,label={Text("전체 예산 · 원")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("budgetTotal"))
  if(scheduled)Text("고정비는 반복 일정으로 자동 계산 중입니다. 아래 직접 입력 잔액은 보관되며 현재 계산에는 쓰이지 않습니다. 기준 전환은 반복 고정비 관리에서 할 수 있습니다.")
  OutlinedTextField(fixed,{fixed=it},enabled=!busy&&!scheduled,label={Text("아직 반영되지 않은 고정비 · 원")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("budgetFixed"))
  Text("가계부에 이미 기록한 고정비는 다시 넣지 마세요. 이 값은 예산 지출을 늘리지 않고 추천의 여유 금액에서 차감합니다.")
  Text("카테고리별 한도",style=MaterialTheme.typography.titleMedium)
  for(code in (ledgerCategories.keys+categories.keys).distinct()){
   val enabled=categories.containsKey(code)
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
    Text(categoryLabel(code)+(if(enabled)" · 한도 설정" else " · 미설정"))
    Switch(checked=enabled,enabled=!busy,onCheckedChange={checked->categories=if(checked)categories+(code to "") else categories-code},modifier=Modifier.testTag("budgetEnabled_$code"))
   }
   if(enabled)OutlinedTextField(categories.getValue(code),{categories=categories+(code to it)},enabled=!busy,label={Text(categoryLabel(code)+" 한도 · 원")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("budgetLimit_$code"))
  }
  Text("한도를 끄면 해당 분류는 미설정이 됩니다. 카테고리별 한도의 합은 전체 예산 이하여야 합니다.")
  Button(enabled=!busy&&!conflict,onClick={action{
   val totalWon=amount(total);val fixedWon=amount(fixed);val limits=categories.mapValues{amount(it.value)}
   require(limits.values.sum()<=totalWon){"카테고리 한도의 합이 전체 예산보다 큽니다."}
   val target=loadedMonth!!
   api.request("/v1/budgets/$target","PUT",JSONObject().put("totalWon",totalWon).put("fixedRemainingWon",fixedWon).put("categories",JSONObject(limits)).put("version",version))
   confirmedWrite=true;conflict=true;onInvalidated()
   // Keep saving blocked if either read fails after the committed write.
   onChanged();load(target);notice="$target 예산을 저장하고 다시 불러왔습니다."
  }}){Text("월 예산 저장")}
 }
}
