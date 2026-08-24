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
                    val xmlText = String(bytes, Charsets.UTF_8)

                    val classMatch = Regex("""eu\.kanade\.tachiyomi\.extension\.[a-zA-Z0-9_.]*""").find(xmlText)
                    classMatch?.value
                }
            }
        } catch (e: Exception) {
            Log.e("AniyomiApkLoader", "Erro ao extrair nome da classe do APK", e)
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
