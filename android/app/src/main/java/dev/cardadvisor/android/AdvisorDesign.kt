package dev.cardadvisor.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private val Blue = Color(0xFF2563EB)
private val Ink = Color(0xFF14213D)
private val Pale = Color(0xFFEFF6FF)
private val Line = Color(0xFFDCE6F2)

@Composable internal fun AdvisorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue, onPrimary = Color.White, primaryContainer = Color(0xFFDBEAFE),
            onPrimaryContainer = Color(0xFF1746A2), secondary = Color(0xFF536581),
            secondaryContainer = Pale, onSecondaryContainer = Ink,
            background = Color(0xFFF4F6FA), onBackground = Ink,
            surface = Color.White, onSurface = Ink, surfaceVariant = Pale,
            onSurfaceVariant = Color(0xFF586781), outline = Color(0xFF8C9AB0),
            outlineVariant = Line, surfaceContainer = Color.White,
            surfaceContainerLow = Color.White, surfaceContainerHighest = Pale
        ),
        shapes = Shapes(small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        typography = Typography(
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp)
        ), content = content
    )
}

/** Presentation only: operations, authentication and request state stay with AdvisorApp. */
@Composable internal fun AdvisorShell(
    loggedIn: Boolean, tab: Int, busyState: State<Boolean>, message: String,
    onNavigate: (Int) -> Unit, onLogout: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (loggedIn) Surface(color = Color.White, tonalElevation = 0.dp, shadowElevation = 3.dp) {
                // Scroll semantics also keep the existing instrumentation navigation helper compatible.
                BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding()) {
                val navigationWidth = maxWidth
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                Row(Modifier.width(navigationWidth).padding(horizontal = 8.dp)) {
                    listOf(7 to "홈", 1 to "추천", 2 to "가계부", 0 to "설정").forEach { (target, label) ->
                        val selected = if (target == 0) (tab == 0 || (tab in 3..12 && tab != 7)) else tab == target
                        NavigationBarItem(
                            // Observe the state in this persistent Scaffold slot, not a captured Boolean.
                            selected = selected, onClick = { onNavigate(target) }, enabled = !busyState.value,
                            icon = { AdvisorNavIcon(target, selected) }, label = { Text(label, fontSize = 12.sp) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Pale, selectedIconColor = Blue, selectedTextColor = Blue),
                            modifier = Modifier.widthIn(min = 72.dp).weight(1f)
                        )
                    }
                }
                }
                }
            }
        }
    ) { padding ->
        // Each destination owns its scroll position; navigating never leaves a new page halfway down.
        key(if (loggedIn) tab else -1) {
            Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(when { !loggedIn -> "카드 어드바이저"; tab == 1 -> "결제 전 카드 추천"; tab == 2 -> "가계부"; tab==0 -> "설정"; tab==3 -> "실적과 혜택 자격"; tab==4 -> "월별 예산"; tab==5 -> "반복 고정비"; tab==6 -> "금융 연결"; tab==8 -> "계정과 동의"; tab==9 -> "내 카드"; tab==10 -> "소비 분석"; tab==11 -> "주문서 확인"; else -> "카드 어드바이저" }, style = MaterialTheme.typography.titleLarge)
                        if(tab==7||!loggedIn)Text("결제 전, 나에게 맞는 선택", color = MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
                    }
                    if(loggedIn&&tab==7)IconButton(enabled=!busyState.value,onClick={onNavigate(0)}){AdvisorNavIcon(0,false)}
                    else if(loggedIn&&tab==0)TextButton(enabled=!busyState.value,onClick=onLogout){Text("나가기",fontSize=12.sp)}
                }
                // Full messages remain accessible; quiet one-line status can be expanded for errors.
                var noticeExpanded by remember(message){mutableStateOf(false)}
                Text(message,modifier=Modifier.fillMaxWidth().clickable{noticeExpanded=!noticeExpanded},
                    maxLines=if(noticeExpanded)Int.MAX_VALUE else 1,overflow=TextOverflow.Ellipsis,
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if (busyState.value) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Blue, trackColor = Line)
                content()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable private fun AdvisorNavIcon(destination: Int, selected: Boolean) {
    val color = if (selected) Blue else Color(0xFF6B7890)
    Canvas(Modifier.size(24.dp)) {
        val w = size.width; val h = size.height; val stroke = Stroke(width = 1.8.dp.toPx())
        when (destination) {
            7 -> drawPath(Path().apply { moveTo(w*.14f,h*.45f); lineTo(w*.5f,h*.12f); lineTo(w*.86f,h*.45f); lineTo(w*.86f,h*.88f); lineTo(w*.6f,h*.88f); lineTo(w*.6f,h*.61f); lineTo(w*.4f,h*.61f); lineTo(w*.4f,h*.88f); lineTo(w*.14f,h*.88f); close() }, color, style = stroke)
            1 -> drawPath(Path().apply { for(i in 0..9) { val angle = Math.PI*i/5-Math.PI/2; val radius = if(i%2==0) .44f else .2f; val x=w*(.5f+radius*kotlin.math.cos(angle).toFloat()); val y=h*(.5f+radius*kotlin.math.sin(angle).toFloat()); if(i==0)moveTo(x,y)else lineTo(x,y) }; close() }, color, style = stroke)
            2 -> for (i in 0..2) { val x=w*(.22f+i*.28f); drawLine(color, Offset(x,h*.85f), Offset(x,h*(if(i==1).15f else if(i==2).4f else .55f)), strokeWidth=3.dp.toPx()) }
            else -> { drawCircle(color, radius=w*.36f, style=stroke); drawCircle(color,radius=w*.12f,style=stroke); for(i in 0..7){val a=Math.PI*i/4;drawLine(color,Offset(w*(.5f+.36f*kotlin.math.cos(a).toFloat()),h*(.5f+.36f*kotlin.math.sin(a).toFloat())),Offset(w*(.5f+.48f*kotlin.math.cos(a).toFloat()),h*(.5f+.48f*kotlin.math.sin(a).toFloat())),strokeWidth=2.dp.toPx())} }
        }
    }
}

@Composable internal fun DesignPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

private fun designWon(value: Long) = "%,d원".format(java.util.Locale.KOREA, value)
@Composable internal fun HomeBudget(summary: JSONObject?, busy: Boolean, onBudget: () -> Unit, onRefresh: () -> Unit) {
    Surface(shape=RoundedCornerShape(28.dp),modifier=Modifier.fillMaxWidth(),color=Blue){
        Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF123B91),Color(0xFF2869E8)))).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Text("이번 달 남은 예산",color=Color(0xFFDCE8FF),style=MaterialTheme.typography.labelLarge)
                Text("예산 관리 ›",color=Color.White,style=MaterialTheme.typography.labelMedium,modifier=Modifier.clickable(enabled=!busy,onClick=onBudget).padding(vertical=8.dp))
            }
            if(summary==null){
                Text("예산을 불러와 주세요",color=Color.White,style=MaterialTheme.typography.titleLarge)
                TextButton(enabled=!busy,onClick=onRefresh,colors=ButtonDefaults.textButtonColors(contentColor=Color.White)){Text("현황 새로고침")}
            }else{
                val remaining=summary.getLong("remainingWon");val total=summary.getLong("totalWon");val spent=summary.getLong("spentWon")
                Text(designWon(remaining),style=MaterialTheme.typography.headlineLarge.copy(fontWeight=FontWeight.Bold,letterSpacing=(-1).sp),color=Color.White)
                if(total>0)LinearProgressIndicator(progress={(spent.toFloat()/total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(6.dp),color=Color(0xFF9DC5FF),trackColor=Color.White.copy(alpha=.18f))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Text("사용 "+designWon(spent),style=MaterialTheme.typography.bodySmall,color=Color(0xFFDCE8FF))
                    Text("전체 "+designWon(total),style=MaterialTheme.typography.bodySmall,color=Color(0xFFDCE8FF))
                }
                HorizontalDivider(color=Color.White.copy(alpha=.18f))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Text("미반영 고정비",color=Color(0xFFDCE8FF),style=MaterialTheme.typography.bodySmall)
                    Text(designWon(summary.getLong("fixedRemainingWon")),color=Color.White,style=MaterialTheme.typography.labelLarge)
                }
                if(summary.optLong("pendingFinancialTransactions",0)>0)Text("보류 금융 거래 ${summary.getLong("pendingFinancialTransactions")}건 · 확인된 지출 기준",color=Color(0xFFFFE3AB),style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable internal fun DashboardCards(cards: JSONArray, onEdit: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("내 카드", style = MaterialTheme.typography.titleLarge)
        TextButton(enabled=enabled,onClick=onEdit){Text("실적 관리")}
    }
    if (cards.length()==0) DesignPanel { Text("첫 카드를 등록해 보세요",style=MaterialTheme.typography.titleMedium); Text("보유한 카드와 예산을 기준으로 결제 전 선택을 도와드려요.",color=MaterialTheme.colorScheme.onSurfaceVariant) }
    for (i in 0 until cards.length()) {
        val c=cards.getJSONObject(i)
        DesignPanel {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                ProductThumbnail(c.getString("name"))
                Column(Modifier.weight(1f)) {
                    Text(c.getString("name"),style=MaterialTheme.typography.titleMedium)
                    Text("이번 혜택 자격 · "+if(c.isNull("currentQualified"))"확인 필요" else if(c.getBoolean("currentQualified"))"충족" else "미충족",color=Blue,style=MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.background)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text("다음 달용 실적",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if(c.isNull("performanceWon"))"미확인"else designWon(c.getLong("performanceWon")),fontWeight=FontWeight.Bold)
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text("혜택 잔여 한도",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if(c.isNull("remainingCapWon"))"미확인"else designWon(c.getLong("remainingCapWon")),fontWeight=FontWeight.Bold)
                }
            }
            Text("목표까지 "+if(c.isNull("gapWon"))"확인 필요"else designWon(c.getLong("gapWon")),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Exact official assets, never used for virtual A/B products. No entitlement is inferred from artwork. */
@Composable internal fun ProductThumbnail(name: String, modifier: Modifier = Modifier) {
    val image = when {
        name.contains("가상") -> null
        name.contains("Easy all",ignoreCase=true)&&name.contains("티타늄") -> R.drawable.card_kb_easy_all
        name.contains("트래블GO",ignoreCase=true) -> R.drawable.card_hana_travelgo
        name.contains("트래블로그") -> R.drawable.card_hana_travelog
        else -> null
    }
    if (image != null) Image(painterResource(image),contentDescription=null,contentScale=ContentScale.Fit,modifier=modifier.width(94.dp).height(60.dp))
    else Surface(modifier=modifier.width(80.dp).height(52.dp),color=Blue,shape=RoundedCornerShape(9.dp)) {
        Box(contentAlignment=Alignment.Center){Text(if(name.contains("가상"))"가상 카드"else "카드",color=Color.White,fontSize=12.sp)}
    }
}

@Composable internal fun OfficialCardGallery() {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
        Text("지원 카드 안내",style=MaterialTheme.typography.titleLarge)
        Text("상품 안내 · 계산 미연결",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    listOf("KB국민 Easy all 티타늄" to "AUTO 모드", "하나 트래블로그 체크" to "트래블로그", "하나 트래블GO 체크" to "트래블GO").forEach{(name,detail)->
        DesignPanel {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                ProductThumbnail(name,Modifier)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(name,fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=19.sp)
                    Text(detail,color=Blue,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** All shortcuts keep their real navigation callbacks. No decorative inactive controls. */
@Composable internal fun ServiceMenu(title:String,detail:String,enabled:Boolean=true,onClick:()->Unit){
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(20.dp),color=Color.White,modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text(title,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyLarge)
                Text(detail,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
            }
            Text("›",fontSize=26.sp,color=Color(0xFF9AA8BB))
        }
    }
}
@Composable internal fun CompareCallout(enabled:Boolean,onClick:()->Unit){
    Surface(onClick=onClick,enabled=enabled,color=Color.White,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(22.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
            Surface(shape=RoundedCornerShape(16.dp),color=Pale){Box(Modifier.size(52.dp),contentAlignment=Alignment.Center){AdvisorNavIcon(1,true)}}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("결제 전 카드 비교",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                Text("이번 혜택과 다음 달 실적을 함께",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›",color=Blue,fontSize=26.sp)
        }
    }
}
