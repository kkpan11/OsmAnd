package net.osmand.shared.gpx

import net.osmand.shared.extensions.format
import net.osmand.shared.util.LoggerFactory
import net.osmand.shared.gpx.GpxParameter.*
import net.osmand.shared.io.CommonFile
import net.osmand.shared.routing.ColoringType
import net.osmand.shared.util.PlatformUtil

class GpxDatabase {

	companion object {
		val log = LoggerFactory.getLogger("GpxDatabase")

		const val DB_VERSION = 25
		const val DB_NAME = "gpx_database"
		const val GPX_TABLE_NAME = "gpxTable"
		const val GPX_DIR_TABLE_NAME = "gpxDirTable"
		const val TMP_NAME_COLUMN_COUNT = "itemsCount"
		const val UNKNOWN_TIME_THRESHOLD = 10L
		val GPX_UPDATE_PARAMETERS_START = "UPDATE $GPX_TABLE_NAME SET "
		val GPX_FIND_BY_NAME_AND_DIR =
			" WHERE ${FILE_NAME.columnName} = ? AND ${FILE_DIR.columnName} = ?"
		val GPX_MIN_CREATE_DATE =
			"SELECT MIN(${FILE_CREATION_TIME.columnName}) FROM $GPX_TABLE_NAME WHERE ${FILE_CREATION_TIME.columnName} > $UNKNOWN_TIME_THRESHOLD"
		val GPX_MAX_COLUMN_VALUE = "SELECT MAX(%s) FROM $GPX_TABLE_NAME"
		val CHANGE_NULL_TO_EMPTY_STRING_QUERY_PART =
			"case when %1\$s is null then '' else %1\$s end as %1\$s"
		val INCLUDE_NON_NULL_COLUMN_CONDITION = " WHERE %1\$s NOT NULL AND %1\$s <> '' "
		val GET_ITEM_COUNT_COLLECTION_BASE =
			"SELECT %s, count (*) as $TMP_NAME_COLUMN_COUNT FROM $GPX_TABLE_NAME%s group by %s ORDER BY %s %s"
	}

	init {
		val db = openConnection(false)
		db?.close()
	}

	fun openConnection(readonly: Boolean): SQLiteConnection? {
		var conn = app.getSQLiteAPI().getOrCreateDatabase(DB_NAME, readonly)
		if (conn == null) return null

		if (conn.getVersion() < DB_VERSION) {
			if (readonly) {
				conn.close()
				conn = app.getSQLiteAPI().getOrCreateDatabase(DB_NAME, false)
			}
			if (conn == null) return null
			val version = conn.getVersion()
			conn.setVersion(DB_VERSION) // not correct version but dangerous for crash loop
			if (version == 0) {
				GpxDbUtils.onCreate(conn)
			} else {
				GpxDbUtils.onUpgrade(this, conn, version, DB_VERSION)
			}
		}
		return conn
	}

	fun updateDataItem(item: DataItem): Boolean {
		val file = item.file
		val tableName = GpxDbUtils.getTableName(file)
		val map = GpxDbUtils.getItemParameters(item)
		return updateGpxParameters(map, tableName, GpxDbUtils.getItemRowsToSearch(file))
	}

	private fun updateGpxParameters(
		rowsToUpdate: Map<GpxParameter, Any>,
		tableName: String,
		rowsToSearch: Map<String, Any>
	): Boolean {
		val db = openConnection(false)
		db?.use {
			return updateGpxParameters(it, tableName, rowsToUpdate, rowsToSearch)
		}
		return false
	}

	private fun updateGpxParameters(
		db: SQLiteConnection,
		tableName: String,
		rowsToUpdate: Map<GpxParameter, Any>,
		rowsToSearch: Map<String, Any>
	): Boolean {
		val map = GpxDbUtils.convertGpxParameters(rowsToUpdate)
		val pair = AndroidDbUtils.createDbUpdateQuery(tableName, map, rowsToSearch)
		db.execSQL(pair.first, *pair.second)
		return true
	}

