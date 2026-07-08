package com.example.yibei.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.yibei.R

class CalendarAdapter(
    private val onDateClick: (String) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.DayVH>() {

    data class DayItem(
        val date: String,       // "yyyy-MM-dd"
        val day: Int,           // 日期数字
        val isCurrentMonth: Boolean,
        val isToday: Boolean,
        val isSelected: Boolean,
        val importance: Int     // 0=无, 1=低(蓝), 2=中(橙), 3=高(红)
    )

    private val days = mutableListOf<DayItem>()

    fun submitList(list: List<DayItem>) {
        days.clear()
        days.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return DayVH(v)
    }

    override fun getItemCount() = days.size

    override fun onBindViewHolder(holder: DayVH, position: Int) {
        val item = days[position]
        holder.tvDay.text = if (item.isCurrentMonth) item.day.toString() else ""

        // 背景
        val bg = GradientDrawable().apply {
            cornerRadius = 16f
        }
        when {
            item.isSelected -> bg.setColor(0xFF2196F3.toInt())
            item.isToday -> bg.setStroke(2, 0xFF2196F3.toInt())
            else -> bg.setColor(Color.TRANSPARENT)
        }
        holder.tvDay.background = bg

        // 文字颜色
        when {
            item.isSelected -> holder.tvDay.setTextColor(Color.WHITE)
            !item.isCurrentMonth -> holder.tvDay.setTextColor(0xFFCFD8DC.toInt())
            item.isToday -> holder.tvDay.setTextColor(0xFF2196F3.toInt())
            else -> holder.tvDay.setTextColor(0xFF37474F.toInt())
        }

        // 日程标记（根据重要性显示不同颜色）
        // importance: 0=无, 1=低(蓝), 2=中(橙), 3=高(红)
        if (item.importance > 0 && item.isCurrentMonth) {
            holder.dotIndicator.visibility = View.VISIBLE
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(when (item.importance) {
                    3 -> 0xFFE53935.toInt()  // 高-红
                    2 -> 0xFFFF9800.toInt()  // 中-橙
                    else -> 0xFF2196F3.toInt() // 低-蓝
                })
            }
            holder.dotIndicator.background = dotBg
        } else {
            holder.dotIndicator.visibility = View.GONE
        }

        // 点击
        holder.itemView.setOnClickListener {
            if (item.isCurrentMonth) onDateClick(item.date)
        }
    }

    class DayVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvDay: TextView = view.findViewById(R.id.tv_day)
        val dotIndicator: View = view.findViewById(R.id.dot_indicator)
    }
}
