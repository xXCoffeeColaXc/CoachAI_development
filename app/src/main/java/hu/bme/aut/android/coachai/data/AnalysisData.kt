package hu.bme.aut.android.coachai.data

data class AnalysisData(
    val mostFrequentRecommendation: String?,
    val bestSwingId: Int,
    val worstSwingId: Int,
    val consistency: String,
    val meanScore: Double,
    val detectedSwings: MutableList<SwingData>
)

data class SwingData(
    val id: Int,
    val totalScore: Int,
    val recommendation: String
)