package com.example.yibei.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yibei.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject

class GameScheduleFragment : Fragment() {

    private var recycler: RecyclerView? = null
    private var tvEmpty: TextView? = null
    private lateinit var prefs: SharedPreferences
    private val games = mutableListOf<GameItem>()
    private lateinit var adapter: GameAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_game_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(R.id.recycler_games)
        tvEmpty = view.findViewById(R.id.tv_empty)
        prefs = requireContext().getSharedPreferences("game_prefs", Context.MODE_PRIVATE)

        createNotificationChannel()

        adapter = GameAdapter(games, { game -> onStartTimer(game) }, { game -> onEditGame(game) })
        recycler!!.layoutManager = LinearLayoutManager(requireContext())
        recycler!!.adapter = adapter

        loadGames()

        view.findViewById<FloatingActionButton>(R.id.fab_add)
            .setOnClickListener { showAddDialog() }

        setupSwipeDelete()

        countdownRunnable = object : Runnable {
            override fun run() {
                adapter.notifyDataSetChanged()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        countdownRunnable?.let { handler.post(it) }
    }

    override fun onPause() {
        super.onPause()
        countdownRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroyView() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        games.forEach { it.handler?.removeCallbacksAndMessages(null) }
        super.onDestroyView()
        recycler = null
        tvEmpty = null
    }

    private fun loadGames() {
        games.clear()
        val json = prefs.getString("games", "[]") ?: "[]"
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val game = GameItem(
                id = obj.getLong("id"),
                name = obj.getString("name"),
                staminaCap = obj.getInt("staminaCap"),
                recoveryMinutes = obj.getInt("recoveryMinutes"),
                startTime = obj.optLong("startTime", 0),
                handler = null
            )
            if (game.startTime > 0) {
                val elapsed = System.currentTimeMillis() - game.startTime
                val totalMs = game.staminaCap.toLong() * game.recoveryMinutes * 60 * 1000
                if (elapsed < totalMs) {
                    scheduleNotification(game)
                }
            }
            games.add(game)
        }
        adapter.notifyDataSetChanged()
        updateVisibility()
    }

    private fun saveGames() {
        val arr = JSONArray()
        for (g in games) {
            val obj = JSONObject()
            obj.put("id", g.id)
            obj.put("name", g.name)
            obj.put("staminaCap", g.staminaCap)
            obj.put("recoveryMinutes", g.recoveryMinutes)
            obj.put("startTime", g.startTime)
            arr.put(obj)
        }
        prefs.edit().putString("games", arr.toString()).apply()
    }

    private fun updateVisibility() {
        val rv = recycler ?: return
        val tv = tvEmpty ?: return
        if (games.isEmpty()) {
            tv.visibility = View.VISIBLE
            rv.visibility = View.GONE
        } else {
            tv.visibility = View.GONE
            rv.visibility = View.VISIBLE
        }
    }

    private fun onStartTimer(game: GameItem) {
        game.startTime = System.currentTimeMillis()
        game.handler?.removeCallbacksAndMessages(null)
        game.handler = Handler(Looper.getMainLooper())
        scheduleNotification(game)
        saveGames()
        adapter.notifyDataSetChanged()
        Snackbar.make(requireView(), "「${game.name}」体力恢复计时已开始", Snackbar.LENGTH_SHORT).show()
    }

    private fun scheduleNotification(game: GameItem) {
        val totalMs = game.staminaCap.toLong() * game.recoveryMinutes * 60 * 1000
        game.handler?.postDelayed({
            sendFullNotification(game)
            game.startTime = 0
            game.handler?.removeCallbacksAndMessages(null)
            game.handler = null
            saveGames()
            adapter.notifyDataSetChanged()
        }, totalMs)
    }

    private fun onEditGame(game: GameItem) {
        showAddDialog(game)
    }

    private fun showAddDialog(editItem: GameItem? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_game, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etGameName)
        val etCap = dialogView.findViewById<TextInputEditText>(R.id.etStaminaCap)
        val etRecovery = dialogView.findViewById<TextInputEditText>(R.id.etRecoveryMinutes)

