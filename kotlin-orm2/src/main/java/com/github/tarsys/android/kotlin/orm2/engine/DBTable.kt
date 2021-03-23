package com.github.tarsys.android.kotlin.orm2.engine

import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import com.github.tarsys.android.kotlin.orm2.Empty
import com.github.tarsys.android.kotlin.orm2.annotations.DBEntity
import com.github.tarsys.android.kotlin.orm2.annotations.Index
import com.github.tarsys.android.kotlin.orm2.annotations.TableField
import com.github.tarsys.android.kotlin.orm2.enums.DBDataType
import com.github.tarsys.android.kotlin.orm2.sqlite.SQLiteSupport
import java.util.*
import kotlin.collections.ArrayList
import kotlin.reflect.KClass

class DBTable {
    var relatedClass: KClass<*>? = null
    var table: DBEntity? = null
    val fields: ArrayList<TableField> = arrayListOf()
    val indexes: ArrayList<Index> = arrayListOf()

    private fun sqlCreateForeignKey(foreignKeyTable: DBTable): String{
        var returnValue: String = String().Empty

        if (this.table != null){
            val foreignKeyTableName = "rel_${this.table!!.TableName.toLowerCase(Locale.ROOT)}_${foreignKeyTable.table!!.TableName.toLowerCase(Locale.ROOT)}"
            var foreignKeyFieldsDefinition: String = String().Empty
            val primaryKeyForeignKeyFields: ArrayList<String> = arrayListOf()

            this.fields.filter { f -> f.PrimaryKey }
                .forEach { fPk ->
                    primaryKeyForeignKeyFields.add("${this.table!!.TableName.toLowerCase(Locale.ROOT)}_${fPk.FieldName}")
                    foreignKeyFieldsDefinition += if (foreignKeyFieldsDefinition.isEmpty()) String().Empty else ", "
                    foreignKeyFieldsDefinition += "${this.table!!.TableName.toLowerCase(Locale.ROOT)}_${if(fPk.DataType == DBDataType.DateDataType) SQLiteSupport.PREFIX_DATE_FIELD else String().Empty}${fPk.FieldName} ${fPk.DataType.SqlType(fPk.DataTypeLength)}"

                }

            foreignKeyTable.fields.filter { f -> f.PrimaryKey }
                .forEach { fPk ->
                    primaryKeyForeignKeyFields.add("${foreignKeyTable.table!!.TableName.toLowerCase(Locale.ROOT)}_${fPk.FieldName}")
                    foreignKeyFieldsDefinition += if (foreignKeyFieldsDefinition.isEmpty()) String().Empty else ", "
                    foreignKeyFieldsDefinition += "${foreignKeyTable.table!!.TableName.toLowerCase(Locale.ROOT)}_${if(fPk.DataType == DBDataType.DateDataType) SQLiteSupport.PREFIX_DATE_FIELD else String().Empty}${fPk.FieldName} ${fPk.DataType.SqlType(fPk.DataTypeLength)}"

                }

            val pKeyFK = ", PRIMARY KEY(${TextUtils.join(", ", primaryKeyForeignKeyFields)})"
            returnValue = "create table if not exists $foreignKeyTableName($foreignKeyFieldsDefinition$pKeyFK)"
        }

        return returnValue
    }

