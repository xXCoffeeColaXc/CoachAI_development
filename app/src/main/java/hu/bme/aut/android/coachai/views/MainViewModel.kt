package hu.bme.aut.android.coachai.views

import androidx.lifecycle.ViewModel
import hu.bme.aut.android.coachai.data.AnalysisData
import hu.bme.aut.android.coachai.detectors.ObjectDetectorHelper
import hu.bme.aut.android.coachai.detectors.PoseLandmarkerHelper

class MainViewModel : ViewModel() {
    // Pose
    private var _modelPose = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_LITE
    private var _minPoseDetectionConfidence: Float = PoseLandmarkerHelper.DEFAULT_POSE_DETECTION_CONFIDENCE
    private var _minPoseTrackingConfidence: Float = PoseLandmarkerHelper.DEFAULT_POSE_TRACKING_CONFIDENCE
    private var _minPosePresenceConfidence: Float = PoseLandmarkerHelper.DEFAULT_POSE_PRESENCE_CONFIDENCE

    val currentMinPoseDetectionConfidence: Float get() = _minPoseDetectionConfidence
    val currentMinPoseTrackingConfidence: Float get() = _minPoseTrackingConfidence
    val currentMinPosePresenceConfidence: Float get() = _minPosePresenceConfidence

    // Object Detection
    private var _threshold: Float = ObjectDetectorHelper.THRESHOLD_DEFAULT
    private var _maxResults: Int = ObjectDetectorHelper.MAX_RESULTS_DEFAULT

    val currentThreshold: Float get() = _threshold
    val currentMaxResults: Int get() = _maxResults

    // Analyzed Data
    private var _analysisData: AnalysisData? = null
    val analyzedData: AnalysisData? get() = _analysisData

    // Other
    private var _delegate: Int = PoseLandmarkerHelper.DELEGATE_GPU
    val currentDelegate: Int get() = _delegate


    fun setAnalyzedData(analysisData: AnalysisData) {
        _analysisData = analysisData
    }

    fun setDelegate(delegate: Int) {
        _delegate = delegate
    }

    fun setMinPoseDetectionConfidence(confidence: Float) {
        _minPoseDetectionConfidence = confidence
    }

    fun setMinPoseTrackingConfidence(confidence: Float) {
        _minPoseTrackingConfidence = confidence
    }

    fun setMinPosePresenceConfidence(confidence: Float) {
        _minPosePresenceConfidence = confidence
    }

    fun setThreshold(threshold: Float) {
        _threshold = threshold
    }

    fun setMaxResults(maxResults: Int) {
        _maxResults = maxResults
    }

    fun setModel(model: Int) {
        _modelPose = model
    }
}