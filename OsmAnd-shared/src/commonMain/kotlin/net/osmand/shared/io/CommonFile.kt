package net.osmand.shared.io

import okio.FileMetadata
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.Sink
import okio.Source
import okio.buffer
import okio.use

class CommonFile {
	val path: Path

	constructor(filePath: String) {
		this.path = filePath.toPath()
	}

	constructor(path: Path) {
		this.path = path
	}

	constructor(file: CommonFile, fileName: String) {
		this.path = file.path.resolve(fileName)
	}

	fun name(): String = path.name

	fun parent(): Path? = path.parent

	@Throws(IOException::class)
	fun exists(): Boolean = FileSystem.SYSTEM.exists(path)

	fun isAbsolute(): Boolean = path.isAbsolute

	fun isDirectory(): Boolean =
		FileSystem.SYSTEM.metadataOrNull(path)?.isDirectory == true

	@Throws(IOException::class)
	fun lastModified(): Long {
		val metadata: FileMetadata? = FileSystem.SYSTEM.metadataOrNull(path)
		return metadata?.lastModifiedAtMillis ?: 0
	}

	fun path(): String = path.toString()

	@Throws(IOException::class)
	fun absolutePath(): String = FileSystem.SYSTEM.canonicalize(path).toString()

	@Throws(IOException::class)
	fun source(): Source = FileSystem.SYSTEM.source(path)

	@Throws(IOException::class)
	fun sink(): Sink = FileSystem.SYSTEM.sink(path)

	@Throws(IOException::class)
	fun createDirectories() = FileSystem.SYSTEM.createDirectories(path)

	@Throws(IOException::class)
	fun readText(): String {
		return FileSystem.SYSTEM.source(path).buffer().use {
			it.readUtf8()
		}
	}

	@Throws(IOException::class)
	fun writeText(text: String) {
		FileSystem.SYSTEM.sink(path).buffer().use {
			it.writeUtf8(text)
		}
	}

	override fun toString(): String {
		return path.toString()
	}
}