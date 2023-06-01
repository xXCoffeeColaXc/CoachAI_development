package hu.bme.aut.android.coachai.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.camera.core.*
import hu.bme.aut.android.coachai.R
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectionResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import hu.bme.aut.android.coachai.data.AnalysisData
import hu.bme.aut.android.coachai.databinding.FragmentCameraBinding
import hu.bme.aut.android.coachai.detectors.Classifier
import hu.bme.aut.android.coachai.detectors.ObjectDetectorHelper
import hu.bme.aut.android.coachai.detectors.PoseLandmarkerHelper
import hu.bme.aut.android.coachai.logic.AnalyzerLogic
import hu.bme.aut.android.coachai.logic.InferenceLogic
import hu.bme.aut.android.coachai.views.MainViewModel
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max

class CameraFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener, ObjectDetectorHelper.DetectorListener, Classifier.ClassifierListener,
    AnalyzerLogic.AnalyzedDataListener {

    companion object {
        private const val TAG_POSE = "Pose Landmarker"
        private const val TAG_DET = "ObjectDetection"
        private const val MAX_BUFFER_SIZE = 200
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var classifier: Classifier
    private lateinit var logic: InferenceLogic
    private lateinit var analyzer: AnalyzerLogic

    private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    //private lateinit var videoCapture: VideoCapture
    //private var cameraFacing = CameraSelector.LENS_FACING_BACK

    private var poseInferenceTime: Long = 0
    private var detInferenceTime: Long = 0
    private var classInferenceTime: Long = 0
    private var hitCounter: Int = 0

    private var lastObjectDetectionResult: ObjectDetectionResult? = null

    /** Blocking ML operations are performed using this executor */
    private lateinit var poseEstimationExecutor: ExecutorService
    private lateinit var objectDetectionExecutor: ExecutorService

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }

        // Start the app again when users come back
        // to the foreground.
        poseEstimationExecutor.execute {
            if (poseLandmarkerHelper.isClose()) {
                poseLandmarkerHelper.setupPoseLandmarker()
            }
        }

        objectDetectionExecutor.execute{
            if (objectDetectorHelper.isClosed()) {
                objectDetectorHelper.setupObjectDetector()
            }
        }

        if (classifier.isClosed()) classifier.setupClassifier()

    }

    override fun onPause() {
        super.onPause()
        if (this::poseLandmarkerHelper.isInitialized) {
            viewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
            viewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
            viewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
            viewModel.setDelegate(poseLandmarkerHelper.currentDelegate)

            // Close the PoseLandmarkerHelper and release resources
            poseEstimationExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
        }


        // save ObjectDetector settings
        if (this::objectDetectorHelper.isInitialized) {
            viewModel.setDelegate(objectDetectorHelper.currentDelegate)
            viewModel.setThreshold(objectDetectorHelper.threshold)
            viewModel.setMaxResults(objectDetectorHelper.maxResults)
            // Close the object detector and release resources
            objectDetectionExecutor.execute { objectDetectorHelper.clearObjectDetector() }
        }

        //TODO("Save setting like detected swings, hit counter etc..")
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our backgrounds executor and gru model
        poseEstimationExecutor.shutdown()
        poseEstimationExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )

        objectDetectionExecutor.shutdown()
        objectDetectionExecutor.awaitTermination(
            Long.MAX_VALUE,
            TimeUnit.NANOSECONDS
        )

        classifier.clearClassifier()
        logic.removeObserver(analyzer)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        poseEstimationExecutor = Executors.newSingleThreadExecutor()
        objectDetectionExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        // Create Analyzer Logic
        analyzer = AnalyzerLogic(this@CameraFragment)

        // Create Inference Logic
        logic = InferenceLogic().apply {
            addObserver(analyzer)
        }

        // Create the PoseLandmarkerHelper that will handle the inference
        poseEstimationExecutor.execute {
            poseLandmarkerHelper =
                PoseLandmarkerHelper(
                    context = requireContext(),
                    runningMode = RunningMode.LIVE_STREAM,
                    minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                    minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                    minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                    currentDelegate = viewModel.currentDelegate,
                    poseLandmarkerHelperListener = this
                )
        }

        // Create the ObjectDetectionHelper that will handle the inference
        objectDetectionExecutor.execute {
            objectDetectorHelper =
                ObjectDetectorHelper(
                    context = requireContext(),
                    threshold = viewModel.currentThreshold,
                    currentDelegate = viewModel.currentDelegate,
                    maxResults = viewModel.currentMaxResults,
                    objectDetectorListener = this,
                    runningMode = RunningMode.LIVE_STREAM
                )
        }

        // Create Classifier
        classifier = Classifier(
            context = requireContext(),
            classifierListener = this,
            inferenceResultListener = logic
        )

        // Attach listeners to UI control widgets
        initBottomSheetControls()
    }

    private fun initBottomSheetControls() {
        // init bottom sheet height
        val bottomSheet = fragmentCameraBinding.bottomSheetLayout.bottomSheetLayout
        val displayMetrics = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)

        val screenHeight = displayMetrics.heightPixels

        // Set bottom sheet height to half of screen height
        val layoutParams = bottomSheet.layoutParams
        layoutParams.height = screenHeight / 2
        bottomSheet.layoutParams = layoutParams

        // init bottom sheet settings
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPoseDetectionConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPoseTrackingConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPosePresenceConfidence
            )

        fragmentCameraBinding.bottomSheetLayout.thresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentThreshold
            )

        // When clicked, lower pose detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
            if (poseLandmarkerHelper.minPoseDetectionConfidence >= 0.2) {
                poseLandmarkerHelper.minPoseDetectionConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise pose detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
            if (poseLandmarkerHelper.minPoseDetectionConfidence <= 0.8) {
                poseLandmarkerHelper.minPoseDetectionConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, lower pose tracking score threshold floor
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
            if (poseLandmarkerHelper.minPoseTrackingConfidence >= 0.2) {
                poseLandmarkerHelper.minPoseTrackingConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise pose tracking score threshold floor
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
            if (poseLandmarkerHelper.minPoseTrackingConfidence <= 0.8) {
                poseLandmarkerHelper.minPoseTrackingConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, lower pose presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
            if (poseLandmarkerHelper.minPosePresenceConfidence >= 0.2) {
                poseLandmarkerHelper.minPosePresenceConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise pose presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
            if (poseLandmarkerHelper.minPosePresenceConfidence <= 0.8) {
                poseLandmarkerHelper.minPosePresenceConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, lower detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (objectDetectorHelper.threshold >= 0.1) {
                objectDetectorHelper.threshold -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (objectDetectorHelper.threshold <= 0.8) {
                objectDetectorHelper.threshold += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, change the underlying hardware used for inference.
        // Current options are CPU and GPU
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate, false
        )
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long
                ) {
                    try {
                        poseLandmarkerHelper.currentDelegate = p2
                        objectDetectorHelper.currentDelegate = p2
                        updateControlsUi()
                    } catch(e: UninitializedPropertyAccessException) {
                        Log.e(TAG_POSE, "PoseLandmarkerHelper has not been initialized yet.")
                    }
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }


    }

    // Update the values displayed in the bottom sheet.
    // Reset Poselandmarker and ObjectDetector helper.
    private fun updateControlsUi() {
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                poseLandmarkerHelper.minPoseDetectionConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                poseLandmarkerHelper.minPoseTrackingConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                poseLandmarkerHelper.minPosePresenceConfidence
            )

        fragmentCameraBinding.bottomSheetLayout.thresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                objectDetectorHelper.threshold)

        // Needs to be cleared instead of reinitialized because the GPU
        // delegate needs to be initialized on the thread using it when applicable
        poseEstimationExecutor.execute {
            poseLandmarkerHelper.clearPoseLandmarker()
            poseLandmarkerHelper.setupPoseLandmarker()
        }

        objectDetectionExecutor.execute {
            objectDetectorHelper.clearObjectDetector()
            objectDetectorHelper.setupObjectDetector()
        }

        fragmentCameraBinding.overlay
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis for Pose Detection
        val poseImageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(poseEstimationExecutor) { image ->
                    //collectFrame(image) // Could be another executor for this task
                    detectPose(image)
                }
            }

        // ImageAnalysis for Object Detection
        val objectImageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(objectDetectionExecutor) { image ->
                    detectObject(image)
                }
            }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, poseImageAnalyzer, objectImageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG_POSE, "Use case binding failed", exc)
        }
    }

    override fun onAnalysisComplete(data: AnalysisData) {
        viewModel.setAnalyzedData(data)

        if (analyzer.actionRecommendationMessage.isNotEmpty()) {
            fragmentCameraBinding.bottomSheetLayout.preRecommendationVal.text = analyzer.actionRecommendationMessage[0]
            fragmentCameraBinding.bottomSheetLayout.hitRecommendationVal.text = analyzer.actionRecommendationMessage[1]
            fragmentCameraBinding.bottomSheetLayout.relRecommendationVal.text = analyzer.actionRecommendationMessage[2]
        }
    }

    private fun detectPose(imageProxy: ImageProxy) {
        poseLandmarkerHelper.detectLiveStream(
            imageProxy = imageProxy
        )
    }

    private fun detectObject(imageProxy: ImageProxy) {
        objectDetectorHelper.detectLiveStream(
            imageProxy = imageProxy
        )
    }

    private fun classifyAction(poseResult: PoseLandmarkerResult, objectDetectionResult: ObjectDetectionResult,
                               inputImageHeight: Int, inputImageWidth: Int
    ) {
        classifier.classify(poseResult, objectDetectionResult, inputImageHeight, inputImageWidth)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    private fun updateInferenceTime() {
        val totalInferenceTime = max(poseInferenceTime, detInferenceTime) + classInferenceTime

        Log.d("INF_TIME", "pose: $poseInferenceTime")
        Log.d("INF_TIME", "det: $detInferenceTime")
        Log.d("INF_TIME", "class: $classInferenceTime")

        fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
            String.format("%d ms", totalInferenceTime)
    }

    // Update UI after pose have been detected. Extracts original
    // image height/width to scale and place the landmarks properly through
    // OverlayView
    override fun onResultPose(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                poseInferenceTime = resultBundle.inferenceTime
                updateInferenceTime()

                // Pass necessary information to OverlayView for drawing on the canvas
                fragmentCameraBinding.overlay.setResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth
                )

                // Classify the action using the most recent pose and object detection results
                lastObjectDetectionResult?.let {
                    classifyAction(resultBundle.results.first(), it, resultBundle.inputImageHeight, resultBundle.inputImageWidth)
                }

                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    override fun onResultsDet(resultBundle: ObjectDetectorHelper.ResultBundle) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                detInferenceTime = resultBundle.inferenceTime
                updateInferenceTime()

                // Pass necessary information to OverlayView for drawing on the canvas
                fragmentCameraBinding.overlay.setResultsDet(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth
                )

                // Store the result for future use
                lastObjectDetectionResult = resultBundle.results.first()

                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    override fun onResultsClass(resultBundle: Classifier.ResultBundle) {
        // handle result here

        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                classInferenceTime = resultBundle.inferenceTime
                updateInferenceTime()

                fragmentCameraBinding.bottomSheetLayout.actionVal.text = resultBundle.result

                if (logic.isActionChanged(resultBundle.result) && resultBundle.result == "hit") hitCounter += 1
                fragmentCameraBinding.bottomSheetLayout.hitCounterVal.text = hitCounter.toString()

                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }

        Log.d("CLASSIFICATION_RESULT", "Predicted action: ${resultBundle.result}, Inference time: ${resultBundle.inferenceTime}")
    }

    // TODO("Merge these somehow ?")
    override fun onErrorPose(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == PoseLandmarkerHelper.GPU_ERROR) {
                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    PoseLandmarkerHelper.DELEGATE_CPU, false
                )
            }
        }
    }

    override fun onErrorDet(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == PoseLandmarkerHelper.GPU_ERROR) {
                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    PoseLandmarkerHelper.DELEGATE_CPU, false
                )
            }
        }
    }

    override fun onErrorClass(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == PoseLandmarkerHelper.GPU_ERROR) {
                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    PoseLandmarkerHelper.DELEGATE_CPU, false
                )
            }
        }
    }

    /*
    // TODO("Maybe in the future, collect frames and save every detected swing into video")
    @SuppressLint("UnsafeOptInUsageError")
    private fun collectFrame(image: ImageProxy) {
        /*
        // Add frame to buffer if its a potential swing
        if (logic.isSwingStarted()) {
            image.image?.let { frameBuffer.add(it) }
        }

        // frame shape = 480x640

        // If the buffer is too big and the condition has not been met, clear the buffer
        else if (frameBuffer.size > MAX_BUFFER_SIZE) {
            frameBuffer.clear()
        }
         */
    }

    private fun saveFrames(frames: List<Image>) {
        // Iterate over the frames and save them
        for (frame in frames) {
            // Save frame...
        }
    }

     */
}
