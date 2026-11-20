package cl.agroalert.araucania

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class ConnectionActivity : BaseActivity() {
    private lateinit var status: TextView
    private lateinit var connect: MaterialButton
    private lateinit var key: TextInputEditText
    private lateinit var btStatus: TextView
    private lateinit var btList: LinearLayout
    private lateinit var btPanel: LinearLayout
    private lateinit var wifiPanel: LinearLayout
    private var navigating = false
    private val askBtPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) listBonded() else btStatus.text = "Sin permisos de Bluetooth no puedo conectar con el módulo."
    }
    private fun hasBtPermissions() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
         checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
    private val enableBt = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) listBonded() else btStatus.text = "Activa el Bluetooth para buscar el módulo."
    }
    private val observer: (NodeState) -> Unit = { state ->
        if (::status.isInitialized) {
            status.text = state.message
            connect.isEnabled = !state.connecting
        }
        if (::btStatus.isInitialized) btStatus.text = state.message
        // Entrar solo con la primera lectura válida: si el baud está mal, el error se ve aquí y no en el dashboard.
        if (state.connected && state.telemetry != null && !navigating) {
            navigating = true
            key.text?.clear()
            startActivity(Intent(this, DashboardActivity::class.java))
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        val prefs = getSharedPreferences("connection", MODE_PRIVATE)
        page("01 / Conexión", "Enlaza tu nodo.", "Elige Bluetooth (Arduino + HC-05) o Wi-Fi (nodo o simulador) con los iconos de arriba.")
        tabBar(listOf("Bluetooth", "Wi-Fi"), listOf(R.drawable.ic_bluetooth, R.drawable.ic_wifi)) { select(it) }
        btPanel = panel()
        wifiPanel = panel()

        label("BLUETOOTH · ARDUINO + HC-05", 11, muted, true, btPanel)
        label("Enciende el Arduino. El PIN del HC-05 se pide UNA sola vez al vincular, en Ajustes de Android, nunca aquí.", 13, muted, parent = btPanel)
        btStatus = label("Listo para conectar por Bluetooth", 13, muted, parent = btPanel)
        button("Buscar módulos emparejados", parent = btPanel) { listBonded() }
        btList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        btPanel.addView(btList, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val info = card(wifiPanel)
        label("Wi-Fi · red local (simulador o nodo)", 18, ink, true, info)
        gap(8, info)
        label("Cada celular abre su propia conexión. Ambos reciben el mismo estado del sistema.", 14, muted, parent = info)
        val host = field("Dirección IP del nodo", initial = prefs.getString("host", "") ?: "", parent = wifiPanel)
        val port = field("Puerto TCP", initial = prefs.getInt("port", 5000).toString(), numeric = true, parent = wifiPanel)
        key = field("Clave del nodo (CLAVE_CONTROL del simulador)", password = true, parent = wifiPanel)
        label("Esta clave la entrega el nodo o el simulador. Es diferente de tu contraseña de inicio de sesión.", 13, muted, parent = wifiPanel)
        gap(parent = wifiPanel)
        status = label("Listo para conectar", 14, green, parent = wifiPanel)
        gap(parent = wifiPanel)
        connect = button("Conectar por Wi-Fi", parent = wifiPanel) {
            val address = (host.text?.toString() ?: "").trim()
            val p = port.text?.toString()?.toIntOrNull()
            val token = (key.text?.toString() ?: "").trim()
            val validIp = address.split('.').let { parts -> parts.size == 4 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) && (it.toIntOrNull() ?: -1) in 0..255 } }
            if (!validIp || p == null || p !in 1..65535) {
                status.text = "Escribe una IPv4 válida (ej. 192.168.1.25) y un puerto entre 1 y 65535."
            } else if (!token.matches(Regex("[A-Za-z0-9_-]{16,128}"))) {
                status.text = "La clave del nodo debe tener entre 16 y 128 letras, números, guiones o guiones bajos."
            } else {
                prefs.edit().putString("host", address).putInt("port", p).apply()
                agro.node.connect(address, p, token)
            }
        }
        button("Cerrar sesión", secondary = true) { logout() }
        button(if (agro.darkMode) "Modo claro" else "Modo oscuro", secondary = true) { toggleDarkMode() }
        gap(16, wifiPanel)
        label("¿Estás probando sin Arduino?\nInicia simulator/server.py en el computador y usa la IP y la clave que muestra la terminal.", 13, muted, parent = wifiPanel)
        btPanel.visibility = View.VISIBLE
    }
    private fun select(index: Int) {
        btPanel.visibility = if (index == 0) View.VISIBLE else View.GONE
        wifiPanel.visibility = if (index == 1) View.VISIBLE else View.GONE
    }
    private fun listBonded() {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) { btStatus.text = "Este celular no tiene Bluetooth."; return }
        if (!hasBtPermissions()) {
            askBtPermission.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
            return
        }
        if (!adapter.isEnabled) {
            btStatus.text = "El Bluetooth del celular está apagado. Actívalo para buscar el módulo."
            enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        btList.removeAllViews()
        val devices = try {
            adapter.bondedDevices.sortedWith(compareByDescending { it.name?.uppercase()?.contains("HC") == true })
        } catch (_: SecurityException) { emptyList() }
        if (devices.isEmpty()) {
            btStatus.text = "Aún no hay ningún HC-05 vinculado a este celular. Vincúlalo una vez en Ajustes (el PIN lo pide ahí, no la app) y vuelve a intentar."
            button("Abrir Ajustes de Bluetooth para vincular", secondary = true, parent = btList) {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            return
        }
        btStatus.text = "Toca el módulo HC-05 del Arduino:"
        devices.take(8).forEach { device ->
            val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
            button("$name · ${device.address}", secondary = true, parent = btList) { connectBt(device) }
        }
    }
    private fun connectBt(device: BluetoothDevice) {
        if (!hasBtPermissions()) {
            askBtPermission.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
            return
        }
        try { agro.node.connectBluetooth(device) }
        catch (_: SecurityException) { btStatus.text = "Falta el permiso de Bluetooth; actívalo y vuelve a intentar." }
    }
    override fun onStart() {
        super.onStart()
        if (::status.isInitialized) { navigating = false; agro.node.observe(observer) }
    }
    override fun onStop() { agro.node.remove(observer); super.onStop() }
}
