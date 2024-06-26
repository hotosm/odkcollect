package org.odk.collect.maps.layers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.odk.collect.maps.databinding.OfflineMapLayersPickerItemBinding
import org.odk.collect.strings.localization.getLocalizedString

class OfflineMapLayersPickerAdapter(
    private val layers: List<ReferenceLayer>,
    private var selectedLayerId: String?,
    private val onSelectedLayerChanged: (String?) -> Unit
) : RecyclerView.Adapter<OfflineMapLayersPickerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = OfflineMapLayersPickerItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.radioButton.setChecked(false)
        if (position == 0) {
            holder.binding.radioButton.text = holder.binding.root.context.getLocalizedString(org.odk.collect.strings.R.string.none)
            if (selectedLayerId == null) {
                holder.binding.radioButton.setChecked(true)
            }
        } else {
            holder.binding.radioButton.text = layers[position - 1].name
            if (selectedLayerId == layers[position - 1].id) {
                holder.binding.radioButton.setChecked(true)
            }
        }
        holder.binding.radioButton.setOnClickListener {
            if (position == 0) {
                onSelectedLayerChanged(null)
            } else {
                onSelectedLayerChanged(layers[position - 1].id)
            }
        }
    }

    override fun getItemCount() = layers.size + 1

    class ViewHolder(val binding: OfflineMapLayersPickerItemBinding) : RecyclerView.ViewHolder(binding.root)
}
