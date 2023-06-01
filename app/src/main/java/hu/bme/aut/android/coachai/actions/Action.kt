package hu.bme.aut.android.coachai.actions

import hu.bme.aut.android.coachai.data.InferenceResult
import hu.bme.aut.android.coachai.data.Recommendation

abstract class Action (val inferenceResults: MutableList<InferenceResult>) {

    protected val recommendations = mutableListOf<Recommendation>()
    protected var duration: Int = 0

    protected abstract fun getActionRecommendations() : MutableList<Recommendation>
    protected abstract fun getActionDuration() : Int

    protected abstract fun analyzeAction() : Double

}