    fun sqlCreateTable(): ArrayList<String> {
        val returnValue: ArrayList<String> = arrayListOf()

        if (this.table != null){
            var createTable = "create table if not exists ${this.table!!.TableName.toLowerCase(Locale.ROOT)}"
            var paramDefinition: String = String().Empty
            val primaryKeyFields: ArrayList<String> = arrayListOf()

            for(field in this.fields){
                if (field.PrimaryKey){
                    if (field.DataType !in arrayOf( DBDataType.EntityDataType, DBDataType.EntityListDataType)){
                        primaryKeyFields += field.FieldName
                    }else if (field.DataType == DBDataType.EntityDataType && field.EntityClass.dbEntity != null){
                        val entityFieldPKeys = field.EntityClass.primaryKeyFieldNames
                        primaryKeyFields += entityFieldPKeys.map { x -> "${field.EntityClass.tableName}_${x}" }
                    }
                }


                if (field.DataType !in arrayOf( DBDataType.EntityDataType, DBDataType.EntityListDataType)){
                    // Primitive fields
                    paramDefinition += if (paramDefinition.trim().isEmpty()) String().Empty else ", "
                    paramDefinition += "${if(field.DataType == DBDataType.DateDataType) SQLiteSupport.PREFIX_DATE_FIELD else String().Empty}${field.FieldName} " +
                            "${field.DataType.SqlType(field.DataTypeLength)} " +
                            "${if (field.NotNull) "not null" else String().Empty } " +
                            if (field.NotNull && field.DefaultValue.isNotEmpty()) "default ${field.DefaultValue}" else String().Empty
                }else{
                    if (field.DataType == DBDataType.EntityDataType){
                        for(fkField in field.EntityClass.primaryKeyProperties){
                            paramDefinition += if (paramDefinition.isNotEmpty()) ", " else String().Empty
                            paramDefinition += "${field.EntityClass.tableName}_${"${if (fkField.tableField!!.DataType == DBDataType.DateDataType) SQLiteSupport.PREFIX_DATE_FIELD else ""}${fkField.tableField!!.FieldName}" } ${fkField.tableField!!.DataType.SqlType(fkField.tableField!!.DataTypeLength)}"
                        }
                    }else{
                        returnValue.add(this.sqlCreateForeignKey(field.EntityClass.dbTable!!))
                    }
                }
            }
            val pKey = if (primaryKeyFields.size > 0) ", PRIMARY KEY(" + TextUtils.join(", ", primaryKeyFields) + ")" else ""
            createTable += " ($paramDefinition$pKey)"
            returnValue.add(createTable)
        }
        return returnValue
    }
    fun sqlCreationQuerys(sqliteDb: SQLiteDatabase): ArrayList<String>{
        val returnValue: ArrayList<String> = arrayListOf()
        if (this.table != null){
            if (!SQLiteSupport.tableExistsInDataBase(sqliteDb, this.table!!.TableName)){
                returnValue += this.sqlCreateTable()
            }else{
                for (field in this.fields){
                    if (field.DataType !in arrayOf(DBDataType.EntityDataType, DBDataType.EntityListDataType)){
                        // add new primitive fields to table...
                        val fieldName = "${if (field.DataType == DBDataType.DateDataType) SQLiteSupport.PREFIX_DATE_FIELD else String().Empty }${field.FieldName}"
                        if (!SQLiteSupport.existsTableField(sqliteDb, this.table!!.TableName, fieldName)){
                            returnValue += ("alter table ${this.table!!.TableName.toLowerCase(Locale.ROOT)} add column $fieldName " +
                                    "${field.DataType.SqlType(field.DataTypeLength)} ${if (field.NotNull) " not null" else String().Empty } " +
                                    if (field.NotNull && field.DefaultValue.isNotEmpty()) "default ${field.DefaultValue}" else String().Empty)
                        }
                    }else{
                        if (field.DataType == DBDataType.EntityDataType){
                            for(pField in field.EntityClass.primaryKeyProperties){
                                val fieldName = "${field.EntityClass.tableName}_${if (pField.dbDataType == DBDataType.DateDataType) SQLiteSupport.PREFIX_DATE_FIELD else String().Empty}${pField.tableField!!.FieldName}"
                                if (!SQLiteSupport.existsTableField(sqliteDb, this.table!!.TableName, fieldName)){
                                    returnValue += ("alter table ${this.table!!.TableName.toLowerCase(Locale.ROOT)} add column $fieldName ${pField.tableField!!.DataType.SqlType(pField.tableField!!.DataTypeLength)}")
                                }
                            }
                        }else{
                            val foreignTable = "rel_${this.table!!.TableName.toLowerCase(Locale.ROOT)}_${field.EntityClass.tableName.toLowerCase(Locale.ROOT)}"
                            if (!SQLiteSupport.tableExistsInDataBase(sqliteDb, foreignTable)){
                                returnValue += this.sqlCreateForeignKey(field.EntityClass.dbTable!!)
                            }
                        }
                    }
                }
            }
            // Finally add indexes...
            for (index in this.indexes){
                returnValue += "drop index if exists ${index.IndexName}"
                returnValue += "create ${if (index.IsUniqueIndex) "unique" else String().Empty} index if not exists ${index.IndexName} on ${this.table!!.TableName.toLowerCase(Locale.ROOT)} (${index.IndexFields.joinToString(", ")})"
            }
        }
        return returnValue
    }
}