package com.example.mindcontrol

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    var isSelected: Boolean = false,
    var isFixed: Boolean = false
)

class AppAdapter(
    private var apps: List<AppInfo>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvAppName)
        val checkBox: android.widget.CompoundButton = view.findViewById(R.id.cbSelected)
    }

    fun updateList(newApps: List<AppInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.name.text = app.label
        holder.icon.setImageDrawable(app.icon)
        holder.name.setTextColor(android.graphics.Color.WHITE)
        
        // Social Media Check
        val isSocial = Constants.isSocialMedia(app.packageName)

        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = app.isSelected
        
        // Logic:
        // 1. Fixed (Dialer) -> Checkbox Checked & Disabled (Visual only, Logic below)
        // 2. Social -> Checkbox Unchecked (or previously checked state, but we'll block change) & Enabled (to catch clicks)
        
        // We set enabled to true generally to catch clicks for error messages, 
        // but for Fixed apps we might want to just lock it visualy.
        holder.checkBox.isEnabled = !app.isFixed

        val toggleAction = View.OnClickListener {
            if (app.isFixed) return@OnClickListener
            
            // Social Block
            if (isSocial) {
                // Determine if we are trying to check it
                if (!app.isSelected) { 
                     // User is trying to select it. BLOCK.
                     android.widget.Toast.makeText(holder.itemView.context, "Social media apps are not allowed!", android.widget.Toast.LENGTH_SHORT).show()
                     holder.checkBox.isChecked = false
                     return@OnClickListener
                }
            }
            
            holder.checkBox.toggle()
        }

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (app.isFixed) {
                holder.checkBox.isChecked = true
                return@setOnCheckedChangeListener
            }
            
            if (isSocial && isChecked) {
                 holder.checkBox.isChecked = false
                 android.widget.Toast.makeText(holder.itemView.context, "Social media apps are not allowed!", android.widget.Toast.LENGTH_SHORT).show()
                 return@setOnCheckedChangeListener
            }
            
            app.isSelected = isChecked
            onSelectionChanged()
        }
        
        holder.itemView.setOnClickListener(toggleAction)
        
        // Visual Styling
        if (isSocial) {
            holder.name.setTextColor(android.graphics.Color.GRAY)
        } else {
            holder.name.setTextColor(android.graphics.Color.WHITE)
        }
    }

    override fun getItemCount() = apps.size
}
