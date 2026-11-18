package com.example.pubgsens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

data class SensitivityProfile(
    val name: String, val camera: Int, val ads: Int, val redDot: Int,
    val x2: Int, val x3: Int, val x4: Int, val x6: Int, val x8: Int
)

object SensitivityCalculator {
    private val ratios = linkedMapOf(
        "ads" to 0.90, "redDot" to 0.85, "x2" to 0.55, "x3" to 0.45,
        "x4" to 0.35, "x6" to 0.25, "x8" to 0.20
    )
    fun calculateFromCamera(cameraValue: Int): Map<String, Int> =
        ratios.mapValues { (_, r) -> (cameraValue * r).toInt().coerceIn(0, 300) }
}

class ProfileStorage(context: Context) {
    private val prefs = context.getSharedPreferences("sensitivity_profiles", Context.MODE_PRIVATE)

    fun saveProfile(p: SensitivityProfile) {
        val json = JSONObject().apply {
            put("camera", p.camera); put("ads", p.ads); put("redDot", p.redDot)
            put("x2", p.x2); put("x3", p.x3); put("x4", p.x4); put("x6", p.x6); put("x8", p.x8)
        }
        prefs.edit().putString(p.name, json.toString()).apply()
    }

    fun loadProfile(name: String): SensitivityProfile? {
        val raw = prefs.getString(name, null) ?: return null
        val j = JSONObject(raw)
        return SensitivityProfile(name, j.getInt("camera"), j.getInt("ads"), j.getInt("redDot"),
            j.getInt("x2"), j.getInt("x3"), j.getInt("x4"), j.getInt("x6"), j.getInt("x8"))
    }

    fun listProfiles(): List<String> = prefs.all.keys.toList()
}

data class DiagnosticResult(val title: String, val reason: String, val isProblem: Boolean, val settingsIntentAction: String?)

class DiagnosticsEngine(private val activity: Activity) {

    fun runAll(): List<DiagnosticResult> = listOf(checkPowerSaveMode(), checkDataSaver(), checkAnimationScale(), checkRefreshRate())

    private fun checkPowerSaveMode(): DiagnosticResult {
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        val on = pm.isPowerSaveMode
        return DiagnosticResult("Quvvat tejash rejimi",
            if (on) "Yoqilgan — lag berishi mumkin" else "O'chirilgan — muammo yo'q", on,
            Settings.ACTION_BATTERY_SAVER_SETTINGS)
    }

    private fun checkDataSaver(): DiagnosticResult {
        val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val on = cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        return DiagnosticResult("Ma'lumot tejash (Data Saver)",
            if (on) "Yoqilgan — fon trafigini bloklaydi" else "O'chirilgan — muammo yo'q", on,
            Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS)
    }

    private fun checkAnimationScale(): DiagnosticResult {
        val scale = try { Settings.Global.getFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE) } catch (e: Exception) { 1f }
        val high = scale > 1f
        return DiagnosticResult("Animatsiya shkalasi",
            if (high) "1x dan yuqori (${scale}x)" else "Normal (${scale}x)", high,
            Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
    }

