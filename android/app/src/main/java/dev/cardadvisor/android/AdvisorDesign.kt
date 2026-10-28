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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private val Blue = Color(0xFF245CEB)
private val Ink = Color(0xFF14213D)
private val Pale = Color(0xFFEFF6FF)
private val Line = Color(0xFFDCE6F2)

@Composable internal fun AdvisorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue, onPrimary = Color.White, primaryContainer = Color(0xFFDBEAFE),
            onPrimaryContainer = Color(0xFF1746A2), secondary = Color(0xFF536581),
            secondaryContainer = Pale, onSecondaryContainer = Ink,
            background = Color(0xFFF5F6FA), onBackground = Ink,
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
    primaryAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (loggedIn) Surface(color = Color.White, tonalElevation = 0.dp) {
                Column(Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = Line.copy(alpha = .65f))
                    // Keep a scroll ancestor for existing accessibility/test scroll-to actions.
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val barWidth = maxWidth
                        if (primaryAction != null) Row(Modifier.horizontalScroll(rememberScrollState())) {
                            Box(Modifier.width(barWidth).padding(horizontal = 22.dp, vertical = 12.dp)) {
                                primaryAction()
                            }
                        }
                        if (primaryAction == null) Spacer(Modifier.height(0.dp))
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val barWidth = maxWidth
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            Row(Modifier.width(barWidth).height(80.dp).padding(horizontal = 8.dp)) {
                                listOf(7 to "홈", 1 to "추천", 2 to "가계부", 0 to "설정").forEach { (target, label) ->
                                    val selected = if (target == 0) (tab == 0 || (tab in 3..12 && tab != 7)) else tab == target
                                    NavigationBarItem(
                                        selected = selected, onClick = { onNavigate(target) }, enabled = !busyState.value,
                                        icon = { AdvisorNavIcon(target, selected) },
                                        label = { Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                                        colors = NavigationBarItemDefaults.colors(indicatorColor = Pale, selectedIconColor = Blue, selectedTextColor = Blue),
                                        modifier = Modifier.widthIn(min = 72.dp).weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        key(if (loggedIn) tab else -1) {
            Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (tab == 7 || !loggedIn) Text("카드 어드바이저", color = Blue, style = MaterialTheme.typography.labelLarge)
                        Text(when {
                            !loggedIn -> "나에게 맞는 카드 생활"; tab == 7 -> "이번 달 소비"; tab == 1 -> "결제 전 카드 추천"; tab == 2 -> "가계부"; tab == 0 -> "설정"
                            tab == 3 -> "실적과 혜택 자격"; tab == 4 -> "월별 예산"; tab == 5 -> "반복 고정비"; tab == 6 -> "금융 연결"
                            tab == 8 -> "계정과 동의"; tab == 9 -> "내 카드"; tab == 10 -> "소비 분석"; tab == 11 -> "주문서 확인"; else -> "카드 어드바이저"
                        }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp)
                        if (tab == 7 && loggedIn) Text("예산부터 카드 혜택까지, 한눈에", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (loggedIn && tab == 7) IconButton(enabled = !busyState.value, onClick = { onNavigate(0) }, modifier = Modifier.semantics { contentDescription = "설정 열기" }) { AdvisorNavIcon(0, false) }
                    else if (loggedIn && tab !in listOf(0, 1, 2, 7)) TextButton(enabled = !busyState.value, onClick = { onNavigate(0) }) { Text("설정으로") }
                }
                // Remove only the initial technical placeholder; user messages and errors remain visible.
                if (message.isNotBlank() && message != "로컬 개발용 · 가상 카드 규칙으로 계산합니다.") {
                    var expanded by remember(message) { mutableStateOf(false) }
                    Surface(color = Pale, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                        Text(message, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), maxLines = if (expanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (busyState.value) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Blue, trackColor = Line)
                content()
                if (loggedIn && tab == 0) TextButton(enabled = !busyState.value, onClick = onLogout, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("나가기", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
    Surface(shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth(), color = Color.White) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("이번 달 남은 예산", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                TextButton(enabled = !busy, onClick = onBudget, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("예산 관리 ›", fontSize = 12.sp) }
            }
            if (summary == null) {
                Text("예산을 불러와 주세요", style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = !busy, onClick = onRefresh) { Text("현황 새로고침") }
            } else {
                val remaining = summary.getLong("remainingWon"); val total = summary.getLong("totalWon"); val spent = summary.getLong("spentWon")
                Text(designWon(remaining), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.4).sp), color = Ink)
                if (total > 0) LinearProgressIndicator(progress = { (spent.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Blue, trackColor = Pale)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("사용 " + designWon(spent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("전체 " + designWon(total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("미반영 고정비", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(designWon(summary.getLong("fixedRemainingWon")), style = MaterialTheme.typography.labelLarge, color = Ink)
                    }
                }
                if (summary.optLong("pendingFinancialTransactions", 0) > 0) Text("보류 금융 거래 ${summary.getLong("pendingFinancialTransactions")}건 · 확인된 지출 기준", color = Color(0xFF976012), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable internal fun DashboardCards(cards: JSONArray, onEdit: () -> Unit, enabled: Boolean, onAdd: () -> Unit = onEdit) {
    val owned=JSONArray().apply{for(i in 0 until cards.length()){val card=cards.getJSONObject(i);if(!card.optString("name").startsWith("[가상]")&&!card.optString("benefitRuleVersion").startsWith("SYNTHETIC"))put(card)}}
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("내 카드", style = MaterialTheme.typography.titleLarge)
        TextButton(enabled=enabled,onClick=onEdit){Text("실적 관리")}
    }
    if (owned.length()==0) DesignPanel { Text("첫 카드를 등록해 보세요",style=MaterialTheme.typography.titleMedium); Text("보유한 카드와 예산을 기준으로 결제 전 선택을 도와드려요.",color=MaterialTheme.colorScheme.onSurfaceVariant); Button(enabled=enabled,onClick=onAdd,modifier=Modifier.fillMaxWidth().testTag("dashboardAddCard")){Text("내 카드 추가하기")} }
    for (i in 0 until owned.length()) {
        val c=owned.getJSONObject(i)
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
private val LocalGroupedMenu = staticCompositionLocalOf { false }

@Composable internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = Color.White, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            CompositionLocalProvider(LocalGroupedMenu provides true) {
                Column(Modifier.padding(vertical = 4.dp), content = content)
            }
        }
    }
}

@Composable internal fun ServiceMenu(title: String, detail: String, enabled: Boolean = true, onClick: () -> Unit) {
    val grouped = LocalGroupedMenu.current
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(if (grouped) 0.dp else 20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = Pale, shape = RoundedCornerShape(12.dp)) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { AdvisorNavIcon(if (title.contains("카드") || title.contains("금융")) 1 else if (title.contains("계정") || title.contains("동의")) 0 else 2, true) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", fontSize = 24.sp, color = Color(0xFF9AA8BB))
        }
    }
}

@Composable internal fun CompareCallout(enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = Blue, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Canvas(Modifier.size(32.dp)) {
                drawRoundRect(Color.White, topLeft = Offset(2.dp.toPx(), 5.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width - 4.dp.toPx(), size.height - 10.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()), style = Stroke(1.7.dp.toPx()))
                drawLine(Color.White, Offset(3.dp.toPx(), 13.dp.toPx()), Offset(size.width - 3.dp.toPx(), 13.dp.toPx()), strokeWidth = 1.7.dp.toPx())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("결제 전 카드 비교", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("이번 혜택과 다음 달 실적을 함께", style = MaterialTheme.typography.bodySmall, color = Color(0xFFDDE7FF))
            }
            Text("›", color = Color.White, fontSize = 26.sp)
        }
    }
}
