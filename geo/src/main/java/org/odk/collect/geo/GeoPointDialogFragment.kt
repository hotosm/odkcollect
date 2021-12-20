package org.odk.collect.geo

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.odk.collect.geo.GeoUtils.formatAccuracy
import org.odk.collect.geo.databinding.GeopointDialogNewBinding
import javax.inject.Inject

class GeoPointDialogFragment : DialogFragment() {

    @Inject
    lateinit var geoPointViewModelFactory: GeoPointViewModelFactory

    var listener: Listener? = null

    private lateinit var binding: GeopointDialogNewBinding
    private lateinit var viewModel: GeoPointViewModel

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val component =
            (context.applicationContext as GeoDependencyComponentProvider).geoDependencyComponent
        component.inject(this)

        listener = context as? Listener

        viewModel =
            ViewModelProvider(
                requireActivity(),
                geoPointViewModelFactory
            ).get(GeoPointViewModel::class.java)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = GeopointDialogNewBinding.inflate(LayoutInflater.from(context))

        val accuracyThreshold = viewModel.accuracyThreshold.toFloat()

        viewModel.currentAccuracy.observe(this) {
            if (it != null) {
                binding.accuracyStatus.setAccuracy(it, accuracyThreshold)
            }
        }

        binding.threshold.text =
            getString(R.string.point_will_be_saved, formatAccuracy(accuracyThreshold))

        viewModel.timeElapsed.observe(this) {
            binding.time.text =
                getString(R.string.time_elapsed, DateUtils.formatElapsedTime(it / 1000))
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setCancelable(false)
            .setPositiveButton(R.string.save) { _, _ -> viewModel.forceLocation() }
            .setNegativeButton(R.string.cancel) { _, _ -> listener?.onCancel() }
            .create()
    }

    interface Listener {
        fun onCancel()
    }
}
