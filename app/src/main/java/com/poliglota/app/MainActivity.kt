package com.poliglota.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.Executors

data class AppItem(
    val label: String,
    val packageName: String,
    val isGame: Boolean,
    val info: ApplicationInfo
)

class AppAdapter(
    private val pm: PackageManager,
    private val onClick: (AppItem) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items: List<AppItem> = emptyList()
    private var selected: String? = null

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardApp)
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvName)
        val pkg: TextView = view.findViewById(R.id.tvPackage)
    }

    fun submit(list: List<AppItem>) {
        items = list
        notifyDataSetChanged()
    }

    fun select(packageName: String) {
        selected = packageName
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.label
        holder.pkg.text = item.packageName
        holder.icon.setImageDrawable(item.info.loadIcon(pm))

        val density = holder.itemView.resources.displayMetrics.density
        if (item.packageName == selected) {
            holder.card.strokeColor = Color.parseColor("#7C4DFF")
            holder.card.strokeWidth = (3 * density).toInt()
        } else {
            holder.card.strokeWidth = 0
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }
}

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private var allApps: List<AppItem> = emptyList()

    private lateinit var adapter: AppAdapter
    private lateinit var etSearch: TextInputEditText
    private lateinit var chipGroup: ChipGroup
    private lateinit var tvCount: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var cardSelected: MaterialCardView
    private lateinit var ivSelectedIcon: ImageView
    private lateinit var tvSelectedName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etSearch = findViewById(R.id.etSearch)
        chipGroup = findViewById(R.id.chipGroup)
        tvCount = findViewById(R.id.tvCount)
        tvEmpty = findViewById(R.id.tvEmpty)
        cardSelected = findViewById(R.id.cardSelected)
        ivSelectedIcon = findViewById(R.id.ivSelectedIcon)
        tvSelectedName = findViewById(R.id.tvSelectedName)

        val recycler: RecyclerView = findViewById(R.id.recycler)
        adapter = AppAdapter(packageManager) { item -> onAppSelected(item) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        etSearch.doAfterTextChanged { applyFilter() }
        chipGroup.setOnCheckedStateChangeListener { _, _ -> applyFilter() }

        loadApps()
    }

    private fun loadApps() {
        tvCount.text = ""
        executor.execute {
            val list = queryApps()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                allApps = list
                applyFilter()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun queryApps(): List<AppItem> {
        val pm = packageManager
        val installed = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            pm.getInstalledApplications(0)
        }
        return installed
            .filter { it.packageName != packageName }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { info ->
                val game = (Build.VERSION.SDK_INT >= 26 &&
                    info.category == ApplicationInfo.CATEGORY_GAME) ||
                    (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                AppItem(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.packageName,
                    isGame = game,
                    info = info
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun applyFilter() {
        val query = etSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        val onlyGames = chipGroup.checkedChipId == R.id.chipGames

        val filtered = allApps.filter { app ->
            (!onlyGames || app.isGame) &&
                (query.isEmpty() ||
                    app.label.lowercase().contains(query) ||
                    app.packageName.lowercase().contains(query))
        }
        adapter.submit(filtered)
        tvCount.text = getString(R.string.apps_count, filtered.size)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onAppSelected(item: AppItem) {
        adapter.select(item.packageName)
        ivSelectedIcon.setImageDrawable(item.info.loadIcon(packageManager))
        tvSelectedName.text = item.label
        cardSelected.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
