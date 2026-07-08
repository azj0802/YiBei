package com.example.yibei.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.yibei.R
import com.example.yibei.databinding.FragmentDecisionBinding
import com.google.android.material.snackbar.Snackbar

class DecisionFragment : Fragment() {

    private var _binding: FragmentDecisionBinding? = null
    private val binding get() = _binding!!
    private val dimensionViews = mutableListOf<DimensionRow>()
    private val defaultDimensions = listOf("重要性", "紧急程度", "难度", "兴趣", "成本")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDecisionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDefaultDimensions()
        setupListeners()
    }

    private fun setupDefaultDimensions() {
        binding.layoutDimensions.removeAllViews()
        dimensionViews.clear()
        for (dim in defaultDimensions) {
            addDimensionRow(dim)
        }
    }

    private fun addDimensionRow(name: String) {
        val row = layoutInflater.inflate(
            R.layout.item_decision_dimension,
            binding.layoutDimensions,
            false
        )
        val dr = DimensionRow(
            container = row,
            tvName = row.findViewById(R.id.tvDimName),
            seekA = row.findViewById(R.id.seekA),
            seekB = row.findViewById(R.id.seekB),
            tvScoreA = row.findViewById(R.id.tvScoreA),
            tvScoreB = row.findViewById(R.id.tvScoreB)
        )
        dr.tvName.text = name
        dr.seekA.progress = 5
        dr.seekB.progress = 5
        dr.tvScoreA.text = "5"
        dr.tvScoreB.text = "5"

        dr.seekA.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                dr.tvScoreA.text = "$progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        dr.seekB.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                dr.tvScoreB.text = "$progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.layoutDimensions.addView(row)
        dimensionViews.add(dr)
    }

    private fun setupListeners() {
        binding.btnWeigh.setOnClickListener {
            val optionA = binding.etOptionA.text.toString().trim()
            val optionB = binding.etOptionB.text.toString().trim()

            if (optionA.isEmpty() || optionB.isEmpty()) {
                Snackbar.make(binding.root, "请填写两个选项", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            calculateAndShowResult(optionA, optionB)
        }

        binding.btnAddDimension.setOnClickListener {
            val input = binding.etNewDimension.text.toString().trim()
            if (input.isEmpty()) {
                Snackbar.make(binding.root, "请输入维度名称", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addDimensionRow(input)
            binding.etNewDimension.text?.clear()
        }

        binding.btnReset.setOnClickListener {
            binding.etOptionA.text?.clear()
            binding.etOptionB.text?.clear()
            binding.tvResult.text = ""
            binding.tvResult.visibility = View.GONE
            binding.layoutResultBar.visibility = View.GONE
            setupDefaultDimensions()
        }
    }

    private fun calculateAndShowResult(optionA: String, optionB: String) {
        var totalA = 0
        var totalB = 0

        for (dr in dimensionViews) {
            totalA += dr.seekA.progress
            totalB += dr.seekB.progress
        }

        val winner: String
        val barPercent: Int // A的占比，0-100

        if (totalA > totalB) {
            winner = optionA
            barPercent = if (totalA + totalB > 0) (totalA * 100 / (totalA + totalB)) else 50
        } else if (totalB > totalA) {
            winner = optionB
            barPercent = if (totalA + totalB > 0) (totalA * 100 / (totalA + totalB)) else 50
        } else {
            winner = "平局"
            barPercent = 50
        }

        binding.tvResult.visibility = View.VISIBLE
        binding.layoutResultBar.visibility = View.VISIBLE

        if (winner == "平局") {
            binding.tvResult.text = "⚖  两者势均力敌！\n$optionA（${totalA}分） vs $optionB（${totalB}分）"
            binding.viewBarA.layoutParams = (binding.viewBarA.layoutParams as LinearLayout.LayoutParams).apply { weight = 1f }
            binding.viewBarB.layoutParams = (binding.viewBarB.layoutParams as LinearLayout.LayoutParams).apply { weight = 1f }
            binding.tvBarA.text = optionA
            binding.tvBarB.text = optionB
        } else {
            binding.tvResult.text = "🏆  建议选择：「$winner」\n$optionA（${totalA}分） vs $optionB（${totalB}分）"
            binding.viewBarA.layoutParams = (binding.viewBarA.layoutParams as LinearLayout.LayoutParams).apply { weight = barPercent.toFloat() }
            binding.viewBarB.layoutParams = (binding.viewBarB.layoutParams as LinearLayout.LayoutParams).apply { weight = (100 - barPercent).toFloat() }
            binding.tvBarA.text = "$optionA ${totalA}分"
            binding.tvBarB.text = "$optionB ${totalB}分"

            if (totalA > totalB) {
                binding.viewBarA.setBackgroundColor(Color.parseColor("#4CAF50"))
                binding.viewBarB.setBackgroundColor(Color.parseColor("#E0E0E0"))
            } else {
                binding.viewBarA.setBackgroundColor(Color.parseColor("#E0E0E0"))
                binding.viewBarB.setBackgroundColor(Color.parseColor("#4CAF50"))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class DimensionRow(
        val container: View,
        val tvName: TextView,
        val seekA: SeekBar,
        val seekB: SeekBar,
        val tvScoreA: TextView,
        val tvScoreB: TextView
    )
}
