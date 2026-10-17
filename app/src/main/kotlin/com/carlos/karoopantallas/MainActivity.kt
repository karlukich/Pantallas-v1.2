package com.carlos.karoopantallas

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView

/** Pantalla mínima que se ve al abrir la app desde el Karoo: explica cómo añadir las pantallas. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val text = TextView(this).apply {
            setText(R.string.main_text)
            setTextColor(Color.parseColor("#F2F5F8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(pad, pad, pad, pad)
        }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Color.parseColor("#0C0F13"))
                addView(text)
            },
        )
    }
}