    @Suppress("DEPRECATION")
    private fun checkRefreshRate(): DiagnosticResult {
        val rate = activity.windowManager.defaultDisplay.refreshRate
        val low = rate < 90f
        return DiagnosticResult("Ekran yangilanish tezligi",
            if (low) "${rate.toInt()}Hz — 90/120Hz tavsiya etiladi" else "${rate.toInt()}Hz — yaxshi", low,
            Settings.ACTION_DISPLAY_SETTINGS)
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var storage: ProfileStorage
    private lateinit var diagnostics: DiagnosticsEngine
    private lateinit var etCamera: EditText
    private lateinit var etAds: EditText
    private lateinit var etRedDot: EditText
    private lateinit var etX2: EditText
    private lateinit var etX3: EditText
    private lateinit var etX4: EditText
    private lateinit var etX6: EditText
    private lateinit var etX8: EditText
    private lateinit var etProfileName: EditText
    private lateinit var diagnosticsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        storage = ProfileStorage(this)
        diagnostics = DiagnosticsEngine(this)

        etCamera = findViewById(R.id.etCamera); etAds = findViewById(R.id.etAds)
        etRedDot = findViewById(R.id.etRedDot); etX2 = findViewById(R.id.etX2)
        etX3 = findViewById(R.id.etX3); etX4 = findViewById(R.id.etX4)
        etX6 = findViewById(R.id.etX6); etX8 = findViewById(R.id.etX8)
        etProfileName = findViewById(R.id.etProfileName)
        diagnosticsContainer = findViewById(R.id.diagnosticsContainer)

        findViewById<Button>(R.id.btnCalculate).setOnClickListener { calculateFromCamera() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { saveProfile() }
        findViewById<Button>(R.id.btnLoad).setOnClickListener { showProfilesDialog() }
        findViewById<Button>(R.id.btnDiagnose).setOnClickListener { runDiagnostics() }
    }

    private fun calculateFromCamera() {
        val camera = etCamera.text.toString().toIntOrNull()
        if (camera == null) { Toast.makeText(this, "Avval Camera qiymatini kiriting", Toast.LENGTH_SHORT).show(); return }
        val r = SensitivityCalculator.calculateFromCamera(camera)
        etAds.setText(r["ads"].toString()); etRedDot.setText(r["redDot"].toString())
        etX2.setText(r["x2"].toString()); etX3.setText(r["x3"].toString())
        etX4.setText(r["x4"].toString()); etX6.setText(r["x6"].toString()); etX8.setText(r["x8"].toString())
    }

    private fun saveProfile() {
        val name = etProfileName.text.toString().trim()
        if (name.isEmpty()) { Toast.makeText(this, "Profil nomini kiriting", Toast.LENGTH_SHORT).show(); return }
        storage.saveProfile(SensitivityProfile(name,
            etCamera.text.toString().toIntOrNull() ?: 0, etAds.text.toString().toIntOrNull() ?: 0,
            etRedDot.text.toString().toIntOrNull() ?: 0, etX2.text.toString().toIntOrNull() ?: 0,
            etX3.text.toString().toIntOrNull() ?: 0, etX4.text.toString().toIntOrNull() ?: 0,
            etX6.text.toString().toIntOrNull() ?: 0, etX8.text.toString().toIntOrNull() ?: 0))
        Toast.makeText(this, "Saqlandi: $name", Toast.LENGTH_SHORT).show()
    }

    private fun showProfilesDialog() {
        val names = storage.listProfiles()
        if (names.isEmpty()) { Toast.makeText(this, "Hali profil saqlanmagan", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Profilni tanlang").setItems(names.toTypedArray()) { _, which ->
            val p = storage.loadProfile(names[which]) ?: return@setItems
            etProfileName.setText(p.name); etCamera.setText(p.camera.toString()); etAds.setText(p.ads.toString())
            etRedDot.setText(p.redDot.toString()); etX2.setText(p.x2.toString()); etX3.setText(p.x3.toString())
            etX4.setText(p.x4.toString()); etX6.setText(p.x6.toString()); etX8.setText(p.x8.toString())
        }.show()
    }

    private fun runDiagnostics() {
        diagnosticsContainer.removeAllViews()
        for (result in diagnostics.runAll()) {
            val row = layoutInflater.inflate(android.R.layout.simple_list_item_2, diagnosticsContainer, false)
            row.findViewById<TextView>(android.R.id.text1).text = (if (result.isProblem) "⚠ " else "✅ ") + result.title
            row.findViewById<TextView>(android.R.id.text2).text = result.reason
            row.setOnClickListener {
                result.settingsIntentAction?.let { a -> try { startActivity(Intent(a)) } catch (e: Exception) {
                    Toast.makeText(this, "Sozlama sahifasi topilmadi", Toast.LENGTH_SHORT).show() } }
            }
            diagnosticsContainer.addView(row)
        }
    }
}
