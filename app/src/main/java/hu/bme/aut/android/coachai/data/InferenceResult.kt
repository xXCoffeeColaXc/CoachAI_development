package hu.bme.aut.android.coachai.data

import android.graphics.RectF

data class InferenceResult(
    val boundingBox: RectF,
    val pose: ArrayList<ScaledLandmark>,
    val label: String
)