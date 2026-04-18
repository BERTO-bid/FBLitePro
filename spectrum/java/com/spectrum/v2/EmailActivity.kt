package com.spectrum.v2

import android.app.*
import android.content.*
import android.graphics.drawable.ColorDrawable
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class EmailActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email)
        prefs = getSharedPreferences("LitePro", MODE_PRIVATE)
        setupViews()
        applyTheme()
    }

    private fun setupViews() {
        supportActionBar?.title = "Pilih Provider Email"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val tvInfo = findViewById<TextView>(R.id.tvCurrentEmail)
        val curProvider = prefs.getString("preferred_provider","fakeemailpro") ?: "fakeemailpro"
        val providerName = EmailManager.providers.firstOrNull { it.id == curProvider }?.name ?: "FakeEmail.pro"
        tvInfo.text = "Provider aktif: $providerName\nKlik ✉️ di floating = auto generate"

        val listView = findViewById<ListView>(R.id.lvInbox)
        val items = EmailManager.providers.map { "${it.emoji} ${it.name}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice,
            android.R.id.text1, items)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        val curIdx = EmailManager.providers.indexOfFirst { it.id == curProvider }.let { if(it<0) 0 else it }
        listView.setItemChecked(curIdx, true)

        listView.setOnItemClickListener { _, _, pos, _ ->
            val selected = EmailManager.providers[pos]
            prefs.edit().putString("preferred_provider", selected.id).apply()
            tvInfo.text = "✅ Provider: ${selected.name}\nKlik ✉️ di floating = auto generate"
            Toast.makeText(this, "Provider: ${selected.name}", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnGenEmail).setOnClickListener {
            Toast.makeText(this, "Klik ✉️ di floating bar untuk generate email!", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyTheme() {
        val t = Themes.all[prefs.getInt("theme_idx", 0)]
        findViewById<View>(R.id.emailRoot).setBackgroundColor(t.phoneBg)
        supportActionBar?.setBackgroundDrawable(ColorDrawable(t.appBarBg))
        findViewById<Button>(R.id.btnGenEmail).setBackgroundColor(t.accentColor)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
