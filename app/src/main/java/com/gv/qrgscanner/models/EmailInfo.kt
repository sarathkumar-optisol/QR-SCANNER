package com.gv.qrgscanner.models

import java.io.Serializable

/**
 * Created by SARATH on 15-04-2021
 */
data class EmailInfo(
    var address: String,
    var subject: String,
    var body: String
) : Serializable