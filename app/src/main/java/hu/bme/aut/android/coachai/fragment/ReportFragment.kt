package hu.bme.aut.android.coachai.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import hu.bme.aut.android.coachai.adapter.CardAdapter
import hu.bme.aut.android.coachai.data.CardItem
import hu.bme.aut.android.coachai.databinding.FragmentReportBinding
import hu.bme.aut.android.coachai.views.MainViewModel

class ReportFragment : Fragment() {

    private var _fragmentReportBinding: FragmentReportBinding? = null
    private val fragmentReportBinding get() = _fragmentReportBinding!!

    private var adapter: CardAdapter? = null
    private val viewModel: MainViewModel by activityViewModels()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Inflate the layout for this fragment
        _fragmentReportBinding =
            FragmentReportBinding.inflate(inflater, container, false)

        return fragmentReportBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val analyzedData = viewModel.analyzedData

        // Set Overall data
        fragmentReportBinding.mostFreqRecVal.text = analyzedData?.mostFrequentRecommendation
        if (analyzedData != null) {
            fragmentReportBinding.bestShotVal.text = analyzedData.bestSwingId.toString()
            fragmentReportBinding.worstShotVal.text = analyzedData.worstSwingId.toString()
        } else {
            fragmentReportBinding.bestShotVal.text = ""
            fragmentReportBinding.worstShotVal.text = ""
        }
        fragmentReportBinding.consistencyVal.text = analyzedData?.consistency
        fragmentReportBinding.meanScoreVal.text = analyzedData?.meanScore.toString()

        // Create cards
        val swings = analyzedData?.detectedSwings
        swings?.let {

            val cardList = mutableListOf<CardItem>()

            for (swing in it) {
                cardList.add(CardItem(swing.id, swing.totalScore, swing.recommendation))
            }

            adapter = CardAdapter(cardList)
        }

        fragmentReportBinding.shotsRecyclerView.layoutManager = GridLayoutManager(context, 2)
        fragmentReportBinding.shotsRecyclerView.adapter = adapter

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _fragmentReportBinding = null
    }


}
