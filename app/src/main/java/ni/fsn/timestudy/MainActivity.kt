package ni.fsn.timestudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ni.fsn.timestudy.ui.TimeStudyApp
import ni.fsn.timestudy.ui.theme.TimeStudyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimeStudyTheme { TimeStudyApp() }
        }
    }
}
