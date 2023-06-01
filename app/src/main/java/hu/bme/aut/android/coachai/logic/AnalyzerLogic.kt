package hu.bme.aut.android.coachai.logic

import android.util.Log
import hu.bme.aut.android.coachai.data.AnalysisData
import hu.bme.aut.android.coachai.data.Recommendation
import hu.bme.aut.android.coachai.data.SwingData
import hu.bme.aut.android.coachai.listener.SwingObserver
import hu.bme.aut.android.coachai.swing.Swing
import kotlin.math.round
import kotlin.math.sqrt

class AnalyzerLogic(
    private val analyzedDataListener: AnalyzedDataListener? = null
) : SwingObserver {

    private val swingHistory = mutableListOf<Swing>() // swings collection
    private val swingScoreHistory = mutableListOf<ArrayList<Int>>() // swingScore = [preActionScore, hitActionScore, relActionScore]
    private val swingTotalScoreHistoryAsc = mutableListOf<Pair<Int, Int>>() // Pair(swingTotalScore = sum(swingScore),insertionCounter)
    private var insertionCounter: Int = 0
    private val swingRecommendationHistory = mutableListOf<MutableList<MutableList<Recommendation>>>() // [ swingRecs [ swingRec [ ActionRec]]]
    var actionRecommendationMessage = mutableListOf<String?>() // recommendation message for all three action to display

    override fun onSwingDetected(swing: Swing) {
        runAnalysis(swing)
        displaySwingScore(swingScoreHistory.size)
        sendAnalysedData()
    }

    private fun sendAnalysedData() {
        // Result
        val resultBundle = AnalysisData(
            mostFrequentRecommendation = getMostFreqRecommendation(),
            bestSwingId = getBestSwingId(),
            worstSwingId = getWorstSwingId(),
            consistency = getConsistency(),
            meanScore = getMeanScore(),
            detectedSwings = createAllSwingData()
        )
        analyzedDataListener?.onAnalysisComplete(resultBundle)
    }

    private fun runAnalysis(swing: Swing) {

        swingHistory.add(swing)

        Log.d("ANALYZER", swing.toString())

        // Go through all action in swing and analyze them
        val swingScore = swing.analyzeSwing()
        swingScoreHistory.add(swingScore)

        // Insert swing score into a ascending list
        insertTotalScoreIntoAscendingList(swingScore)

        // Store swing recommendations
        val swingRecommendation = swing.getSwingRecommendations()
        swingRecommendationHistory.add(swingRecommendation)

        val swingDuration = swing.getSwingDuration()

        // Get most frequent recommendation for each action of a swing
        actionRecommendationMessage = calculateActionMostFrequentRecMessages(swingRecommendation, swingDuration)

        Log.d("ANALYZER/REC_MESSAGE", actionRecommendationMessage.toString())
    }

    // Insert next swing's score into ascending list
    private fun insertTotalScoreIntoAscendingList(swingScore: ArrayList<Int>) {
        val totalScore = swingScore.sum()
        val index = swingTotalScoreHistoryAsc.binarySearch { compareValues(it.first, totalScore) }
        if (index < 0) {
            swingTotalScoreHistoryAsc.add(-index - 1, Pair(totalScore, insertionCounter))
        } else {
            swingTotalScoreHistoryAsc.add(index, Pair(totalScore, insertionCounter))
        }
        insertionCounter++

        Log.d("ANALYZER/ASC", swingTotalScoreHistoryAsc.toString())
    }

    // Create swing data for every detected swing
    private fun createAllSwingData(): MutableList<SwingData> {
        val swingsData = mutableListOf<SwingData>()
        for (i in swingHistory.indices) {
            getMostFrequentRec(swingRecommendationHistory[i].flatten())?.let {
                SwingData(i+1, swingScoreHistory[i].sum(),
                    it)
            }?.let { swingsData.add(it) }
        }
        return swingsData
    }

    // Function to get the most frequent recommendation from a list
    private fun getMostFrequentRec(list: List<Recommendation>) : String? {
        return list.groupingBy { it.message }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }

    // Function to get the most frequent recommendations from a swing
    private fun calculateActionMostFrequentRecMessages(swingRec: MutableList<MutableList<Recommendation>>, swingDuration: MutableList<Int>) : MutableList<String?> {
        val preRecs = swingRec[0]
        val hitRecs = swingRec[1]
        val relRecs = swingRec[2]
        val totalRecsSize = preRecs.size + hitRecs.size + relRecs.size

        Log.d("ANALYZER/REC", "preRec size: ${preRecs.size}")
        Log.d("ANALYZER/REC", "hitRec size: ${hitRecs.size}")
        Log.d("ANALYZER/REC", "relRec size: ${relRecs.size}")
        Log.d("ANALYZER/REC", "totalRec size: $totalRecsSize")

        // TODO("Handle dividing with 0")
        val prePercent = preRecs.size.toDouble() / (swingDuration[0].toDouble()*3)
        val hitPercent = hitRecs.size.toDouble() / (swingDuration[1].toDouble()*3)
        val relPercent = relRecs.size.toDouble() / (swingDuration[2].toDouble()*3)

        Log.d("ANALYZER/REC_MESSAGE/REC_PERCENT", "preRec/maxPreRec : $prePercent")
        Log.d("ANALYZER/REC_MESSAGE/REC_PERCENT", "hitRec/maxHitRec: $hitPercent")
        Log.d("ANALYZER/REC_MESSAGE/REC_PERCENT", "relRec/maxRelRec: $relPercent")
        val threshold = 0.2

        // do not recommend, because it was a good shot
        if (prePercent < threshold) {
            preRecs.clear()
        }
        if (hitPercent < threshold) {
            hitRecs.clear()
        }
        if (relPercent < threshold) {
            relRecs.clear()
        }

        return mutableListOf(getMostFrequentRec(preRecs), getMostFrequentRec(hitRecs), getMostFrequentRec(relRecs))
    }

    // Function to get most recommended message of all swings
    private fun getMostFreqRecommendation(): String? {
        val swingFreqRec = mutableListOf<String>()
        for (swingRec in swingRecommendationHistory) {
            getMostFrequentRec(swingRec.flatten())?.let { swingFreqRec.add(it) }
        }
        return swingFreqRec.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    // TODO("Test this values and create message")
    // peti 54 dev / 13 swing
    // abel 37 dev / 13 swing
    // 90 ha van 400-700 score-k

    private fun getConsistency() : String {
        fun standardDeviation(list: List<Int>): Double {
            if (list.isEmpty()) return 0.0

            // Calculate the mean
            val mean = list.average()

            // Calculate the variance
            var variance = 0.0
            for (num in list) {
                variance += Math.pow(num - mean, 2.0)
            }
            variance /= list.size

            // Standard deviation is the square root of variance
            return sqrt(variance)
        }

        val deviation = standardDeviation(swingTotalScoreHistoryAsc.map { it.first })
        Log.d("ANALYZE", "deviation:$deviation")

        return  when {
            deviation < 50.0 -> "Very good"
            deviation >= 50.0 && deviation < 70 -> "Good"
            deviation >= 70 -> "Poor"
            else -> ""
        }
    }

    private fun getMeanScore(): Double {
        return round(swingTotalScoreHistoryAsc.map { it.first }.average())
    }


    private fun getBestSwingId() : Int {
        return if(swingTotalScoreHistoryAsc.isEmpty()) -1
        else
            swingTotalScoreHistoryAsc[swingTotalScoreHistoryAsc.size-1].second + 1
    }

    private fun getWorstSwingId() : Int {
        return if(swingTotalScoreHistoryAsc.size < 2) -1
        else
            swingTotalScoreHistoryAsc[0].second + 1
    }

    private fun displaySwingScore(index: Int) {
        val scores = swingScoreHistory[index-1]
        val totalScore = scores.sum()
        Log.d("ANALYZER/SCORES", "pre: ${scores[0]}, hit: ${scores[1]}, rel: ${scores[2]}, total: $totalScore")
    }

    fun removeOutliers() {
        TODO("Not implemented yet")
    }

    interface AnalyzedDataListener {
        fun onAnalysisComplete(data: AnalysisData)
    }


}