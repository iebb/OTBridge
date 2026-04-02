package ee.nekoko.nbridge

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), NBridgeBackend.Listener {
    private lateinit var backend: NBridgeBackend
    private lateinit var slotContainer: LinearLayout
    private lateinit var openMobileStatusIconView: ImageView
    private lateinit var openMobileStatusView: TextView
    private lateinit var telephonyStatusIconView: ImageView
    private lateinit var telephonyStatusView: TextView
    private lateinit var slotHintView: TextView
    private lateinit var activeConnectionsView: TextView
    private lateinit var loadingView: View
    private val mainHandler = Handler(Looper.getMainLooper())
    private val simRefreshRunnable = Runnable { refreshSlots() }
    @Volatile
    private var renderGeneration = 0
    @Volatile
    private var lastHandledSimGeneration = 0L
    private var hasRenderedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        NBridgeKeepAliveService.start(applicationContext)
        backend = NBridgeBackend.getInstance(applicationContext)
        setContentView(R.layout.activity_main)

        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        val statusBarColor = MaterialColors.getColor(
            window.decorView,
            com.google.android.material.R.attr.colorSurfaceContainer,
        )
        window.statusBarColor = statusBarColor
        window.navigationBarColor = MaterialColors.getColor(
            window.decorView,
            com.google.android.material.R.attr.colorSurface,
        )
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        setSupportActionBar(topAppBar)
        ViewCompat.setOnApplyWindowInsetsListener(topAppBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                bars.top,
                view.paddingRight,
                view.paddingBottom,
            )
            WindowInsetsCompat.Builder(insets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars(),
                    Insets.of(bars.left, 0, bars.right, bars.bottom),
                )
                .build()
        }
        findViewById<ImageView>(R.id.heroIcon).setImageResource(R.mipmap.ic_launcher)
        slotContainer = findViewById(R.id.slotContainer)
        openMobileStatusIconView = findViewById(R.id.openMobileStatusIcon)
        openMobileStatusView = findViewById(R.id.openMobileStatus)
        telephonyStatusIconView = findViewById(R.id.telephonyStatusIcon)
        telephonyStatusView = findViewById(R.id.telephonyStatus)
        slotHintView = findViewById(R.id.slotHint)
        activeConnectionsView = findViewById(R.id.activeConnections)
        loadingView = findViewById(R.id.loadingView)
    }

    override fun onResume() {
        super.onResume()
        refreshSlots()
    }

    override fun onStart() {
        super.onStart()
        backend.addListener(this)
    }

    override fun onStop() {
        mainHandler.removeCallbacks(simRefreshRunnable)
        backend.removeListener(this)
        super.onStop()
    }

    private fun refreshSlots() {
        val generation = ++renderGeneration
        if (!hasRenderedOnce) {
            loadingView.visibility = View.VISIBLE
        }
        thread(name = "nbridge-slot-refresh") {
            val allSlots = backend.listAllSlots()
            val activeConnections = queryActiveConnections()
            runOnUiThread {
                if (generation != renderGeneration || isFinishing || isDestroyed) return@runOnUiThread
                renderSlots(allSlots, activeConnections)
                loadingView.visibility = View.GONE
                hasRenderedOnce = true
            }
        }
    }

    override fun onSimStateChanged(action: String?, generation: Long) {
        if (generation <= lastHandledSimGeneration) {
            return
        }
        lastHandledSimGeneration = generation
        Log.i(TAG, "SIM state change received from backend: $action (#$generation)")
        mainHandler.removeCallbacks(simRefreshRunnable)
        mainHandler.postDelayed(simRefreshRunnable, SIM_REFRESH_DEBOUNCE_MS)
    }

    private fun queryActiveConnections(): List<ActiveConnection> {
        val result = contentResolver.call(
            android.net.Uri.parse("content://${NBridgeContract.AUTHORITY}"),
            NBridgeContract.METHOD_LIST_ACTIVE_CONNECTIONS,
            null,
            null,
        ) ?: return emptyList()
        if (!result.getBoolean(NBridgeContract.RESULT_OK, false)) {
            return emptyList()
        }
        val bundles = result.getParcelableArrayList<Bundle>(
            NBridgeContract.RESULT_ACTIVE_CONNECTIONS,
            Bundle::class.java,
        ) ?: return emptyList()
        return bundles.mapNotNull { bundle ->
            val connectionId = bundle.getString("connectionId") ?: return@mapNotNull null
            val slotId = bundle.getString("slotId") ?: return@mapNotNull null
            val transport = bundle.getString("transport") ?: return@mapNotNull null
            val aid = bundle.getString("aid") ?: return@mapNotNull null
            ActiveConnection(
                connectionId = connectionId,
                slotId = slotId,
                transport = transport,
                channelNumber = bundle.getInt("channelNumber"),
                aid = aid,
            )
        }
    }

    private fun renderSlots(
        allSlots: List<SlotDescriptor>,
        activeConnections: List<ActiveConnection>,
    ) {
        bindTransportStatus(
            iconView = openMobileStatusIconView,
            textView = openMobileStatusView,
            label = getString(R.string.transport_open_mobile),
            slots = allSlots,
            transport = "omapi",
        )
        bindTransportStatus(
            iconView = telephonyStatusIconView,
            textView = telephonyStatusView,
            label = getString(R.string.transport_telephony),
            slots = allSlots,
            transport = "tmapi",
        )
        slotHintView.text = when {
            allSlots.any { it.transport == "omapi" } -> getString(R.string.slot_section_subtitle)
            allSlots.any { it.transport == "tmapi" } -> getString(R.string.slot_section_subtitle_tmapi_only)
            else -> getString(R.string.slot_section_subtitle_empty)
        }
        activeConnectionsView.text = if (activeConnections.isEmpty()) {
            getString(R.string.active_connections_empty)
        } else {
            val slotLabels = allSlots.associate { it.id to it.displayName }
            activeConnections.joinToString("\n") { connection ->
                getString(
                    R.string.active_connection_item,
                    slotLabels[connection.slotId] ?: formatSlotLabel(connection),
                    connection.channelNumber,
                    connection.aid,
                )
            }
        }

        slotContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        allSlots.forEach { slot ->
            val card = inflater.inflate(R.layout.item_slot_toggle, slotContainer, false) as MaterialCardView
            val title = card.findViewById<TextView>(R.id.slotTitle)
            val subtitle = card.findViewById<TextView>(R.id.slotSubtitle)
            val transport = card.findViewById<TextView>(R.id.slotTransport)
            val toggle = card.findViewById<MaterialSwitch>(R.id.slotSwitch)

            title.text = slot.displayName
            subtitle.text = buildSubtitle(slot)
            transport.text = slot.transport.uppercase()
            val badgeColor = MaterialColors.getColor(
                transport,
                if (slot.transport == "tmapi") {
                    com.google.android.material.R.attr.colorPrimaryContainer
                } else {
                    com.google.android.material.R.attr.colorSecondaryContainer
                },
            )
            val badgeTextColor = MaterialColors.getColor(
                transport,
                if (slot.transport == "tmapi") {
                    com.google.android.material.R.attr.colorOnPrimaryContainer
                } else {
                    com.google.android.material.R.attr.colorOnSecondaryContainer
                },
            )
            transport.background.setTint(badgeColor)
            transport.setTextColor(badgeTextColor)
            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = backend.isSlotEnabled(slot.id)
            toggle.setOnCheckedChangeListener { _, isChecked ->
                backend.setSlotEnabled(slot.id, isChecked)
                refreshSlots()
            }

            slotContainer.addView(card)
        }
    }

    private fun bindTransportStatus(
        iconView: ImageView,
        textView: TextView,
        label: String,
        slots: List<SlotDescriptor>,
        transport: String,
    ) {
        val transportSlots = slots.filter { it.transport == transport }
        val total = transportSlots.size
        val enabled = transportSlots.count { backend.isSlotEnabled(it.id) }
        @DrawableRes val iconRes = if (enabled > 0) {
            R.drawable.ic_status_check
        } else {
            R.drawable.ic_status_cross
        }
        iconView.setImageResource(iconRes)
        textView.text = getString(R.string.transport_status, label, enabled, total)
    }

    private fun buildSubtitle(slot: SlotDescriptor): String {
        val parts = mutableListOf<String>()
        slot.readerName?.let { parts += it }
        slot.slotIndex?.let {
            parts += getString(R.string.slot_position, it + 1, slot.portIndex ?: 0)
        }
        parts += if (slot.present) {
            getString(R.string.slot_state_present)
        } else {
            getString(R.string.slot_state_absent)
        }
        if (slot.isEuicc == true) {
            parts += getString(R.string.slot_state_euicc)
        }
        return parts.joinToString("  •  ")
    }

    private fun formatSlotLabel(connection: ActiveConnection): String {
        return when {
            connection.transport == "omapi" -> {
                val slotName = connection.slotId.removePrefix("omapi:")
                "O-$slotName"
            }
            connection.transport == "tmapi" -> {
                val parts = connection.slotId.split(':')
                if (parts.size >= 5) {
                    val slotIndex = parts[2].toIntOrNull()
                    val portIndex = parts[4].toIntOrNull()
                    if (slotIndex != null) {
                        return if (portIndex != null && portIndex > 0) {
                            "T-SIM${slotIndex + 1}p$portIndex"
                        } else {
                            "T-SIM${slotIndex + 1}"
                        }
                    }
                }
                connection.slotId
            }
            else -> connection.slotId
        }
    }

    companion object {
        private const val TAG = "NBridgeActivity"
        private const val SIM_REFRESH_DEBOUNCE_MS = 400L
    }
}
