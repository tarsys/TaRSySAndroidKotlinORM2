package com.github.tarsys.android.kotlin.orm2.annotations

import com.github.tarsys.android.kotlin.orm2.enums.OrderCriteria
import kotlin.annotation.Retention
@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Indexes(val value: Array<Index>)