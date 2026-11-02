package com.carlos.karoopantallas

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.carlos.karoopantallas.data.Settings

/**
 * Pantalla de la app en el Karoo: ajustes arriba (se guardan al tocar) e instrucciones abajo.
 * Cada ajuste se pinta solo a partir de [Settings.groups]: los numéricos con − / +,
 * los de sí/no con un interruptor y los de opciones con un botón que va rotando.
 */
class MainActivity : Activity() {

    private val bg = Color.parseColor("#0C0F13")
    private val card = Color.parseColor("#1A2028")
    private val fg = Color.parseColor("#F2F5F8")
    private val muted = Color.parseColor("#AEB8C4")
    private val accent = Color.parseColor("#C8F03C")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        root.addView(label("Ajustes", 24f, fg, bold = true))
        for ((title, items) in Settings.groups) {
            root.addView(label(title, 14f, accent, bold = true).apply { setPadding(0, dp(20), 0, dp(6)) })
            for (item in items) root.addView(row(item))
        }
        root.addView(label("Cómo usarla", 14f, accent, bold = true).apply { setPadding(0, dp(24), 0, dp(6)) })
        root.addView(label(getString(R.string.main_text), 15f, fg))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(root)
        })
    }

    private fun label(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(s: String, onClick: () -> Unit) = TextView(this).apply {
        text = s
        gravity = Gravity.CENTER
        setTextColor(bg)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { setColor(accent); cornerRadius = dp(8).toFloat() }
        minWidth = dp(52)
        minHeight = dp(44)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        setOnClickListener { onClick() }
    }

    /** Una tarjeta: nombre y ayuda a la izquierda; valor y controles a la derecha. */
    private fun row(item: Any): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(card); cornerRadius = dp(10).toFloat() }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val name = label("", 16f, fg, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val value = label("", 18f, accent, bold = true).apply { setPadding(dp(8), 0, dp(8), 0) }
        line.addView(name)

        val help: String
        when (item) {
            is Settings.IntSetting -> {
                name.text = item.label
                help = item.help
                fun show() { value.text = "${item.value} ${item.unit}" }
                line.addView(button("−") { item.set(item.value - item.step); show() })
                line.addView(value)
                line.addView(button("+") { item.set(item.value + item.step); show() })
                show()
            }
            is Settings.BoolSetting -> {
                name.text = item.label
                help = item.help
                lateinit var b: TextView
                fun show() { b.text = if (item.value) "SÍ" else "NO"; b.alpha = if (item.value) 1f else 0.5f }
                b = button("") { item.set(!item.value); show() }
                line.addView(b)
                show()
            }
            is Settings.ChoiceSetting -> {
                name.text = item.label
                help = item.help
                lateinit var b: TextView
                fun show() { b.text = item.options[item.value] }
                b = button("") { item.set((item.value + 1) % item.options.size); show() }
                line.addView(b)
                show()
            }
            else -> help = ""
        }
        box.addView(line)
        if (help.isNotEmpty()) box.addView(label(help, 13f, muted).apply { setPadding(0, dp(6), 0, 0) })
        return box
    }
}
