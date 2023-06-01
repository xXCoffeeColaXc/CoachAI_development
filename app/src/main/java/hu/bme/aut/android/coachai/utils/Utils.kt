package hu.bme.aut.android.coachai.utils

import android.util.Log
import hu.bme.aut.android.coachai.data.ScaledLandmark
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.acos

object Utils {

    // calculates a score between 0 and 100 based on how close the inputValue is to the targetValue,
    // with higher scores for closer values.
    fun calculateScore(inputValue: Double, targetValue: Double) : Double {
        val maxScore = 100.0 // The maximum score that can be achieved
        var score = 0.0

        // Calculate the distance from the target
        val distanceFromTarget = abs(inputValue - targetValue)

        // Calculate the score
        if (targetValue > inputValue) {
            score = maxScore - (distanceFromTarget * (maxScore / targetValue))
        }
        else {
            score = maxScore - (distanceFromTarget * (targetValue / maxScore))
        }
        return max(score, 0.0) // Return the score, ensuring that it's not negative
    }

    // calculates the angle (in degrees) between vectors ba and bc using the dot product and cosine law,
    // returns the smaller angle between ba and bc
    fun calculateAngle(a: ScaledLandmark, b: ScaledLandmark, c: ScaledLandmark): Double {
        val ba = floatArrayOf(a.x - b.x, a.y - b.y)
        val bc = floatArrayOf(c.x - b.x, c.y - b.y)

        val baLength = sqrt(ba[0].pow(2) + ba[1].pow(2))
        val bcLength = sqrt(bc[0].pow(2) + bc[1].pow(2))

        val dotProduct = ba[0] * bc[0] + ba[1] * bc[1]

        val cosineAngle = dotProduct / (baLength * bcLength)
        val angle = acos(cosineAngle)

        return Math.toDegrees(angle.toDouble())
    }

    fun calculateAngle3D(a: ScaledLandmark, b: ScaledLandmark, c: ScaledLandmark): Double {
        val ab = floatArrayOf(a.x - b.x, a.y - b.y, a.z - b.z)
        val bc = floatArrayOf(c.x - b.x, c.y - b.y, c.z - b.z)

        val baLength = sqrt(ab[0].pow(2) + ab[1].pow(2) + ab[2].pow(2))
        val bcLength = sqrt(bc[0].pow(2) + bc[1].pow(2) + bc[2].pow(2))

        val dotProduct = ab[0] * bc[0] + ab[1] * bc[1] + ab[2] * bc[2]

        val cosineAngle = dotProduct / (baLength * bcLength)
        val angle = acos(cosineAngle)

        return Math.toDegrees(angle.toDouble())
    }

    fun calculateDeeperLandmark(a: ScaledLandmark, b: ScaledLandmark) : ScaledLandmark {
        Log.d("ACTION/REL/DEEPER", "a.z: ${a.z}")
        Log.d("ACTION/REL/DEEPER", "b.z: ${b.z}")

        return if (a.z > b.z) a
        else b
    }

    fun calculateHigherLandmark(a: ScaledLandmark, b: ScaledLandmark) : ScaledLandmark {
        return if (a.y < b.y) a
        else b
    }

    fun isAligned(a: ScaledLandmark, b: ScaledLandmark, tolerance: Double) : Boolean {
        val yMin = max(a.y - tolerance, 0.0) // handle negative value
        val yMax = min(a.y + 3*tolerance, 480.0) // handle image size
        Log.d("ACTION/PRE/ALIGNED", "yMin: $yMin")
        Log.d("ACTION/PRE/ALIGNED", "yMax: $yMax")
        Log.d("ACTION/PRE/ALIGNED", "a.y: ${a.y}")
        Log.d("ACTION/PRE/ALIGNED", "b.y: ${b.y}")
        return (b.y > yMin && b.y < yMax)
    }


}