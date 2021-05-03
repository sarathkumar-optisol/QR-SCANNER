package com.gv.qrgscanner

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gv.qrgscanner.Constants.QR_CALENDER
import com.gv.qrgscanner.Constants.QR_CONTACT
import com.gv.qrgscanner.Constants.QR_DL
import com.gv.qrgscanner.Constants.QR_EMAIL
import com.gv.qrgscanner.Constants.QR_GEOPOINT
import com.gv.qrgscanner.Constants.QR_PHONE
import com.gv.qrgscanner.Constants.QR_SMS
import com.gv.qrgscanner.Constants.QR_URL
import com.gv.qrgscanner.Constants.QR_WIFI
import com.gv.qrgscanner.Constants.isLaunched
import com.gv.qrgscanner.databinding.ActivityDataBinding
import com.gv.qrgscanner.models.ContactInfo

class DataActivity : AppCompatActivity() {
    private lateinit var binding : ActivityDataBinding
    private lateinit var intentTitle:String

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val textView = findViewById<TextView>(R.id.textView)

        var data: String? = intent.getStringExtra("BARCODEDATA")
        var mail=intent.getStringExtra("MAIL")
        var type = intent.getStringExtra("QRTYPE")
        when(type){
            QR_EMAIL ->
                    {   binding.actionButton.visibility= View.VISIBLE
                        binding.actionButton.text=getString(R.string.send_mail)

                    }
            QR_CONTACT->
                    {
                        binding.actionButton.visibility= View.VISIBLE
                        binding.actionButton.text=getString(R.string.add_contact)
                        var contactInfo=intent .getSerializableExtra("bundle") as ContactInfo
                        Log.d("DAta"," conta "+contactInfo.name)
                    }
            QR_CALENDER->
                    {
                        binding.actionButton.visibility= View.VISIBLE
                        binding.actionButton.text=getString(R.string.add_calender_event)
                    }
            QR_SMS->
            {
                binding.actionButton.visibility= View.VISIBLE
                binding.actionButton.text=getString(R.string.send_sms)
            }
            QR_WIFI->
            {
                binding.actionButton.visibility= View.VISIBLE
                binding.actionButton.text=getString(R.string.copy_password)
            }
            QR_URL->
            {
                binding.actionButton.visibility= View.VISIBLE
                binding.actionButton.text=getString(R.string.open_url)
            }
            QR_GEOPOINT->
            {
                binding.actionButton.visibility= View.VISIBLE
                binding.actionButton.text=getString(R.string.open_location)
            }
            QR_DL ->
            {
                binding.actionButton.visibility= View.GONE
//                binding.actionButton.text=getString(R.string.open_location)
            }
            QR_PHONE->
                    {
                        binding.actionButton.visibility=View.GONE
                        binding.actionIcon.visibility=View.VISIBLE

                    }
            else -> {
                binding.actionButton.visibility=View.GONE
            }
        }
        binding.actionButton.setOnClickListener {

            /** mail **/
             intent = Intent(Intent.ACTION_SEND)
            intent.setType("message/rfc822");
            var mails= arrayOf("toaddress@gmail.com")
            intent.putExtra(Intent.EXTRA_EMAIL, mails )
            intent.putExtra(Intent.EXTRA_SUBJECT, "Subject xxxx")
            intent.putExtra(Intent.EXTRA_TEXT, "I'm email body.")
            intentTitle="Send Email"
            startActivity(Intent.createChooser(intent, intentTitle))


//            /**browswe**/
//            var url: String? = "http://www.example.com"
//             intent = Intent(Intent.ACTION_VIEW)
//            intent.setData(Uri.parse(url));
//            startActivity(Intent.createChooser(intent,"browsee"))

        }
        textView.text = data

    }

    override fun onBackPressed() {
        super.onBackPressed()
        isLaunched = false
    }
}