package hu.bme.aut.android.coachai.detectors

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectionResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import hu.bme.aut.android.coachai.data.InferenceResult
import hu.bme.aut.android.coachai.data.LANDMARKS
import hu.bme.aut.android.coachai.data.ScaledLandmark
import hu.bme.aut.android.coachai.ml.GruClassifierMetadata
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.collections.ArrayList

class Classifier (
    val context: Context,
    var classifierListener: ClassifierListener? = null,
    var inferenceResultListener: InferenceResultListener? = null
) {

    // Use this if the model located in assets dir
    //private var interpreter: Interpreter? = null
    //private val probabilityProcessor: TensorProcessor = TensorProcessor.Builder().add(NormalizeOp(0f, 1f)).build()

    private var model: GruClassifierMetadata? = null
    private val labels = arrayOf("pre", "hit", "rel")
    private val ignoreLandmarks = arrayListOf(0,1,2,3,4,5,6,7,8,9,10,13,15,17,19,21)
    private var inputDataSequence: ArrayList<ArrayList<Float>> = ArrayList()

    init {
        setupClassifier()
    }

    fun setupClassifier() {
        try {
            // Load the model
            model = GruClassifierMetadata.newInstance(context)

            // If model in assets
            //val model = loadModelFile()
            //interpreter = Interpreter(model)
        } catch (e: Exception) {
            classifierListener?.onErrorClass(e.message ?: "Error loading model", 1)
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd("gru_classifier.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun classify(poseResult: PoseLandmarkerResult, objectDetectionResult: ObjectDetectionResult,
                 inputImageHeight: Int, inputImageWidth: Int) {

        Log.d("CLASSIFIER", "--------------------")

        // Add one frame's predictions to sequence
        val inputData = extractDataFromDetectors(poseResult, objectDetectionResult, inputImageHeight, inputImageWidth)
        inputData?.let { inputDataSequence.add(it) }

        if (inputDataSequence.size > TIMESTEP) // predict on the last 16 frame
        inputDataSequence = inputDataSequence.takeLast(TIMESTEP) as ArrayList<ArrayList<Float>>

        // Predict on one sequence
        if (inputDataSequence.size == TIMESTEP) {
            Log.d("CLASSIFIER/SEQUENCE", "PREDICTION STARTED")

            val startTime = System.currentTimeMillis()

            // Prediction
            val predictedAction = predict(inputDataSequence)

            // Calculate inference time
            val endTime = System.currentTimeMillis()
            val inferenceTime = endTime - startTime

            // Create a result bundle and pass it to the listener
            val resultBundle = ResultBundle(predictedAction, inferenceTime)
            classifierListener?.onResultsClass(resultBundle)

            // Create InferenceResult and send it to a receiver(InferenceLogic)
            sendInferenceResult(poseResult, objectDetectionResult, inputImageHeight, inputImageWidth, predictedAction)
        }
    }

    private fun sendInferenceResult(poseResult: PoseLandmarkerResult, objectDetectionResult: ObjectDetectionResult,
                                    inputImageHeight: Int, inputImageWidth: Int, label: String) {

        var boundingBox = RectF(0.0f, 0.0f, 0.0f, 0.0f)
        val poseLandmarks: ArrayList<ScaledLandmark> = ArrayList()

        // ScaleFactor do not needed, because we don't care about relative position, just ratio



        poseResult.let { poseLandmarkerResult ->
            for (landmark in poseLandmarkerResult.landmarks()) {
                for ((index, normalizedLandmark) in landmark.withIndex()) {
                    //if (!ignoreLandmarks.contains(index)) {
                        val specificLandmark = LANDMARKS.values().find { it.id == index }

                        val scaledLandmark = ScaledLandmark(
                            specificLandmark!!.name,
                            normalizedLandmark.x() * inputImageWidth,
                            normalizedLandmark.y() * inputImageHeight,
                            normalizedLandmark.z() * inputImageWidth
                        )
                        poseLandmarks.add(scaledLandmark)
                    //}
                }
            }
        }

        objectDetectionResult.let {
            for (detection in it.detections()) {
                boundingBox = detection.boundingBox()
            }
        }

        // Create InferenceResult
        val resultBundle = InferenceResult(
            pose = poseLandmarks,
            boundingBox = boundingBox,
            label = label
        )

        // Send
        inferenceResultListener?.onInferenceResult(resultBundle)

    }

    private fun extractDataFromDetectors(poseResult: PoseLandmarkerResult, objectDetectionResult: ObjectDetectionResult, inputImageHeight: Int, inputImageWidth: Int): ArrayList<Float>? {
        // Create Input Data
        val inputData: ArrayList<Float> = ArrayList()

        poseResult.let { poseLandmarkerResult ->
            for(landmark in poseLandmarkerResult.landmarks()) {
                for((index, normalizedLandmark) in landmark.withIndex()) {
                    if (!ignoreLandmarks.contains(index)){
                        //Log.d("CLASSIFIER/POSE", normalizedLandmark.toString())
                        inputData.add(normalizedLandmark.x())
                        inputData.add(normalizedLandmark.y())
                        inputData.add(normalizedLandmark.z())
                    }
                }
            }
        }

        objectDetectionResult.let {
            for (detection in it.detections()) { // one iteration
                val boundingBox = detection.boundingBox()

                val normalizedBox = normalizeBoundingBox(boundingBox, inputImageHeight, inputImageWidth)

                val top = normalizedBox.top
                val bottom = normalizedBox.bottom
                val left = normalizedBox.left
                val right = normalizedBox.right

                Log.d("CLASSIFIER/BBOX", "bbox: ${boundingBox.toShortString()}")
                Log.d("CLASSIFIER/BBOX", "top: $top")
                Log.d("CLASSIFIER/BBOX", "bottom: $bottom")
                Log.d("CLASSIFIER/BBOX", "left: $left")
                Log.d("CLASSIFIER/BBOX", "right: $right")

                inputData.add(left)
                inputData.add(top)
                inputData.add(right)
                inputData.add(bottom)

            }
        }

        // If there was no Object Detection add 0 values
        if (inputData.size == 51) for (i in 0 until 4) inputData.add(0.0f)

        Log.d("CLASSIFIER/INPUT", inputData.toString() + " size: " + inputData.size)
        return if (inputData.size == 55) inputData // It would crash if there is no pose detection
        else null
    }

    private fun normalizeBoundingBox(boundingBox: RectF, imageHeight: Int, imageWidth: Int): RectF {
        val left = boundingBox.left / imageWidth
        val top = boundingBox.top / imageHeight
        val right = boundingBox.right / imageWidth
        val bottom = boundingBox.bottom / imageHeight

        return RectF(left, top, right, bottom)
    }

    private fun predict(input: ArrayList<ArrayList<Float>>) : String {
        // Creates inputs for reference.
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(BATCH_SIZE, TIMESTEP, FEATURES), DataType.FLOAT32)
        val floatArray = input.flatten().toFloatArray()
        inputFeature0.loadArray(floatArray)

        // Inference
        val outputs = model!!.process(inputFeature0)

        // Get the float array of probabilities
        val probability = outputs.probabilityAsTensorBuffer
        val probs = probability.floatArray

        // Get the index of the highest probability
        val maxProbIndex = probs.indices.maxByOrNull { probs[it] } ?: -1
        val predictedAction = shiftLabel(labels[maxProbIndex])

        return predictedAction
    }

    private fun shiftLabel(label: String) : String {
        return when(label) {
            "pre" -> "hit"
            "hit" -> "rel"
            "rel" -> "pre"
            else -> label
        }
    }

    fun clearClassifier() {
        // Releases model resources if no longer used.
        model!!.close()
        model = null
    }

    fun isClosed(): Boolean {
        return model == null
    }


    /*
    // ------ TEST ------
    fun classifyTest(inputBatch: List<List<Float>>) {

        // Creates inputs for reference.
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 16, 55), DataType.FLOAT32)
        val floatArray = inputBatch.flatten().toFloatArray()
        inputFeature0.loadArray(floatArray)

        // Runs model inference and gets result.
        val startTime = System.currentTimeMillis()

        val outputs = model!!.process(inputFeature0)

        // Calculate inference time
        val endTime = System.currentTimeMillis()
        val inferenceTime = endTime - startTime

        val probability = outputs.probabilityAsTensorBuffer

        // Get the float array of probabilities
        val probs = probability.floatArray

        // Log the probabilities
        Log.d("CLASSIFICATION_RESULT", "Output probabilities: ${probs.joinToString(", ")}")

        // Get the index of the highest probability
        val maxProbIndex = probs.indices.maxByOrNull { probs[it] } ?: -1

        /*
         // If model in assets
        // Prepare input tensor
        val inputTensor = TensorBuffer.createFixedSize(intArrayOf(1, 16, 55), DataType.FLOAT32)
        val floatArray = inputBatch.flatten().toFloatArray()
        inputTensor.loadArray(floatArray)

        // Run inference
        val outputTensor = TensorBuffer.createFixedSize(intArrayOf(1, 3), DataType.FLOAT32)
        interpreter?.run(inputTensor.buffer, outputTensor.buffer)

        // Process and get the result
        val outputs = probabilityProcessor.process(outputTensor)

        val maxProbIndex = outputs.floatArray.indices.maxByOrNull { outputs.floatArray[it] } ?: -1


        Log.d("CLASSIFICATION_RESULT", "Probabilities: ${outputs.floatArray.joinToString()}")
        */

        val predictedAction = labels[maxProbIndex]

        // Create a result bundle and pass it to the listener
        //val resultBundle = ResultBundle(predictedAction, inferenceTime)
        //classifierListener?.onResultsClass(resultBundle)



        clearClassifier()
    }

    fun createTestData(): List<List<Float>> {
      val testData = mutableListOf<List<Float>>()
      val random = Random()
      for (i in 0 until 16) {
          val data = FloatArray(55) { random.nextFloat() }.toList()
          testData.add(data)
      }
      return testData
    }

    fun loadTestData(): List<List<Float>> {
      val testData = mutableListOf<List<Float>>()
      try {
          context.assets.open("test_data.txt").bufferedReader().useLines { lines ->
              lines.forEach { line ->
                  val lineData = line.split(",").map { it.trim().toFloat() }
                  testData.add(lineData)
              }
          }
      } catch (e: Exception) {
          // Handle exception here
      }
      return testData
    }
    // ------------------
     */

    data class ResultBundle(
        val result: String,
        val inferenceTime: Long
    )

    companion object {
        const val TIMESTEP = 16
        const val BATCH_SIZE = 1
        const val FEATURES = 55

        const val TAG = "Classifier"
    }

    interface InferenceResultListener {
        fun onInferenceResult(resultBundle: InferenceResult)
    }

    interface ClassifierListener {
        fun onResultsClass(resultBundle: ResultBundle)
        fun onErrorClass(error: String, errorCode: Int)
    }
}