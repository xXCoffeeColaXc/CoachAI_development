package hu.bme.aut.android.coachai.actions

import android.util.Log
import hu.bme.aut.android.coachai.data.InferenceResult
import hu.bme.aut.android.coachai.data.LANDMARKS
import hu.bme.aut.android.coachai.data.Recommendation
import hu.bme.aut.android.coachai.utils.Utils

class PreAction(inferenceResults: MutableList<InferenceResult>) : Action(inferenceResults) {

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

                val rightElbowAngle3D = Utils.calculateAngle(
                    result.pose[LANDMARKS.RIGHT_WRIST.id],
                    result.pose[LANDMARKS.RIGHT_ELBOW.id],
                    result.pose[LANDMARKS.RIGHT_SHOULDER.id]
                )


                Log.d("ACTION/PRE", "rightElbowAngle: $rightElbowAngle")
                Log.d("ACTION/PRE", "rightElbowAngle3D: $rightElbowAngle3D")

                // Racquet head is down
                if (result.boundingBox.width() != 0.0f && result.boundingBox.width() > result.boundingBox.height()) {
                    Log.d("ACTION/PRE/RECOMMEND", "Emeld fel az ütő fejét!")
                    recommendations.add(Recommendation(actionCount, "Emeld fel az ütő fejét!"))
                }
                // Racquet head is up
                else {
                    score += 100
                }

                // Right elbow must be bent
                if (rightElbowAngle3D > 140) {
                    Log.d("ACTION/PRE/RECOMMEND", "Hajlítsd be a könyököd!")
                    recommendations.add(Recommendation(actionCount, "Hajlítsd be a könyököd!"))
                }

                // Calculate score for elbow angle
                score += Utils.calculateScore(rightElbowAngle3D, 110.0)


                // TODO("Le kell tesztelni, azt hogy alulrol kezdi az utest")

                Log.d("ACTION/PRE/X", "rs.x: ${result.pose[LANDMARKS.RIGHT_SHOULDER.id].x}")
                Log.d("ACTION/PRE/X", "rw.x: ${result.pose[LANDMARKS.RIGHT_WRIST.id].x}")
                // Shoulder and wrist should be aligned
                if (!Utils.isAligned(result.pose[LANDMARKS.RIGHT_SHOULDER.id], result.pose[LANDMARKS.RIGHT_WRIST.id], 20.0)) {
                    Log.d("ACTION/PRE/RECOMMEND", "Emeld fel a karod váll magasságba!")
                    recommendations.add(Recommendation(actionCount, "Emeld fel a karod váll magasságba!"))
                }

                // Calculate score for wrist altitude
                score += Utils.calculateScore(
                    result.pose[LANDMARKS.RIGHT_SHOULDER.id].y.toDouble(),
                    result.pose[LANDMARKS.RIGHT_WRIST.id].y.toDouble()
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