	fun rename(currentFile: CommonFile, newFile: CommonFile): Boolean {
		val map = linkedMapOf(
			FILE_NAME to newFile.name(),
			FILE_DIR to GpxDbUtils.getGpxFileDir(newFile)
		)
		val tableName = GpxDbUtils.getTableName(currentFile)
		return updateGpxParameters(map, tableName, GpxDbUtils.getItemRowsToSearch(currentFile))
	}

	fun remove(file: CommonFile): Boolean {
		val db = openConnection(false)
		db?.use {
			val fileName = file.name()
			val fileDir = GpxDbUtils.getGpxFileDir(file)
			val tableName = GpxDbUtils.getTableName(file)
			db.execSQL(
				"DELETE FROM $tableName $GPX_FIND_BY_NAME_AND_DIR",
				arrayOf(fileName, fileDir)
			)
			return true
		}
		return false
	}

	fun add(item: DataItem): Boolean {
		val db = openConnection(false)
		db?.use {
			insertItem(item, it)
			return true
		}
		return false
	}

	private fun insertItem(item: DataItem, db: SQLiteConnection) {
		val file = item.getFile()
		val tableName = GpxDbUtils.getTableName(file)
		val map = GpxDbUtils.convertGpxParameters(GpxDbUtils.getItemParameters(item))
		db.execSQL(
			AndroidDbUtils.createDbInsertQuery(tableName, map.keys),
			map.values.toTypedArray()
		)
	}

	private fun readGpxDataItem(query: SQLiteCursor): GpxDataItem {
		val file = readItemFile(query)
		val item = GpxDataItem(file)
		val analysis = GpxTrackAnalysis()
		processItemParameters(item, query, entries, analysis)
		item.setAnalysis(analysis)
		return item
	}

	private fun readItemFile(query: SQLiteCursor): CommonFile {
		var fileDir: String = query.getString(query.getColumnIndex(FILE_DIR.columnName))
		val fileName = query.getString(query.getColumnIndex(FILE_NAME.columnName))

		val appDir = PlatformUtil.getAppDir()
		val gpxDir = PlatformUtil.getGpxDir()
		if ("$fileName" == gpxDir.name()) {
			return gpxDir
		}
		fileDir = fileDir.replace(gpxDir.toString(), "")
		fileDir = fileDir.replace(appDir.toString(), "")
		val dir = if (fileDir.isEmpty()) gpxDir else CommonFile(gpxDir, fileDir)
		return CommonFile(dir, fileName)
	}

	private fun processItemParameters(
		item: DataItem,
		query: SQLiteCursor,
		parameters: List<GpxParameter>,
		analysis: GpxTrackAnalysis?
	) {
		for (parameter in parameters) {
			var value = GpxDbUtils.queryColumnValue(query, parameter)
			if (value == null && !parameter.isAppearanceParameter()) {
				value = parameter.defaultValue
			}
			if (parameter.analysisParameter) {
				analysis?.setGpxParameter(parameter, value)
			} else {
				if (parameter == COLOR) {
					value = GpxUtilities.parseColor(value as String)
				} else if (parameter == COLORING_TYPE) {
					val type = value as String
					var coloringType = ColoringType.valueOf(ColoringPurpose.TRACK, type)
					val scaleType = GradientScaleType.getGradientTypeByName(type)
					if (coloringType == null && scaleType != null) {
						coloringType = ColoringType.valueOf(scaleType)
						value = coloringType?.getName(null)
					}
				}
				item.setParameter(parameter, value)
			}
		}
	}

	private fun readGpxDirItem(query: SQLiteCursor): GpxDirItem {
		val file = readItemFile(query)
		val item = GpxDirItem(file)
		processItemParameters(item, query, GpxParameter.getGpxDirParameters(), null)
		return item
	}

	fun getTracksMinCreateDate(): Long {
		var minDate = -1L
		val db = openConnection(false)
		db?.use {
			val query = db.rawQuery(GPX_MIN_CREATE_DATE, null)
			query?.use {
				if (query.moveToFirst()) {
					minDate = query.getLong(0)
				}
			}
		}
		return minDate
	}

