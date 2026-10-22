package dev.cardadvisor.android
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
@Composable internal fun CheckoutConditions(category:String,payment:String,installments:String,split:String,allocating:Boolean,allocations:Map<String,String>,enabled:Boolean,onCategory:(String)->Unit,onPayment:(String)->Unit,onInstallments:(String)->Unit,onSplit:(String)->Unit,onAllocationToggle:(Boolean)->Unit,onAllocation:(String,String)->Unit){
 Text("구매 분류와 결제 조건",style=MaterialTheme.typography.titleMedium)
 Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
  ledgerCategories.entries.chunked(3).forEach{items->Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){items.forEach{(key,label)->FilterChip(selected=category==key,onClick={onCategory(key)},enabled=enabled,label={Text(label)},modifier=Modifier.testTag("checkoutCategory_$key"))}}}
 }
 listOf("DIRECT_CARD" to "일반 카드","NAVER_PAY_CARD" to "네이버페이 카드","OTHER" to "그 외 결제수단 · 확인 필요").forEach{(key,label)->FilterChip(selected=payment==key,onClick={onPayment(key)},enabled=enabled,label={Text(label)},modifier=Modifier.testTag("checkoutPayment_$key"))}
 OutlinedTextField(installments,onInstallments,enabled=enabled,label={Text("결제 개월 수 · 일시불은 1")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("checkoutInstallments"))
 listOf("NO" to "한 번에 카드 승인","YES" to "여러 번 나누어 카드 승인","UNKNOWN" to "승인 분할 여부 모름").forEach{(key,label)->FilterChip(selected=split==key,onClick={onSplit(key)},enabled=enabled,label={Text(label)},modifier=Modifier.testTag("checkoutSplit_$key"))}
 if(installments!="1"||split!="NO"||payment=="OTHER")Text("할부·분할 승인·미지원 수단은 혜택 조건을 추정하지 않고 추천을 보류합니다. 구매월 예산 지출은 전체 금액으로 계산합니다.")
 Row{Checkbox(allocating,onAllocationToggle,enabled=enabled,modifier=Modifier.testTag("checkoutAllocate"));Text("여러 카테고리에 예산 지출 나누기")}
 if(allocating){Text("무상 포인트를 제외한 지출액을 나누세요. 합계는 카드 승인액 + 유상 머니와 같아야 합니다. 카드 실적과 혜택 업종은 바뀌지 않습니다.")
  ledgerCategories.forEach{(key,label)->OutlinedTextField(allocations[key]?:"",{onAllocation(key,it)},enabled=enabled,label={Text("$label 배분액")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("checkoutAllocation_$key"))}
 }
}
