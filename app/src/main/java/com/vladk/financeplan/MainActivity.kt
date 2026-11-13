package com.vladk.financeplan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.vladk.financeplan.reminder.Reminders
import com.vladk.financeplan.ui.FinanceApp
import com.vladk.financeplan.ui.FinanceViewModel
import com.vladk.financeplan.ui.theme.FinanceTheme

class MainActivity : ComponentActivity() {

    private val viewModel: FinanceViewModel by viewModels()

    /** Goes up by one each time the app is opened from the reminder or the "Add expense" shortcut. */
    private var addExpenseRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Not after the system re-creates the activity: the request was already handled then.
        if (savedInstanceState == null) handle(intent)
        setContent {
            FinanceTheme {
                FinanceApp(viewModel, addExpenseRequests)
            }
        }
    }

    /** The app was already open when the notification or shortcut was tapped. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(Reminders.EXTRA_ADD_EXPENSE, false) == true ||
            intent?.getStringExtra(Reminders.EXTRA_ADD_EXPENSE) == "true"
        ) {
            addExpenseRequests++
        }
    }
}
