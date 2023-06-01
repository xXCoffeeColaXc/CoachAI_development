package hu.bme.aut.android.coachai.actions

import android.util.Log
import hu.bme.aut.android.coachai.data.InferenceResult
import hu.bme.aut.android.coachai.data.LANDMARKS
import hu.bme.aut.android.coachai.data.Recommendation
import hu.bme.aut.android.coachai.utils.Utils
import kotlin.math.max

class HitAction(inferenceResults: MutableList<InferenceResult>) : Action(inferenceResults) {


    public override fun analyzeAction() : Double {

        var score: Double = 1.0
        var actionCount: Int = 0

        // Iterate through all results (frames)
        for(result in inferenceResults) {

            if (result.pose.isNotEmpty()) {
                actionCount += 1

                val rightElbowAngle = Utils.calculateAngle(
                    result.pose[LANDMARKS.RIGHT_WRIST.id],
                    result.pose[LANDMARKS.RIGHT_ELBOW.id],
                    result.pose[LANDMARKS.RIGHT_SHOULDER.id]
                )

                val rightKneeAngle = Utils.calculateAngle(
                    result.pose[LANDMARKS.RIGHT_ANKLE.id],
                    result.pose[LANDMARKS.RIGHT_KNEE.id],
                    result.pose[LANDMARKS.RIGHT_HIP.id]
                )

                val leftKneeAngle = Utils.calculateAngle(
                    result.pose[LANDMARKS.LEFT_ANKLE.id],
                    result.pose[LANDMARKS.LEFT_KNEE.id],
                    result.pose[LANDMARKS.LEFT_HIP.id]
                )

                val rightElbowAngle3D = Utils.calculateAngle3D(
                    result.pose[LANDMARKS.RIGHT_WRIST.id],
                    result.pose[LANDMARKS.RIGHT_ELBOW.id],
                    result.pose[LANDMARKS.RIGHT_SHOULDER.id]
                )

                val rightKneeAngle3D = Utils.calculateAngle3D(
                    result.pose[LANDMARKS.RIGHT_ANKLE.id],
                    result.pose[LANDMARKS.RIGHT_KNEE.id],
                    result.pose[LANDMARKS.RIGHT_HIP.id]
                )

                val leftKneeAngle3D = Utils.calculateAngle3D(
                    result.pose[LANDMARKS.LEFT_ANKLE.id],
                    result.pose[LANDMARKS.LEFT_KNEE.id],
                    result.pose[LANDMARKS.LEFT_HIP.id]
                )

                Log.d("ACTION/HIT", "rightElbowAngle: $rightElbowAngle")
                Log.d("ACTION/HIT", "rightKneeAngle: $rightKneeAngle")
                Log.d("ACTION/HIT", "leftKneeAngle: $leftKneeAngle")

                Log.d("ACTION/HIT3D", "rightElbowAngle3D: $rightElbowAngle3D")
                Log.d("ACTION/HIT3D", "rightKneeAngle3D: $rightKneeAngle3D")
                Log.d("ACTION/HIT3D", "leftKneeAngle3D: $leftKneeAngle3D")

                //  Right arm must be straight
                if (100 < rightElbowAngle3D && rightElbowAngle3D < 120) { // lower bound for misdetection
                    Log.d("ACTION/HIT/RECOMMEND", "Nyújtsd ki a kezed!")
                    recommendations.add(Recommendation(actionCount, "Nyújtsd ki a kezed!"))
                }
                // Calculate score for straight arm
                score += Utils.calculateScore(rightElbowAngle, 120.0)

                // Left or right leg must be bent
                if (leftKneeAngle3D > 150 && rightKneeAngle3D > 150) {
                    Log.d("ACTION/HIT/RECOMMEND", "Hajlítsd be a lábad!")
                    recommendations.add(Recommendation(actionCount, "Hajlítsd be a lábad!"))
                }
                // Calculate score for bent knee, add higher score
                score += max(Utils.calculateScore(leftKneeAngle3D, 140.0), Utils.calculateScore(rightKneeAngle3D, 140.0))

                // Wrist should be lower then the hip
                if (Utils.calculateHigherLandmark(result.pose[LANDMARKS.RIGHT_WRIST.id], result.pose[LANDMARKS.RIGHT_HIP.id]).name == LANDMARKS.RIGHT_HIP.name) {
                    Log.d("ACTION/HIT/RECOMMEND", "Hajolj lejebb!")
                    recommendations.add(Recommendation(actionCount, "Hajolj lejebb!"))
                }
                // Calculate score for low shot
                score += Utils.calculateScore(
                    result.pose[LANDMARKS.RIGHT_WRIST.id].y.toDouble(),
                    result.pose[LANDMARKS.RIGHT_HIP.id].y.toDouble()
                )
            }
        }

        duration = actionCount

        // Normalize scores
        if (actionCount > 0) score /= actionCount

        return score
    }

    public override fun getActionRecommendations(): MutableList<Recommendation> {
        return recommendations
    }

    public override fun getActionDuration(): Int {
        return duration
    }
}