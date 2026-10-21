package dev.cardadvisor.android
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
/** Non-exported debug audit host. Same personal session; secure display; no capture/export hook. */
class PrivateLedgerAuditActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);setContent{AdvisorTheme{AdvisorApp()}}}
}
