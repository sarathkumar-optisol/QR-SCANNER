package com.gv.qrgscanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.app.Activity
import android.app.PendingIntent.getActivity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.impl.PreviewConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.gv.qrgscanner.Constants.QR_CALENDER
import com.gv.qrgscanner.Constants.QR_CONTACT
import com.gv.qrgscanner.Constants.QR_DL
import com.gv.qrgscanner.Constants.QR_EMAIL
import com.gv.qrgscanner.Constants.QR_GEOPOINT
import com.gv.qrgscanner.Constants.QR_MAIL
import com.gv.qrgscanner.Constants.QR_PHONE
import com.gv.qrgscanner.Constants.QR_SMS
import com.gv.qrgscanner.Constants.QR_URL
import com.gv.qrgscanner.Constants.QR_WIFI
import com.gv.qrgscanner.Constants.isLaunched
import com.gv.qrgscanner.databinding.ActivityMainBinding
import com.gv.qrgscanner.models.ContactInfo
import java.io.Serializable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class MainActivity : AppCompatActivity()  {

    private lateinit var binding : ActivityMainBinding


    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val REQUEST_CAMERA = 10
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }

    private  var  previewUseCase: Preview? =null
    private  var  cameraSelector: CameraSelector? =null
    private  var  cameraProvider: ProcessCameraProvider? = null
    private  var  analysisUseCase: ImageAnalysis? =null
    private var camera_lens = CameraSelector.LENS_FACING_BACK
    private val screenAspectRatio: Int
        get() {

            val metrics = DisplayMetrics().also { binding.previewView.display?.getRealMetrics(it) }
            return aspectRatio(metrics.widthPixels, metrics.heightPixels)
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.previewView.post { setupCamera() }
    }

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        return super.onCreateView(parent, name, context, attrs)
        setupCamera()
    }

    private  fun setupCamera(){
        cameraSelector = CameraSelector.Builder().requireLensFacing(camera_lens).build()
        ViewModelProvider(this,ViewModelProvider.AndroidViewModelFactory.getInstance(application))
            .get(CameraXViewModel::class.java)
            .processCameraProvider
            .observe(this,
                Observer { provider:ProcessCameraProvider ->  cameraProvider = provider
                    if(isCameraPermissionGranted()){
                        bindCameraUseCases()
                    }
                    else{
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA),
                            REQUEST_CAMERA)
                        }
                })
    }
    private fun bindCameraUseCases() {
        bindPreviewUseCase()
        bindAnalyseUseCase()
    }
    private  fun isCameraPermissionGranted():Boolean{
        val permission = ContextCompat.checkSelfPermission(baseContext, Manifest.permission.CAMERA)
        return permission == PackageManager.PERMISSION_GRANTED

    }
    @SuppressLint("ClickableViewAccessibility")
    private  fun  bindPreviewUseCase(){
        if (cameraProvider == null) {
            return
        }
        if (previewUseCase != null) {
            cameraProvider!!.unbind(previewUseCase)
        }
        val displayMetrics = resources.displayMetrics
        val factory = SurfaceOrientedMeteringPointFactory(
            displayMetrics.widthPixels.toFloat(),
            displayMetrics.heightPixels.toFloat()
        )
        val point = factory.createPoint(
            displayMetrics.widthPixels / 2f,
            displayMetrics.heightPixels / 2f
        )
        val action = FocusMeteringAction
            .Builder(point, FocusMeteringAction.FLAG_AF)
            .build()
        Log.d(TAG,"af "+binding.previewView?.display)
        var rotation =  binding.previewView?.display.rotation
        previewUseCase = Preview.Builder().setTargetAspectRatio(screenAspectRatio).setTargetRotation(
           rotation
        ).build()
        previewUseCase!!.setSurfaceProvider(binding.previewView!!.createSurfaceProvider())
        try {
            var camera = cameraProvider!!.bindToLifecycle(
                /* lifecycleOwner= */this,
                cameraSelector!!,
                previewUseCase
            )
            camera.cameraControl.startFocusAndMetering(action)


            binding.flash!!.setOnClickListener{
                var torchState=camera.cameraInfo.torchState
                if(torchState.value==TorchState.OFF){
                    camera.cameraControl.enableTorch(true)
                    binding.flash!!.setImageResource(R.drawable.ic_baseline_flash_off_24)
                }
                else{
                    camera.cameraControl.enableTorch(false)
                    binding.flash!!.setImageResource(R.drawable.ic_baseline_flash_on_24)
                }


            }

            /** camera switch  **/
            binding.switchCamera!!.setOnClickListener {
                if(camera_lens ==  CameraSelector.LENS_FACING_BACK) {
                    camera_lens = CameraSelector.LENS_FACING_FRONT
                }
                else{
                    camera_lens = CameraSelector.LENS_FACING_BACK
                }
                setupCamera()
            }


            /** Pinch to Zoom **/
            val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val currentZoomRatio: Float = camera.cameraInfo.zoomState.value!!.zoomRatio?: 1F
                    val delta = detector.scaleFactor
                    camera.cameraControl.setZoomRatio(currentZoomRatio * delta)
                    return true
                }

            }
            val scaleGestureDetector = ScaleGestureDetector(this, listener)
            binding.previewView!!.setOnTouchListener { _, event ->
                when(event.action){
                    MotionEvent.ACTION_DOWN -> return@setOnTouchListener true
                    MotionEvent.ACTION_MOVE -> {
                        scaleGestureDetector.onTouchEvent(event)
                        return@setOnTouchListener true}
                    MotionEvent.ACTION_UP -> {
                        val factory = binding.previewView!!.createMeteringPointFactory(cameraSelector!!)
                        val point = factory.createPoint(event.x, event.y)
                        val action = FocusMeteringAction.Builder(point).build()
                        camera.cameraControl.startFocusAndMetering(action)
                        return@setOnTouchListener true
                    }
                    else -> return@setOnTouchListener false

                }
            }


        } catch (illegalStateException: IllegalStateException) {
            Log.e(TAG, illegalStateException.message.toString())
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e(TAG, illegalArgumentException.message.toString())
        }
    }



    private fun  bindAnalyseUseCase(){
        val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build())


        if (cameraProvider == null) {
            return
        }
        if (analysisUseCase != null) {
            cameraProvider!!.unbind(analysisUseCase)
        }
        analysisUseCase = ImageAnalysis.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(binding.previewView!!.display.rotation)
            .build()
        val cameraExecutor = Executors.newSingleThreadExecutor()
        analysisUseCase?.setAnalyzer(
            cameraExecutor,
            ImageAnalysis.Analyzer { imageProxy ->
                processImageProxy(barcodeScanner, imageProxy)
            }
        )
        try {
            cameraProvider!!.bindToLifecycle(
                /* lifecycleOwner= */this,
                cameraSelector!!,
                analysisUseCase
            )
        } catch (illegalStateException: IllegalStateException) {
            Log.e(TAG, illegalStateException.message.toString())
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e(TAG, illegalArgumentException.message.toString())
        }
    }
    @SuppressLint("UnsafeExperimentalUsageError")
    private fun processImageProxy(
        barcodeScanner: BarcodeScanner,
        imageProxy: ImageProxy
    ) {


        val inputImage =
            InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)


        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                if(barcodes.size!=0){
                    if(!isLaunched){
                        var intent = Intent(this@MainActivity,DataActivity::class.java)
                        Log.d(TAG,barcodes[0].valueType.toString())
                        if(barcodes[0].calendarEvent!=null){
                            intent.putExtra("QRTYPE", QR_CALENDER)
                            Log.d(TAG,"cl  -"+barcodes[0].calendarEvent!!.toString())

                        }
                        else if(barcodes[0].email!=null){
                            intent.putExtra("QRTYPE", QR_EMAIL)

                        }
                        else if(barcodes[0].phone!=null){
                            intent.putExtra("QRTYPE", QR_PHONE)

                        } else if(barcodes[0].sms!=null){
                            intent.putExtra("QRTYPE", QR_SMS)

                        }
                        else if(barcodes[0].wifi!=null){
                            intent.putExtra("QRTYPE", QR_WIFI)

                        }else if(barcodes[0].url!=null){
                            intent.putExtra("QRTYPE", QR_URL)

                        }else if(barcodes[0].geoPoint!=null){
                            intent.putExtra("QRTYPE", QR_GEOPOINT)

                        }else if(barcodes[0].driverLicense!=null){
                            intent.putExtra("QRTYPE", QR_DL)
                            intent.putExtra("MAIL", barcodes[0].email.address)
                        }





                        else if (barcodes[0].contactInfo!=null){
                            intent.putExtra("QRTYPE", QR_CONTACT)

                            barcodes[0].displayValue
                            val bundle = ContactInfo(barcodes[0].contactInfo.addresses[0].addressLines.toString(),barcodes[0].contactInfo.emails[0].address,barcodes[0].contactInfo.name.formattedName,barcodes[0].contactInfo.organization.toString())
                                intent.also {
                                    it.putExtra("bundle",bundle)
                                    startActivity(it)
                                }

//                            Log.d(TAG,"cl  -"+barcodes[0].contactInfo!!.toString())
//                            Log.d(TAG,"cl  -"+barcodes[0].contactInfo!!.name.toString())
//                            var bundle = Bundle()
//                            var contact = ContactInfo(barcodes[0].contactInfo!!.addresses,barcodes[0].contactInfo!!.emails,barcodes[0].contactInfo!!.name,barcodes[0].contactInfo!!.organization)
//                            bundle.putSerializable("CONTACT",contact)

//                            intent.putExtras(bundle)

                        }
                        else if (barcodes[0].geoPoint!=null){
                            Log.d(TAG,"cl  -"+barcodes[0].geoPoint!!.toString())
                        }
                        Log.d(TAG,"raw  -"+barcodes[0].rawValue!!.toString())

                    var barcodesData = barcodes[0].rawValue
                    intent.putExtra("BARCODEDATA", barcodesData)
                        isLaunched=true
                        startActivity(intent)
                    }


                }
            }
            .addOnFailureListener {
                Log.e(TAG, it.message.toString())
            }.addOnCompleteListener {
                // When the image is from CameraX analysis use case, must call image.close() on received
                // images when finished using them. Otherwise, new images may not be received or the camera
                // may stall.
                imageProxy.close()


            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CAMERA){
            if(isCameraPermissionGranted()){
                binding.previewView!!.post{setupCamera()}
            }
            else{
                Toast.makeText(this,"Camera Permission Reqd",Toast.LENGTH_LONG).show()

            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }
}

