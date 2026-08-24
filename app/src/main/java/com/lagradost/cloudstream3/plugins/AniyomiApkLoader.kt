package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.util.Log
import dalvik.system.PathClassLoader
import java.io.File
import java.util.zip.ZipFile

class AniyomiApkLoader(private val context: Context) {

    private val classLoaderCache = mutableMapOf<String, PathClassLoader>()

    fun getClassLoader(apkFile: File): PathClassLoader {
        val path = apkFile.absolutePath
        return classLoaderCache.getOrPut(path) {
            PathClassLoader(path, context.classLoader)
        }
    }

    fun getMainClassName(apkFile: File): String? {
        return try {
            ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml") ?: return null
                zip.getInputStream(entry).use { inputStream ->
                    val bytes = inputStream.readBytes()
                    extractExtensionClassName(bytes)
                }
            }
        } catch (e: Exception) {
            Log.e("AniyomiApkLoader", "Erro ao extrair nome da classe do APK: ${apkFile.name}", e)
            null
        }
    }

    private fun extractExtensionClassName(manifestBytes: ByteArray): String? {
        return try {
            val content = String(manifestBytes, Charsets.ISO_8859_1)
            val regex = Regex("""eu\.kanade\.tachiyomi\.extension\.[a-zA-Z0-9_.]*""")
            val match = regex.find(content)
            match?.value
        } catch (e: Exception) {
            null
        }
    }

    fun loadExtensionClass(apkFile: File, className: String): Any? {
        return try {
            val loader = getClassLoader(apkFile)
            val clazz = loader.loadClass(className)
            clazz.getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            Log.e("AniyomiApkLoader", "Erro ao instanciar classe da extensão: $className", e)
            null
        }
    }
}