	fun getColumnMaxValue(parameter: GpxParameter): String {
		var maxValue = ""
		val db = openConnection(false)
		db?.use {
			val queryString = String.format(GPX_MAX_COLUMN_VALUE, parameter.columnName)
			val query = db.rawQuery(queryString, null)
			query?.use {
				if (query.moveToFirst()) {
					maxValue = query.getString(0)
				}
			}
		}
		return maxValue
	}

	fun getStringIntItemsCollection(
		columnName: String,
		includeEmptyValues: Boolean,
		sortByName: Boolean,
		sortDescending: Boolean
	): List<Pair<String, Int>> {
		val column1 =
			if (includeEmptyValues) CHANGE_NULL_TO_EMPTY_STRING_QUERY_PART.format(columnName) else columnName
		val includeEmptyValuesPart =
			if (includeEmptyValues) "" else INCLUDE_NON_NULL_COLUMN_CONDITION.format(columnName)
		val orderBy = if (sortByName) columnName else TMP_NAME_COLUMN_COUNT
		val sortDirection = if (sortDescending) "DESC" else "ASC"
		val query = GET_ITEM_COUNT_COLLECTION_BASE.format(
			column1,
			includeEmptyValuesPart,
			columnName,
			orderBy,
			sortDirection
		)
		return getStringIntItemsCollection(query)
	}

	private fun getStringIntItemsCollection(dataQuery: String): List<Pair<String, Int>> {
		val folderCollection = mutableListOf<Pair<String, Int>>()
		val db = openConnection(false)
		db?.use {
			val query = db.rawQuery(dataQuery, null)
			query?.use {
				if (query.moveToFirst()) {
					do {
						folderCollection.add(Pair(query.getString(0), query.getInt(1)))
					} while (query.moveToNext())
				}
			}
		}
		return folderCollection
	}

	fun getGpxDataItems(): List<GpxDataItem> {
		val items = mutableSetOf<GpxDataItem>()
		val db = openConnection(false)
		db?.use {
			val query = db.rawQuery(GpxDbUtils.getSelectGpxQuery(), null)
			query?.use {
				if (query.moveToFirst()) {
					do {
						items.add(readGpxDataItem(query))
					} while (query.moveToNext())
				}
			}
		}
		return items.toList()
	}

	fun getGpxDirItems(): List<GpxDirItem> {
		val items = mutableSetOf<GpxDirItem>()
		val db = openConnection(false)
		db?.use {
			val query = db.rawQuery(GpxDbUtils.getSelectGpxDirQuery(), null)
			query?.use {
				if (query.moveToFirst()) {
					do {
						items.add(readGpxDirItem(query))
					} while (query.moveToNext())
				}
			}
		}
		return items.toList()
	}

	fun getGpxDataItem(file: CommonFile): GpxDataItem? {
		return if (GpxDbUtils.isGpxFile(file)) {
			getDataItem(file) as? GpxDataItem
		} else {
			null
		}
	}

	fun getGpxDirItem(file: CommonFile): GpxDirItem? {
		return if (file.isDirectory()) {
			getDataItem(file) as? GpxDirItem
		} else {
			null
		}
	}

	private fun getDataItem(file: CommonFile): DataItem? {
		val db = openConnection(false)
		db?.use {
			return getDataItem(file, it)
		}
		return null
	}

	private fun getDataItem(file: CommonFile, db: SQLiteConnection): DataItem? {
		val name = file.name()
		val dir = GpxDbUtils.getGpxFileDir(file)
		val gpxFile = GpxDbUtils.isGpxFile(file)
		val selectQuery =
			if (gpxFile) GpxDbUtils.getSelectGpxQuery() else GpxDbUtils.getSelectGpxDirQuery()
		val query = db.rawQuery("$selectQuery $GPX_FIND_BY_NAME_AND_DIR", arrayOf(name, dir))
		query?.use {
			if (query.moveToFirst()) {
				return if (gpxFile) readGpxDataItem(query) else readGpxDirItem(query)
			}
		}
		return null
	}
}

