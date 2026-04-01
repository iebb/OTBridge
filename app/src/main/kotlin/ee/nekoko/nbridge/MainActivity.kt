package ee.nekoko.nbridge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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

class MainActivity : AppCompatActivity() {
    private lateinit var backend: NBridgeBackend
    private lateinit var slotContainer: LinearLayout
    private lateinit var slotCountView: TextView
    private lateinit var providerStatusView: TextView
    private lateinit var slotHintView: TextView
    private lateinit var activeConnectionsView: TextView
    private lateinit var loadingView: View
    @Volatile
    private var renderGeneration = 0

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
        slotCountView = findViewById(R.id.slotCount)
        providerStatusView = findViewById(R.id.providerStatus)
        slotHintView = findViewById(R.id.slotHint)
        activeConnectionsView = findViewById(R.id.activeConnections)
        loadingView = findViewById(R.id.loadingView)
    }

    override fun onResume() {
        super.onResume()
        refreshSlots()
    }

    private fun refreshSlots() {
        val generation = ++renderGeneration
        loadingView.visibility = View.VISIBLE
        thread(name = "nbridge-slot-refresh") {
            val allSlots = backend.listAllSlots()
            val activeConnections = queryActiveConnections()
            runOnUiThread {
                if (generation != renderGeneration || isFinishing || isDestroyed) return@runOnUiThread
                renderSlots(allSlots, activeConnections)
                loadingView.visibility = View.GONE
            }
        }
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
        val enabledSlots = allSlots.count { backend.isSlotEnabled(it.id) }
        slotCountView.text = getString(R.string.slot_count_value, enabledSlots, allSlots.size)
        providerStatusView.text = when {
            allSlots.any { it.transport == "omapi" } -> getString(R.string.provider_status_active)
            allSlots.any { it.transport == "tmapi" } -> getString(R.string.provider_status_partial)
            else -> getString(R.string.provider_status_inactive)
        }
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
}
