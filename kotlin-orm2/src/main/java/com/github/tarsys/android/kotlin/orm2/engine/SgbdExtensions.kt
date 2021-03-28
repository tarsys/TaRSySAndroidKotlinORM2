package com.github.tarsys.android.kotlin.orm2.engine

import android.database.Cursor
import android.util.Log
import com.github.tarsys.android.kotlin.orm2.Empty
import com.github.tarsys.android.kotlin.orm2.annotations.DBEntity
import com.github.tarsys.android.kotlin.orm2.annotations.Index
import com.github.tarsys.android.kotlin.orm2.annotations.Indexes
import com.github.tarsys.android.kotlin.orm2.annotations.TableField
import com.github.tarsys.android.kotlin.orm2.enums.DBDataType
import com.github.tarsys.android.kotlin.orm2.enums.OrderCriteria
import com.github.tarsys.android.kotlin.orm2.interfaces.IOrmEntity
import com.github.tarsys.android.kotlin.orm2.sqlite.SQLiteSupport
import com.github.tarsys.android.kotlin.orm2.toNotNullString
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaType

//region useful function

fun KClass<*>.createFilteringEntity(vClass: KClass<*>, dataCursor: Cursor): Any? {
    val dbEntity = this.dbEntity
    var returnValue: Any? = null

    try{
        if (dbEntity != null){
            val primaryKeyProperties = this.primaryKeyProperties

            if (primaryKeyProperties.isNotEmpty()){
                returnValue = vClass.primaryConstructor?.call()

                for(pkField in primaryKeyProperties){
                    val fieldName = pkField.foreignKeyFieldName(dbEntity)
                    val fieldValue: Any? = when(pkField.tableField!!.DataType){
                        DBDataType.StringDataType, DBDataType.TextDataType, DBDataType.Serializable -> dataCursor.getString(dataCursor.getColumnIndex(fieldName))
                        DBDataType.RealDataType -> dataCursor.getFloat(dataCursor.getColumnIndex(fieldName))
                        DBDataType.IntegerDataType, DBDataType.LongDataType -> dataCursor.getLong(dataCursor.getColumnIndex(fieldName))
                        DBDataType.DateDataType -> {
                            val ticks = dataCursor.getLong(dataCursor.getColumnIndex(fieldName))
                            val calendar = Calendar.getInstance()
                            calendar.timeInMillis = ticks
                            calendar.time
                        }
                        else -> null
                    }

                    if (fieldValue != null && pkField is KMutableProperty){

                        if (pkField.tableField!!.DataType in arrayOf(DBDataType.IntegerDataType, DBDataType.LongDataType)){
                            try{
                                pkField.setter.call(returnValue, fieldValue as? Long ?: 0)
                            }catch (ex: Exception){
                                if (fieldValue is Int)
                                    pkField.setter.call(returnValue, fieldValue as? Int ?: 0)
                                else
                                    pkField.setter.call(returnValue, (fieldValue as? Long ?: 0).toInt())
                            }
                        }else{
                            pkField.setter.call(returnValue, fieldValue)
                        }
                    }

                }
            }
        }
    }catch (ex: Exception){
        Log.e("createFilteringEntity", ex.message.toNotNullString())
        ex.printStackTrace()
    }

    return returnValue
}

inline fun <reified T>KProperty<*>.getAnnotationHashMap(): HashMap<String, String>
{
    val paramsHash = HashMap<String, String>()
    val annotation = this.annotations.firstOrNull { x -> x.annotationClass.simpleName == T::class.java.simpleName }

    if (annotation != null) {
        val aHandler = Proxy.getInvocationHandler(annotation)
        val returnValue = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java),aHandler) as? T
        val stringObject = returnValue.toString()
        val pRegex = Pattern.compile("(\\w+=\\w*).*?")
        val matches = pRegex.matcher(stringObject)

        // Once we have the parameters in the HashMap, by reflection we create the object directly.
        while (matches.find()){
            val paramValue = matches.group(1)?.split('=')
            if (paramValue != null)
                paramsHash[paramValue[0]] = paramValue[1]
        }
    }

    return paramsHash
}

