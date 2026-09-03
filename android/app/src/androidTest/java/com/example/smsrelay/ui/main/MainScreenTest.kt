package com.example.smsrelay.ui.main

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import com.example.smsrelay.MainActivity
import com.example.smsrelay.data.SimNumberStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun simSettingsValidateSaveAndReopen() {
        val store = SimNumberStore(compose.activity)
        val previous = store.get()
        try {
            compose.onNodeWithText("SIM 号码").performClick()
            compose.onNodeWithText("SIM 1 手机号").performTextReplacement("abc")
            compose.onNodeWithText("保存").assertIsNotEnabled()
            compose.onNodeWithText("SIM 1 手机号").performTextReplacement("+8613800000000")
            compose.onNodeWithText("SIM 2 手机号").performTextReplacement("09000000000")
            compose.onNodeWithText("保存").performClick()
            compose.onNodeWithText("SIM 号码").performClick()
            compose.onNodeWithText("SIM 1 手机号").assertTextContains("+8613800000000")
            compose.onNodeWithText("SIM 2 手机号").assertTextContains("09000000000")
            compose.onNodeWithText("取消").performClick()
        } finally {
            store.save(previous)
        }
    }
}
