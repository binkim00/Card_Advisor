package dev.cardadvisor.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private fun displayWon(value: Long) = "%,d원".format(java.util.Locale.KOREA,value)

@Composable internal fun ExpandablePanel(title:String,initial:Boolean=false,onExpanded:((Boolean)->Unit)?=null,content:@Composable ColumnScope.()->Unit){
    var internal by remember{mutableStateOf(initial)}
    val expanded=if(onExpanded==null)internal else initial
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        TextButton(onClick={if(onExpanded==null)internal=!expanded else onExpanded(!expanded)},contentPadding=PaddingValues(vertical=8.dp)){
            Text(title+if(expanded)"  −" else "  +",style=MaterialTheme.typography.labelLarge)
        }
        if(expanded)content()
    }
}

@Composable internal fun PaymentFields(order:String,points:String,money:String,onOrder:(String)->Unit,onPoints:(String)->Unit,onMoney:(String)->Unit){
    listOf(Triple("쿠폰·배송비 반영 주문 합계",order,onOrder),Triple("사용할 무상 적립포인트",points,onPoints),Triple("사용할 유상 충전 머니",money,onMoney)).forEachIndexed{index,(label,value,change)->
        OutlinedTextField(value,change,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag(listOf("paymentOrder","paymentPoints","paymentMoney")[index]))
    }
}

@Composable internal fun CheckoutOverview(order:String,points:String,money:String,busy:Boolean,paymentLabel:String="네이버페이 카드",onEdit:()->Unit){
    DesignPanel{
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Text("수동 결제 정보",style=MaterialTheme.typography.titleMedium)
            TextButton(enabled=!busy,onClick=onEdit,contentPadding=PaddingValues(0.dp)){Text("결제 정보 수정")}
        }
        Text(paymentLabel+" · 쇼핑",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
        val o=order.toLongOrNull();val p=points.toLongOrNull();val m=money.toLongOrNull()
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Text("카드 결제 예정액",style=MaterialTheme.typography.bodyMedium)
            Text(if(o!=null&&p!=null&&m!=null&&o>=0&&p>=0&&m>=0&&p<=o&&m<=o-p)displayWon(o-p-m) else "입력 확인",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        }
    }
}