inline fun <reified T>KClass<*>.getAnnotationHashMap(): HashMap<String, String>
{
    val paramsHash = HashMap<String, String>()
    val annotation = this.annotations.firstOrNull { x -> x.annotationClass.simpleName == T::class.java.simpleName }

    if (annotation != null) {
        val aHandler = Proxy.getInvocationHandler(annotation)
        val returnValue = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java),aHandler) as? T
        val stringObject = returnValue.toString()
        val pRegex = Pattern.compile("(\\w+=\\w*).*?")
        val matches = pRegex.matcher(stringObject)

        // Once we have the parameters in the HashMap, by reflection we create the object directly.
        while (matches.find()){
            val paramValue = matches.group(1)?.split('=')
            if (paramValue != null)
                paramsHash[paramValue[0]] = paramValue[1]
        }
    }

    return paramsHash
}

fun KProperty<*>.fnTableField(tableField: TableField): TableField?{
    var returnValue: TableField? = null
    val ownedTableField = this.tableField

    if (ownedTableField != null){
        returnValue = TableField::class.constructors.first().call(
            if (ownedTableField.FieldName.toLowerCase(Locale.ROOT).isEmpty()) this.name.toLowerCase(Locale.ROOT) else tableField.FieldName.toLowerCase(
                Locale.ROOT),
            tableField.Description,
            tableField.ResourceDescription,
            if (tableField.DataType != DBDataType.None) tableField.DataType else this.dbDataType,
            if (tableField.DataTypeLength == 0) this.dbDataTypeLength else tableField.DataTypeLength,
            tableField.EntityClass,
            tableField.PrimaryKey,
            tableField.ForeignKeyName,
            tableField.ForeignKeyTableName,
            tableField.ForeignKeyFieldName,
            tableField.NotNull,
            tableField.DefaultValue,
            tableField.CascadeDelete,
            tableField.AutoIncrement)
    }

    return returnValue
}

fun KProperty<*>.foreignKeyFieldName(dbEntity: DBEntity): String
{
    if (this.tableField != null){
        return "${dbEntity.TableName.toLowerCase(Locale.ROOT)}_${this.fieldName}"
    }

    return String().Empty
}

fun KClass<*>.entityListRelationTables(addWithoutCascadeDelete: Boolean): List<String> = this.entityListFields
    .filter { x -> x.tableField!!.CascadeDelete || (!addWithoutCascadeDelete && !x.tableField!!.CascadeDelete) }
    .map { f -> "rel_${this.dbEntity!!.TableName.toLowerCase(Locale.ROOT)}_${f.dbEntityClass!!.tableName}" }

//endregion

//region sgbd field properties and methods

val KClass<*>.dbEntity: DBEntity?
    get() {
        var returnValue: DBEntity? = null
        val dbEntityTmpHash = this.getAnnotationHashMap<DBEntity>()

        if (dbEntityTmpHash.isNotEmpty()) {
            returnValue = DBEntity::class.constructors.first()
                .call(if (dbEntityTmpHash["TableName"]?.isEmpty() == true) this.simpleName?.toLowerCase(Locale.ROOT) else dbEntityTmpHash["TableName"],
                    dbEntityTmpHash["Description"] ?: "",
                    dbEntityTmpHash["ResourceDescription"]?.toIntOrNull() ?: 0,
                    dbEntityTmpHash["ResourceDrawable"]?.toIntOrNull() ?: 0
                )
        }

        return returnValue
    }

val KClass<*>.dbTable: DBTable?
    get() {
        var returnValue: DBTable? = null
        val dbEntity: DBEntity? = this.dbEntity

        if (dbEntity != null){
            try{
                returnValue = DBTable()
                val properties = this.memberProperties.filter { x -> x.annotations.any { x -> x.annotationClass.simpleName!!.contains("TableField") } } //this.memberProperties.filter { x -> x.tableField != null }.toList()
                returnValue.relatedClass = this
                returnValue.table = this.dbEntity
                returnValue.fields.addAll(properties.mapNotNull { x -> x.tableField })
                returnValue.indexes += properties.flatMap { x -> x.dbIndexes }


            }catch (ex: Exception){
                returnValue = null
                ex.printStackTrace()
            }
        }

        return returnValue
    }

