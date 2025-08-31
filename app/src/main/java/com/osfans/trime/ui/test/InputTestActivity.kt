package com.osfans.trime.ui.test

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.osfans.trime.R

class InputTestActivity : AppCompatActivity() {
    private lateinit var inputEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_test)

        inputEditText = findViewById(R.id.input_edit_text)

        // 自動 focus 到輸入框並顯示鍵盤
        inputEditText.requestFocus()
        inputEditText.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(inputEditText, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }
}
