package cl.agroalert.araucania

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

abstract class BaseActivity : AppCompatActivity() {
    protected val agro get() = application as AgroApplication
    protected lateinit var content: LinearLayout
    protected val ink = Color.rgb(24, 48, 40)
    protected val muted = Color.rgb(91, 111, 102)
    protected val green = Color.rgb(23, 99, 75)
    protected val amber = Color.rgb(145, 88, 0)
    protected val red = Color.rgb(177, 43, 46)
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }

    protected fun page(section: String, title: String, subtitle: String) {
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.rgb(244, 247, 244)) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(28))
        }
        scroll.addView(content)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        label("AGROALERT  /  ARAUCANÍA", 13, green, true)
        gap(24)
        label(section.uppercase(), 11, muted, true)
        label(title, 30, ink, true).setPadding(0, dp(8), 0, dp(8))
        label(subtitle, 15, muted)
        gap(22)
    }

    protected fun label(text: String, size: Int = 16, color: Int = ink, bold: Boolean = false, parent: LinearLayout = content): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setLineSpacing(dp(3).toFloat(), 1f)
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }

    protected fun gap(height: Int = 14, parent: LinearLayout = content) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    protected fun card(parent: LinearLayout = content): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = dp(20).toFloat(); setStroke(dp(1), Color.rgb(222, 231, 223))
            }
        }
        parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        return box
    }

    protected fun field(hint: String, password: Boolean = false, initial: String = "", numeric: Boolean = false): TextInputEditText {
        val box = TextInputLayout(this).apply {
            this.hint = hint
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxBackgroundColor = Color.WHITE
            setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
            if (password) endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val input = TextInputEditText(box.context).apply {
            setSingleLine(true)
            inputType = when {
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                numeric -> InputType.TYPE_CLASS_NUMBER
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            setText(initial)
            if (password) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        box.addView(input, LinearLayout.LayoutParams(-1, -2))
        content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        return input
    }

    protected fun button(text: String, secondary: Boolean = false, action: () -> Unit): MaterialButton {
        val style = if (secondary) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle
        return MaterialButton(this, null, style).apply {
            this.text = text
            isAllCaps = false
            textSize = 15f
            cornerRadius = dp(14)
            minHeight = dp(54)
            setOnClickListener { action() }
            content.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
    }

    protected fun requireSession(): Boolean {
        if (agro.role != null) return true
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
        return false
    }

    protected fun logout() {
        agro.logout()
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }
}