val KClass<*>.tableName: String
    get() {
        if (this.dbEntity != null)
            return if (this.dbEntity!!.TableName.isNotEmpty()) this.dbEntity!!.TableName else this.simpleName?.toLowerCase(Locale.ROOT) ?: String().Empty

        return String().Empty
    }

val KClass<*>.dbTableModel: ArrayList<DBTable>
    get(){
        val returnValue: ArrayList<DBTable> = arrayListOf()
        val thisModel = this.dbTable

        if (thisModel != null){
            returnValue += this.memberProperties.filter { x -> x.dbEntityClass != null }.flatMap { y -> y.dbEntityClass!!.dbTableModel }
            returnValue += thisModel
        }

        return returnValue
    }

val KClass<*>.primaryKeyFieldNames: ArrayList<String>
    get() {
        val returnValue: ArrayList<String> = arrayListOf()

        if (this.dbEntity != null)
            returnValue += this.memberProperties.filter { x -> x.tableField?.PrimaryKey ?: false }.map { y -> y.tableField!!.FieldName }

        return returnValue
    }

val KClass<*>.withForeignEntities: Boolean
    get() {
        if (this.dbEntity != null){
            return this.memberProperties.firstOrNull { x -> x.tableField?.DataType in arrayOf(DBDataType.EntityDataType, DBDataType.EntityListDataType) } != null
        }

        return false
    }

val KClass<*>.primaryKeyProperties: ArrayList<KProperty<*>>
    get(){
        val returnValue: ArrayList<KProperty<*>> = arrayListOf()

        if (this.dbEntity != null){
            returnValue += this.memberProperties.filter { x -> x.tableField?.PrimaryKey ?: false }
        }

        return returnValue
    }

val KClass<*>.entityFields: ArrayList<KProperty<*>>
    get(){
        val returnValue: ArrayList<KProperty<*>> = arrayListOf()

        if (this.dbEntity != null){
            returnValue += this.memberProperties.filter { x -> x.tableField!= null && x.returnType::class.dbEntity != null }
        }

        return returnValue
    }

val KClass<*>.entityListFields: ArrayList<KProperty<*>>
    get(){
        val returnValue: ArrayList<KProperty<*>> = arrayListOf()

        if (this.dbEntity != null){
            returnValue += this.memberProperties.filter { x -> (x.tableField?.DataType ?: DBDataType.None) == DBDataType.EntityListDataType }
        }

        return returnValue
    }

val KClass<*>.tableFieldProperties: ArrayList<KProperty<*>>
    get(){
        val returnValue: ArrayList<KProperty<*>> = arrayListOf()

        if (this.dbEntity != null){
            returnValue += this.memberProperties.filter { x -> x.tableField != null }
        }

        return returnValue
    }


val KProperty<*>.tableField: TableField?
    get(){
        var returnValue: TableField? = null
        val tableFieldHash = this.getAnnotationHashMap<TableField>()

        if (tableFieldHash.isNotEmpty()){
            returnValue = TableField::class.constructors.first().call(
                if (tableFieldHash["FieldName"]?.toLowerCase(Locale.ROOT)?.isEmpty() == true) this.name.toLowerCase(Locale.ROOT) else tableFieldHash["FieldName"]!!.toLowerCase(Locale.ROOT),
                tableFieldHash["Description"],
                tableFieldHash["ResourceDescription"]?.toIntOrNull() ?: 0,
                if (tableFieldHash["DataType"] != "None") DBDataType.DataType(tableFieldHash["DataType"]?: "StringDataType" ) else this.dbDataType,
                tableFieldHash["DataTypeLength"]?.toIntOrNull() ?: this.dbDataTypeLength,
                if (tableFieldHash["EntityClass"].isNullOrEmpty() || tableFieldHash["EntityClass"].equals("class", true)) String::class else Class.forName(tableFieldHash["EntityClass"]!!),
                tableFieldHash["PrimaryKey"].toBoolean(),
                tableFieldHash["ForeignKeyName"],
                tableFieldHash["ForeignKeyTableName"],
                tableFieldHash["ForeignKeyFieldName"],
                tableFieldHash["NotNull"].toBoolean(),
                tableFieldHash["DefaultValue"],
                tableFieldHash["CascadeDelete"].toBoolean(),
                tableFieldHash["AutoIncrement"].toBoolean()
            )
        }

        return returnValue
    }

