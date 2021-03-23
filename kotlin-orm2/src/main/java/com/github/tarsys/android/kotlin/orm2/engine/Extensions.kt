package com.github.tarsys.android.kotlin.orm2

import android.content.ContentValues
import android.util.Log
import com.github.tarsys.android.kotlin.orm2.engine.Filter

fun Any?.toNotNullString() = this?.toString() ?: ""

val String.Empty: String
    get() = ""

fun Boolean.toInt() = if (this) 1 else 0

val ContentValues.toFilter: Filter?
    get(){
        try{
            if (this.size() > 0){
                val returnValue = Filter()
                for (entry in this.valueSet()){
                    returnValue.FilterString += if (returnValue.FilterString.trim() == String().Empty) String().Empty else " and "
                    returnValue.FilterString += "${entry.key}=?"
                    returnValue.FilterData.add(entry.value.toString())
                }

                return returnValue
            }
        }catch (ex:java.lang.Exception){
            Log.e("ContentValues.toFilter", ex.message.toNotNullString())
            ex.printStackTrace()
        }

        return null
    }