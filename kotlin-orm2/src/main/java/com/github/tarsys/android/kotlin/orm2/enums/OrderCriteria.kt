package com.github.tarsys.android.kotlin.orm2.enums

enum class OrderCriteria {
    Asc {
        override fun toString(): String {
            return "Asc"
        }
    },
    Desc {
        override fun toString(): String {
            return "Desc"
        }
    }
}
