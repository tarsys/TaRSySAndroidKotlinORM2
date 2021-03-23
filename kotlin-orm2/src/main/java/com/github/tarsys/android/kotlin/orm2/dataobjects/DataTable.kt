package com.github.tarsys.android.kotlin.orm2.dataobjects

class DataTable: Iterable<DataRow> {
    var columns: ArrayList<DataColumn> = arrayListOf()
    var rows: ArrayList<DataRow> = arrayListOf()

    constructor()

    constructor(columns: ArrayList<DataColumn>){
        this.columns.addAll(columns)
    }

    val empty: Boolean = this.rows.size == 0
    fun clone(): DataTable = DataTable(this.columns)
    fun importRow(row: DataRow): DataRow{
        val returnValue = DataRow(this)
        returnValue.putAll(row)

        return returnValue
    }
    fun copy(): DataTable{
        val returnValue = DataTable()
        returnValue.columns.addAll(this.columns)

        this.rows.forEach { r -> returnValue.importRow(r) }

        return returnValue
    }

    override fun iterator(): Iterator<DataRow> {
        return object : Iterator<DataRow> {
            private val dataRows = this@DataTable.rows

            private var currentIndex = 0

            override fun hasNext(): Boolean {
                return currentIndex < dataRows.size
            }

            override fun next(): DataRow {
                return dataRows[currentIndex++]
            }
        }
    }

}