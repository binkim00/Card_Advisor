package dev.cardadvisor.android
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
/** Internal debug-only host; never reads the personal session and has no exported entry. */
class LedgerVerificationActivity:ComponentActivity(){
 companion object { var client:ApiClient?=null }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val isolated=requireNotNull(client);window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);setContent{AdvisorTheme{AdvisorApp(isolated)}}}
}
