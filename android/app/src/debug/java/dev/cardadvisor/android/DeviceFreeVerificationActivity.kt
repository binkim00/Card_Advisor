package dev.cardadvisor.android
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import org.json.JSONObject
class DeviceFreeVerificationActivity:ComponentActivity(){
 companion object{var client:ApiClient?=null;var entry:JSONObject?=null;var screen="CLASSIFY"}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);setContent{AdvisorTheme{Surface{var done by remember{mutableStateOf(false)};if(screen=="ADVISOR")AdvisorApp(clientOverride=requireNotNull(client)) else if(screen=="CLASSIFY"){if(done)Text("저장 완료") else ClassificationEditor(requireNotNull(client),requireNotNull(entry),{}, {},{}, {done=true},{done=true})}else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){if(screen=="FINANCE")FinanceEditor(requireNotNull(client),{},{},{}) else FixedCostEditor(requireNotNull(client),{}, {},{}, {})}}}}
}
}
