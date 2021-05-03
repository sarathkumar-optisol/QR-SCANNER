package com.gv.qrgscanner.models

import com.google.mlkit.vision.barcode.Barcode
import java.io.Serializable


data class ContactInfo(
        var address: String,
        var mail:  String,
        var name: String,
        var org:String
        ) : Serializable