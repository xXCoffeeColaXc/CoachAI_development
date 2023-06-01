package hu.bme.aut.android.coachai.actions

import android.util.Log
import hu.bme.aut.android.coachai.data.InferenceResult
import hu.bme.aut.android.coachai.data.LANDMARKS
import hu.bme.aut.android.coachai.data.Recommendation
import hu.bme.aut.android.coachai.utils.Utils

class RelAction(inferenceResult: MutableList<InferenceResult>) :  Action(inferenceResult){


    public override fun analyzeAction() : Double{

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

                val rightElbowAngle3D = Utils.calculateAngle3D(
                    result.pose[LANDMARKS.RIGHT_WRIST.id],
                    result.pose[LANDMARKS.RIGHT_ELBOW.id],
                    result.pose[LANDMARKS.RIGHT_SHOULDER.id]
                )

                Log.d("ACTION/REL", "rightElbowAngle: $rightElbowAngle")
                Log.d("ACTION/REL", "rightElbowAngle3D: $rightElbowAngle3D")

                // Racquet head is down
                if (result.boundingBox.width() != 0.0f && result.boundingBox.width() > result.boundingBox.height()) {
                    Log.d("ACTION/REL/RECOMMEND", "Emeld fel az ütő fejét!")
                    recommendations.add(Recommendation(actionCount, "Emeld fel az ütő fejét!"))
                }
                // Racquet head is up
                else {
                    score += 100
                }

                // Right wrist higher than hip
                if (Utils.calculateHigherLandmark(result.pose[LANDMARKS.RIGHT_WRIST.id], result.pose[LANDMARKS.RIGHT_HIP.id]).name != LANDMARKS.RIGHT_WRIST.name) {
                    Log.d("ACTION/REL/RECOMMEND", "Vezesd ki az ütőt magasabbra!")
                    recommendations.add(Recommendation(actionCount, "Vezesd ki az ütőt magasabbra!"))
                }
                // Calculate score for wrist altitude
                score += Utils.calculateScore(
                    result.pose[LANDMARKS.RIGHT_WRIST.id].y.toDouble(),
                    result.pose[LANDMARKS.RIGHT_SHOULDER.id].y.toDouble(),
                )

                // Upper body should rotate, left shoulder is closer to us than the right shoulder
                if (Utils.calculateDeeperLandmark(result.pose[LANDMARKS.RIGHT_SHOULDER.id], result.pose[LANDMARKS.LEFT_SHOULDER.id]).name != LANDMARKS.RIGHT_SHOULDER.name) {
                    Log.d("ACTION/REL/RECOMMEND", "Fordulj ki testtel jobban!")
                    recommendations.add(Recommendation(actionCount, "Fordulj ki testtel jobban!"))
                }
                else {
                    score += 100
                }
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