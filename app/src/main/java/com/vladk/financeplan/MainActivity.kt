package com.vladk.financeplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.vladk.financeplan.ui.FinanceApp
import com.vladk.financeplan.ui.FinanceViewModel
import com.vladk.financeplan.ui.theme.FinanceTheme

class MainActivity : ComponentActivity() {

    private val viewModel: FinanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinanceTheme {
                FinanceApp(viewModel)
            }
        }
    }
}