/**
 * Pendiente agregar control a Annotation @Indexes
 */
val KProperty<*>.dbIndexes: ArrayList<Index>
    get(){
        val returnValue: ArrayList<Index> = arrayListOf()

        val simpleIndexHashMap: HashMap<String, String> = this.getAnnotationHashMap<Index>()
        val collectionIndexesHashMap = this.getAnnotationHashMap<Indexes>()

        if (simpleIndexHashMap.isNotEmpty()){
            returnValue += Index::class.constructors.first().call(
                simpleIndexHashMap["IndexName"],
                simpleIndexHashMap["IndexFields"],
                simpleIndexHashMap["IsUniqueIndex"].toBoolean(),
                simpleIndexHashMap["Collation"],
                OrderCriteria.valueOf(simpleIndexHashMap["Order"] ?: "Asc")
            )
        }

        if (collectionIndexesHashMap.isNotEmpty()){
            //val collectionIndexes: Indexes = null
            //if (collectionIndexes != null) returnValue += collectionIndexes.value
        }



        return returnValue
    }

val KProperty<*>.dbDataType: DBDataType
    get(){
        var returnValue: DBDataType = when (this.returnType){
            Boolean::class.createType() -> DBDataType.BooleanDataType
            Int::class.createType() -> DBDataType.IntegerDataType
            Long::class.createType() -> DBDataType.LongDataType
            Float::class.createType(), Double::class.createType() -> DBDataType.RealDataType
            Date::class.createType() -> DBDataType.DateDataType
            else -> DBDataType.StringDataType
        }

        if (this.returnType != Boolean::class.createType() &&
            this.returnType != Int::class.createType() &&
            this.returnType != Long::class.createType() &&
            this.returnType != Float::class.createType() &&
            this.returnType != Date::class.createType() &&
            this.returnType != String::class.createType()){

            when {
                this.returnType.javaClass.isEnum -> {
                    returnValue = DBDataType.EnumDataType
                }
                this.returnType.isSubtypeOf(IOrmEntity::class.starProjectedType) -> {
                    returnValue = DBDataType.EntityDataType
                }
                this.returnType.isSubtypeOf(ArrayList::class.starProjectedType) -> {
                    returnValue = DBDataType.EntityListDataType
                }
            }



        }

        return returnValue
    }

val KProperty<*>.dbDataTypeLength: Int
    get(){
        val tableField = this.tableField

        return when (this.returnType){
            String::class.createType() -> if ((tableField?.DataTypeLength ?: 0) > 0) tableField!!.DataTypeLength else SgbdEngine.applicationInfo?.metaData?.getInt("DB_STRING_DEFAULT_LENGTH", 500) ?: 500
            else -> 0
        }
    }

val KProperty<*>.dbEntityClass: KClass<*>?
    get(){
        val ownedTableField: TableField? = this.tableField

        if (ownedTableField?.EntityClass != null) return ownedTableField.EntityClass

        if (ownedTableField?.DataType in arrayOf(DBDataType.EntityDataType, DBDataType.EntityListDataType)){
            if (ownedTableField!!.DataType == DBDataType.EntityDataType){
                if (this.returnType::class.dbEntity != null)
                    return this.returnType::class
            }else{
                val returnClass = this.returnType::class.java

                if (returnClass == ArrayList::class.java && this.returnType.javaType is ParameterizedType){
                    val pType = this.returnType.javaType as ParameterizedType
                    val rType = pType.actualTypeArguments.firstOrNull()?.javaClass?.kotlin
                    if (rType?.dbEntity != null)
                        return rType
                }
            }
        }

        return null
    }

val KProperty<*>.fieldName: String
    get() = "${(if(this.tableField!!.DataType == DBDataType.DateDataType) SQLiteSupport.PREFIX_DATE_FIELD else String().Empty)}${this.tableField!!.FieldName}"

//endregion
