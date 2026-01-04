package dev.matsyshyn.smartparkingsystem.ui.rules

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.matsyshyn.smartparkingsystem.R
import dev.matsyshyn.smartparkingsystem.data.model.AutomationRule
import dev.matsyshyn.smartparkingsystem.data.model.ComparisonOperator
import dev.matsyshyn.smartparkingsystem.data.model.DeviceType
import dev.matsyshyn.smartparkingsystem.databinding.ItemRuleBinding

class RulesAdapter(
    private val onToggleEnabled: (AutomationRule) -> Unit,
    private val onEdit: (AutomationRule) -> Unit,
    private val onDelete: (AutomationRule) -> Unit
) : ListAdapter<AutomationRule, RulesAdapter.RuleViewHolder>(RuleDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val binding = ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RuleViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class RuleViewHolder(private val binding: ItemRuleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(rule: AutomationRule) {
            binding.tvRuleName.text = rule.ruleName
            binding.switchRuleEnabled.isChecked = rule.enabled
            
            val operatorSymbol = when (rule.operator) {
                ComparisonOperator.LESS_THAN -> "<"
                ComparisonOperator.LESS_OR_EQUAL -> "<="
                ComparisonOperator.GREATER_THAN -> ">"
                ComparisonOperator.GREATER_OR_EQUAL -> ">="
            }
            
            binding.tvRuleCondition.text = "${rule.sensorType.name} $operatorSymbol ${rule.threshold}"
            
            val actionText = when (rule.deviceType) {
                DeviceType.DIRECTION_PANELS -> "Увімкнути панелі (яскравість: ${rule.actionBrightness}%)"
                DeviceType.VENTILATION -> "Увімкнути вентиляцію (швидкість: ${rule.actionFanSpeed})"
                DeviceType.HEATING -> "Увімкнути обігрів (потужність: ${rule.actionHeatingPower})"
            }
            binding.tvRuleAction.text = actionText
            
            binding.switchRuleEnabled.setOnCheckedChangeListener { _, _ ->
                onToggleEnabled(rule)
            }
            
            binding.btnEditRule.setOnClickListener {
                onEdit(rule)
            }
            
            binding.btnDeleteRule.setOnClickListener {
                onDelete(rule)
            }
        }
    }
    
    class RuleDiffCallback : DiffUtil.ItemCallback<AutomationRule>() {
        override fun areItemsTheSame(oldItem: AutomationRule, newItem: AutomationRule): Boolean {
            return oldItem.ruleId == newItem.ruleId
        }
        
        override fun areContentsTheSame(oldItem: AutomationRule, newItem: AutomationRule): Boolean {
            return oldItem == newItem
        }
    }
}





