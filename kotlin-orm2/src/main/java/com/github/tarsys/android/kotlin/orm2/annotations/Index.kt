package com.github.tarsys.android.kotlin.orm2.annotations

import com.github.tarsys.android.kotlin.orm2.enums.OrderCriteria
import kotlin.annotation.Retention

@Retention(AnnotationRetention.RUNTIME)
annotation class Index(
    val IndexName: String = "",
    val IndexFields: Array<String> = arrayOf(""),
    val IsUniqueIndex: Boolean = false,
    val Collation: String = "",
    val Order: OrderCriteria = OrderCriteria.Asc
)