@Composable internal fun EmptyRecommendation(){
    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("결제 전에 한 번 더 확인해요",style=MaterialTheme.typography.titleMedium)
            Text("입력한 금액으로 혜택과 다음 달 실적을 비교합니다.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun RecommendationResults(result:JSONObject){
    val cs=result.getJSONArray("candidates")
    for(i in 0 until cs.length()){
        val card=cs.getJSONObject(i);val recommended=card.getString("cardId")==result.optString("recommendedCardId")
        Surface(modifier=Modifier.fillMaxWidth(),color=Color.White,shape=RoundedCornerShape(24.dp),border=if(recommended)BorderStroke(1.5.dp,MaterialTheme.colorScheme.primary)else null){
            Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                    ProductThumbnail(card.getString("name"))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text(card.getString("name")+if(recommended)" · 추천" else "",style=MaterialTheme.typography.titleMedium)
                        Text("이번 혜택 "+if(card.isNull("benefitWon"))"조건 확인 필요" else displayWon(card.getLong("benefitWon")),color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,fontSize=18.sp)
                    }
                }
                if(card.optString("source")=="TERMS_ESTIMATE")Text("약관 기반 예상 · 카드사 확정 혜택 아님",style=MaterialTheme.typography.bodySmall)
                Text("실적 부족액 "+if(card.isNull("gapAfterWon"))"확인 필요" else displayWon(card.getLong("gapAfterWon")),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            Text("구매 후 전체 예산 "+displayWon(result.getLong("overallAfterWon")),fontWeight=FontWeight.SemiBold)
            Text("쇼핑 예산 "+if(result.isNull("categoryAfterWon"))"미확인" else displayWon(result.getLong("categoryAfterWon")),style=MaterialTheme.typography.bodySmall)
            Text("고정비 고려 잔액 "+displayWon(result.getLong("availableAfterWon")),style=MaterialTheme.typography.bodySmall)
            if(result.getBoolean("budgetWarning"))Text("예산 또는 고정비를 고려한 여유 금액을 넘습니다.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        }
    }
    ExpandablePanel("계산 기준 보기"){
        for(i in 0 until cs.length()){
            val card=cs.getJSONObject(i)
            if(!card.isNull("opportunityCostWon"))Text(card.getString("name")+" · 최대 혜택과 차이 "+displayWon(card.getLong("opportunityCostWon")))
            if(card.getBoolean("targetReached"))Text(card.getString("name")+" · 구매 후 다음 달 실적 기준 충족 예상")
        }
        Text("미래 혜택은 다음 달 사용에 따라 달라집니다. 추천만으로 지출이 기록되지 않습니다.")
    }
}

@Composable internal fun LedgerOverview(summary:JSONObject?,month:String){
    if(summary!=null&&summary.optLong("pendingFinancialTransactions",0)>0)Text("보류 금융 거래 ${summary.getLong("pendingFinancialTransactions")}건 · 확인된 지출만 표시합니다.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    Text(month.replace("-","년 ")+"월",style=MaterialTheme.typography.titleMedium)
    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(20.dp),horizontalArrangement=Arrangement.spacedBy(20.dp)){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("이번 달 지출",style=MaterialTheme.typography.bodySmall)
                Text(if(summary==null)"확인 필요" else displayWon(summary.getLong("spentWon")),style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("남은 예산",style=MaterialTheme.typography.bodySmall)
                Text(if(summary==null)"확인 필요" else displayWon(summary.getLong("remainingWon")),style=MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable internal fun LedgerFilters(selected:String,onSelect:(String)->Unit){
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        listOf("ALL" to "전체","SHOPPING" to "쇼핑","FOOD" to "식비","OTHER" to "기타").forEach{(value,label)->
            FilterChip(selected==value,{onSelect(value)},label={Text(label)})
        }
    }
}

private fun JSONObject.ledgerLabel(key:String):String?=optString(key).takeIf{it.isNotBlank()&&it!="null"}

@Composable internal fun LedgerRow(item:JSONObject,expanded:Boolean,enabled:Boolean,onOpen:()->Unit){
    val id=item.getString("id");val original=item.getLong("amount_won");val net=item.getLong("net_won");val cancelled=original-net
    val source=item.getString("source")
    val merchant=item.ledgerLabel("merchant_name")?:if(source=="MANUAL")"수동 구매" else "가맹점 확인 필요"
    val card=item.ledgerLabel("display_card_name")?:if(item.ledgerLabel("financial_status")!=null)"카드 확인 필요"else item.ledgerLabel("card_name")?:"카드 확인 필요"
    val digits=item.ledgerLabel("card_last_digits")?.takeIf{it.matches(Regex("[0-9]{3,4}"))}
    Surface(onClick=onOpen,enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("transaction_"+id),shape=RoundedCornerShape(22.dp),color=Color.White){
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text(merchant,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("ledgerMerchant_"+id))
                    Text(item.getString("purchased_at")+" · "+categoryLabel(item.getString("category")),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(if(cancelled>0)"취소 후 지출"else "지출",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(displayWon(net),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge,modifier=Modifier.testTag("ledgerNet_"+id))
                }
            }
            Text(card+(digits?.let{" · 끝자리 $it"}?:""),style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("ledgerCard_"+id))
            if(source=="MANUAL")Text(if(item.ledgerLabel("financial_status")!=null)"수동 기록 · 금융 내역과 연결됨" else "수동 기록 · 가맹점 미입력",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(item.ledgerLabel("financial_source")=="SYNTHETIC")Text("테스트 내역 · 실제 금융정보 아님",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                FinanceBadge(if(cancelled<=0)"결제"else if(net==0L)"전체 취소"else "부분 취소",modifier=Modifier.testTag("ledgerCancellation_"+id))
                Text(if(item.optBoolean("included",true))"예산 포함" else "예산 제외",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(cancelled>0){
                Text("원래 결제 "+displayWon(original),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("취소 반영 "+displayWon(cancelled),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
            }
            if(item.ledgerLabel("financial_status")?.let{it!="APPLIED"}==true)Text("금융 내역 재확인 필요 · 현재 금액은 기존 가계부 기록 기준입니다.",style=MaterialTheme.typography.bodySmall,color=Color(0xFF855000))
            Text(if(expanded)"상세 접기 ∧" else "거래 상세 ›",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        }
    }
}
