package com.github.tarsys.android.kotlin.orm2.dataobjects

import com.github.tarsys.android.kotlin.orm2.Empty
import java.io.Serializable
import kotlin.reflect.KClass

class DataColumn: Serializable {
    var ColumnName: String = String().Empty
    var ColumnTitle: String = String().Empty
    var ColumnDataType: KClass<*> = String::class
    var Visible: Boolean = false
}