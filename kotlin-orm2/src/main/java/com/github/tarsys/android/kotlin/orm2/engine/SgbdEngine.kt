package com.github.tarsys.android.kotlin.orm2.engine

import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.preference.PreferenceManager
import android.util.Log
import com.github.tarsys.android.kotlin.orm2.Empty
import com.github.tarsys.android.kotlin.orm2.toNotNullString
import dalvik.system.DexFile
import dalvik.system.PathClassLoader
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap
import kotlin.reflect.KClass

@Suppress("DEPRECATION")
class SgbdEngine {
    companion object {

        //region properties (public and private)

        var databaseSQLitePath: String = String().Empty
        var applicationInfo: ApplicationInfo? = null
        private val dbEntities: ArrayList<KClass<*>> = arrayListOf()
        private var entityContainers: String = String().Empty
        private var databaseName: String = String().Empty
        private var databaseFolder: String = String().Empty
        private var entityContainerPackages: ArrayList<String> = arrayListOf()
        private var isExternalStorage: Boolean = false

        //endregion

        fun entityClasses(context: Context, containers: String): ArrayList<KClass<*>>{
            val returnValue: ArrayList<KClass<*>> = arrayListOf()

            if (dbEntities.isEmpty()){
                try{
                    val packages: ArrayList<String> = arrayListOf()
                    val entities = HashMap<String, KClass<*>>()
                    val apkNameTmp = context.packageCodePath
                    val fileApk = File(apkNameTmp)
                    val apksPath = fileApk.parent
                    val pathApks = File(apksPath.toNotNullString())

                    pathApks.listFiles { x -> x?.absolutePath.toNotNullString().endsWith(".apk") }
                        .forEach { apkFile ->
                            val classLoader = PathClassLoader(apkFile.absolutePath, Thread.currentThread().contextClassLoader)

                            try{
                                packages.addAll(listOf(*containers.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()))
                                val dexFile = DexFile(apkFile)
                                dexFile.entries().toList().filter { x -> packages.firstOrNull { p -> x.startsWith(p) } != null }
                                    .forEach { classType ->
                                        try {
                                            val classTable = classLoader.loadClass(classType)?.kotlin

                                            if (classTable?.dbEntity != null) {
                                                if (classTable.simpleName?.isEmpty() == false && !entities.containsKey(classTable.simpleName!!))
                                                    entities[classTable.simpleName!!] = classTable
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                            }catch (ex: java.lang.Exception){
                                Log.e("TarSySORM-Kotlin", ex.message.toNotNullString())
                                ex.printStackTrace()
                            }
                        }

                    returnValue.addAll(entities.values.distinct())
                }catch (ex: java.lang.Exception){
                    Log.e("SGBD::entityClasses", ex.message.toNotNullString())
                    ex.printStackTrace()
                    returnValue.clear()
                }
            }

            return returnValue
        }

        fun databaseSQLite(readOnly: Boolean): SQLiteDatabase?{
            var returnValue: SQLiteDatabase? = null

            if (databaseSQLitePath.isNotEmpty()) {
                val openMode = if (readOnly) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE

                returnValue = try {
                    SQLiteDatabase.openDatabase(databaseSQLitePath, null, SQLiteDatabase.CREATE_IF_NECESSARY or openMode)
                } catch (ex: Exception) {
                    Log.e(SgbdEngine::class.java.toString(),
                        "Exception opening database $databaseSQLitePath:\n$ex"
                    )
                    null
                }

            } else {
                Log.e(SgbdEngine::class.java.toString(), "Database Path not stablished")
            }

            return returnValue
        }

        //region Engine initializers

        /**
         * Initialize SgbdEngine with Manifest metadata info
         */
        fun initialize(context: Context): Boolean{

            applicationInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            val containers = applicationInfo!!.metaData.getString("ENTITY_PACKAGES", "").replace(" ", "")

            return initialize(context, containers)
        }

        fun initialize(context: Context, entityContainers: String): Boolean{
            var returnValue = false
            try{
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)

                if (applicationInfo == null)
                    applicationInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)

                isExternalStorage = applicationInfo!!.metaData.getBoolean("IS_EXTERNALSTORAGE", false)
                dbEntities.clear()
                this.entityContainers = entityContainers
                databaseName = applicationInfo!!.metaData.getString("DATABASE_NAME", "${context.packageName}.db")
                databaseFolder = applicationInfo!!.metaData.getString("DATABASE_DIRECTORY", "")
                databaseSQLitePath = (if (isExternalStorage) Environment.getExternalStorageDirectory().absolutePath + File.separator + databaseFolder
                else Environment.getDataDirectory().absolutePath) + File.separator + databaseName
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val savedAppVersion: Int = if (!File(databaseSQLitePath).exists()) 0 else prefs.getInt("AppVersion", 0)
                val currentAppVersion: Int = packageInfo.versionCode

                if (entityContainers.isNotEmpty()) {
                    entityContainerPackages.addAll(listOf(*entityContainers.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()))
                }

                if (isExternalStorage){
                    if (!File(Environment.getExternalStorageDirectory().absolutePath + File.separator + databaseFolder).exists()) {
                        if (!File(Environment.getExternalStorageDirectory().absolutePath + File.separator + databaseFolder).mkdirs()) {
                            throw IOException("${Environment.getExternalStorageDirectory().absolutePath}${File.separator}${databaseFolder} NOT CREATED!")
                        }
                    }
                }

                if (savedAppVersion != currentAppVersion){
                    val sqlCreation: ArrayList<String> = arrayListOf()

                    entityContainerPackages.forEach {  e ->  sqlCreation.addAll(createSqlQuerys(createDatabaseModel(context, e)))}
                    sqlCreation.forEach { sql -> databaseSQLite(false)!!.execSQL(sql) }

                    prefs.edit().putInt("AppVersion", currentAppVersion).apply()
                }

                returnValue = true
            }catch (ex:Exception){
                Log.e("SgbdEngine", ex.message.toNotNullString())
                ex.printStackTrace()
            }

            return returnValue
        }

        //endregion

        //region Methods for Data Model Object creation

        private fun createDatabaseModel(context: Context, packageName: String): ArrayList<DBTable>{
            val returnValue: ArrayList<DBTable> = arrayListOf()
            val entities: HashMap<String, DBTable> = hashMapOf()

            try{
                entityClasses(context, packageName)
                    .forEach { classTable ->
                        try{
                            if (classTable.dbEntity != null){
                                dbEntities.add(classTable)
                                val tables: ArrayList<DBTable> = classTable.dbTableModel
                                for(table in tables){
                                    if (!entities.containsKey(table.table!!.TableName))
                                        entities[table.table!!.TableName] = table
                                }
                            }
                        }catch (ex: java.lang.Exception){
                            Log.e("CreateDbModel", ex.message.toNotNullString())
                            ex.printStackTrace()
                        }
                    }

                returnValue += entities.values
            }catch (ex: Exception){
                Log.e("SGBD::createDBM", ex.message.toNotNullString())
                ex.printStackTrace()
                returnValue.clear()
            }

            return returnValue
        }

        private fun createSqlQuerys(dataModel: ArrayList<DBTable>): ArrayList<String> {
            val returnValue: ArrayList<String> = arrayListOf()
            val sqliteDb: SQLiteDatabase? = databaseSQLite(true)

            if (sqliteDb != null){
                returnValue += dataModel.flatMap{ x -> x.sqlCreationQuerys(sqliteDb) }

                if (sqliteDb.isOpen) sqliteDb.close()
            }

            return returnValue
        }

        //endregion

        //region Useful methods

        fun contentValuesToFilter(contentValues: ContentValues): Filter?{
            var returnValue: Filter? = null

            try{
                if (contentValues.size() > 0){
                    returnValue = Filter()

                    for (e in contentValues.valueSet()){
                        returnValue.FilterString += if (returnValue.FilterString.trim() == "") "" else " and "
                        returnValue.FilterString += e.key + "=?"
                        returnValue.FilterData.add(e.value.toString())
                    }
                }
            }catch (ex: Exception){
                Log.e("contentValuesToFilter", ex.message.toNotNullString())
                ex.printStackTrace()
                returnValue = null
            }

            return returnValue
        }


        //endregion
    }
}