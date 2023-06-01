package hu.bme.aut.android.coachai.logic

import android.util.Log
import hu.bme.aut.android.coachai.actions.Action
import hu.bme.aut.android.coachai.actions.HitAction
import hu.bme.aut.android.coachai.actions.PreAction
import hu.bme.aut.android.coachai.actions.RelAction
import hu.bme.aut.android.coachai.data.InferenceResult
import hu.bme.aut.android.coachai.detectors.Classifier
import hu.bme.aut.android.coachai.listener.SwingObserver
import hu.bme.aut.android.coachai.swing.Swing

class InferenceLogic : Classifier.InferenceResultListener{

    private val actionHistory: MutableList<Action> = mutableListOf()

    private var currentLabel: String? = null
    private var storedResults = mutableListOf<InferenceResult>()

    private var detectedSwings = mutableListOf<Swing>()

    private val observers = mutableListOf<SwingObserver>()


    override fun onInferenceResult(resultBundle: InferenceResult) {
        // Process inference result, by creating actions and swings
        processResult(resultBundle)

        // Debug
        //Log.d("LOGIC/LABEL", resultBundle.label)
        //Log.d("LOGIC/BBOX", "bbox: ${resultBundle.boundingBox}")
        //Log.d("LOGIC/POSE", "pose: ${resultBundle.pose}")
        Log.d("LOGIC/POSE", "pose: ${resultBundle.pose.size}")
    }

    fun isConditionMet() : Boolean {
        return (actionHistory.size == 3 &&
                actionHistory[0] is PreAction &&
                actionHistory[1] is HitAction &&
                actionHistory[2] is RelAction
                )
    }

    fun isSwingStarted(): Boolean {
        return actionHistory.isNotEmpty() && actionHistory[0] is PreAction
    }

    fun isActionChanged(label: String): Boolean {
        return label != currentLabel
    }


    private fun processResult(result: InferenceResult) {
        if (isActionChanged(result.label)) {
            // Label changed, create an Action from the stored results
            Log.d("LOGIC", "LABELS CHANGED")
            val newAction = createAction()

            // If we created an action, add it to our list
            newAction?.let {
                actionHistory.add(it)
                // If we have more than 3 actions, remove the first one
                if (actionHistory.size > 3) {
                    actionHistory.removeAt(0)
                }

                // Check if we have the correct sequence of actions
                if (isConditionMet()) {
                    Log.d("LOGIC", "SWING CREATED")

                    // Create a Swing object
                    createSwing()

                    // Reset the action list
                    actionHistory.clear()
                }
            }

            // Action has been created, clear the stored results
            storedResults = mutableListOf()
        }

        // Update the current label and add the result to the stored results
        currentLabel = result.label
        storedResults.add(result)
    }

    private fun createAction(): Action? {
        val newAction = when (currentLabel) {
            "pre" -> PreAction(storedResults)
            "hit" -> HitAction(storedResults)
            "rel" -> RelAction(storedResults)
            else -> null
        }
        return newAction
    }

    private fun createSwing() {
        val swing = Swing(
            actionHistory[0] as PreAction,
            actionHistory[1] as HitAction,
            actionHistory[2] as RelAction
        )

        notifySwingDetected(swing)

        Log.d("LOGIC", swing.toString())

        detectedSwings.add(swing)
    }

    fun listSwings() {
        Log.d("LOGIC", detectedSwings.forEach { swing -> swing.toString() }.toString())
    }

    fun addObserver(observer: SwingObserver) {
        observers.add(observer)
    }

    fun removeObserver(observer: SwingObserver) {
        observers.remove(observer)
    }

    private fun notifySwingDetected(swing: Swing) {
        observers.forEach { it.onSwingDetected(swing) }
    }


}