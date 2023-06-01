package hu.bme.aut.android.coachai.swing

import hu.bme.aut.android.coachai.actions.HitAction
import hu.bme.aut.android.coachai.actions.PreAction
import hu.bme.aut.android.coachai.actions.RelAction
import hu.bme.aut.android.coachai.data.Recommendation


class Swing (
    private val preAction: PreAction,
    private val hitAction: HitAction,
    private val relAction: RelAction
    ) {

    // returns rounded scores [preScore, hitScore, relScore]
    fun analyzeSwing() : ArrayList<Int> {
        val scores = arrayListOf<Int>()

        scores.add(preAction.analyzeAction().toInt())
        scores.add(hitAction.analyzeAction().toInt())
        scores.add(relAction.analyzeAction().toInt())

        return scores
    }

    // returns each action's recommendations
    // recommendations are calculated on every frame of action
    fun getSwingRecommendations() : MutableList<MutableList<Recommendation>> {
        val swingRecommendation = mutableListOf<MutableList<Recommendation>>()

        swingRecommendation.add(preAction.getActionRecommendations())
        swingRecommendation.add(hitAction.getActionRecommendations())
        swingRecommendation.add(relAction.getActionRecommendations())

        return swingRecommendation
    }

    fun getSwingDuration(): MutableList<Int> {
        val swingDuration = mutableListOf<Int>()

        swingDuration.add(preAction.getActionDuration())
        swingDuration.add(hitAction.getActionDuration())
        swingDuration.add(relAction.getActionDuration())

        return swingDuration
    }

    fun printScores() {

    }

    override fun toString(): String {

        val duration = preAction.inferenceResults.size + hitAction.inferenceResults.size + relAction.inferenceResults.size

        return "Duration: $duration " +
                "preAction: ${preAction.inferenceResults}" +
                "hitAction: ${hitAction.inferenceResults}" +
                "relAction: ${relAction.inferenceResults}"
    }


}