        if (editItem != null) {
            etName.setText(editItem.name)
            etCap.setText(editItem.staminaCap.toString())
            etRecovery.setText(editItem.recoveryMinutes.toString())
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setTitle(if (editItem != null) "编辑游戏" else "添加游戏")
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val capStr = etCap.text.toString().trim()
                val recoveryStr = etRecovery.text.toString().trim()

                if (name.isEmpty() || capStr.isEmpty() || recoveryStr.isEmpty()) {
                    Snackbar.make(requireView(), "请填写完整信息", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val cap = capStr.toIntOrNull()
                val recovery = recoveryStr.toIntOrNull()
                if (cap == null || cap <= 0 || recovery == null || recovery <= 0) {
                    Snackbar.make(requireView(), "体力上限和恢复时间必须为正整数", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (editItem != null) {
                    editItem.name = name
                    editItem.staminaCap = cap
                    editItem.recoveryMinutes = recovery
                    editItem.handler?.removeCallbacksAndMessages(null)
                    editItem.handler = null
                    editItem.startTime = 0
                } else {
                    games.add(GameItem(
                        id = System.currentTimeMillis(),
                        name = name,
                        staminaCap = cap,
                        recoveryMinutes = recovery,
                        startTime = 0,
                        handler = null
                    ))
                }
                saveGames()
                adapter.notifyDataSetChanged()
                updateVisibility()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupSwipeDelete() {
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val pos = vh.adapterPosition
                val removed = games.removeAt(pos)
                removed.handler?.removeCallbacksAndMessages(null)
                saveGames()
                adapter.notifyItemRemoved(pos)
                updateVisibility()
                Snackbar.make(requireView(), "已删除「${removed.name}」", Snackbar.LENGTH_LONG)
                    .setAction("撤销") {
                        removed.handler = Handler(Looper.getMainLooper())
                        if (removed.startTime > 0) scheduleNotification(removed)
                        games.add(pos, removed)
                        saveGames()
                        adapter.notifyDataSetChanged()
                        updateVisibility()
                    }
                    .show()
            }
        })
        recycler?.let { touchHelper.attachToRecyclerView(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "game_reminder",
                "游戏体力提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "游戏体力恢复完成提醒" }
            requireContext().getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun sendFullNotification(game: GameItem) {
        // Android 13+ 需要运行时检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val notification = NotificationCompat.Builder(requireContext(), "game_reminder")
            .setSmallIcon(R.drawable.ic_water_cup)
            .setContentTitle("体力已回满！")
            .setContentText("「${game.name}」体力已全部恢复，可以继续玩了")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(requireContext()).notify(game.id.toInt(), notification)
    }

    data class GameItem(
        val id: Long,
        var name: String,
        var staminaCap: Int,
        var recoveryMinutes: Int,
        var startTime: Long,
        var handler: Handler?
    )

    inner class GameAdapter(
        private val games: List<GameItem>,
        private val onStart: (GameItem) -> Unit,
        private val onEdit: (GameItem) -> Unit
    ) : RecyclerView.Adapter<GameAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvGameName)
            val tvStatus: TextView = view.findViewById(R.id.tvStatus)
            val tvInfo: TextView = view.findViewById(R.id.tvGameInfo)
            val tvCountdown: TextView = view.findViewById(R.id.tvCountdown)
            val btnStart: Button = view.findViewById(R.id.btnStart)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_game, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val g = games[position]
            holder.tvName.text = g.name
            holder.tvInfo.text = "体力上限 ${g.staminaCap}  ·  每格 ${g.recoveryMinutes}分钟"

            if (g.startTime > 0) {
                val elapsed = System.currentTimeMillis() - g.startTime
                val totalMs = g.staminaCap.toLong() * g.recoveryMinutes * 60 * 1000
                val remaining = totalMs - elapsed

                if (remaining <= 0) {
                    holder.tvStatus.text = "已满"
                    holder.tvStatus.setBackgroundResource(R.drawable.tab_active_bg)
                    holder.tvCountdown.visibility = View.GONE
                    holder.btnStart.text = "重新计时"
                    holder.btnStart.setBackgroundColor(0xFF2196F3.toInt())
                } else {
                    holder.tvStatus.text = "恢复中"
                    holder.tvStatus.setBackgroundResource(R.drawable.tab_inactive_bg)
                    val hours = remaining / 3600000
                    val minutes = (remaining % 3600000) / 60000
                    val seconds = (remaining % 60000) / 1000
                    holder.tvCountdown.text = "体力全满将在 ${hours}小时${minutes}分${seconds}秒 后"
                    holder.tvCountdown.visibility = View.VISIBLE
                    holder.btnStart.text = "重新计时"
                    holder.btnStart.setBackgroundColor(0xFF2196F3.toInt())
                }
            } else {
                holder.tvStatus.text = "未开始"
                holder.tvStatus.setBackgroundResource(R.drawable.tab_inactive_bg)
                holder.tvCountdown.visibility = View.GONE
                holder.btnStart.text = "开始计时"
                holder.btnStart.setBackgroundColor(0xFF4CAF50.toInt())
            }

            holder.btnStart.setOnClickListener { onStart(g) }
            holder.itemView.setOnClickListener { onEdit(g) }
        }

        override fun getItemCount() = games.size
    }
}
