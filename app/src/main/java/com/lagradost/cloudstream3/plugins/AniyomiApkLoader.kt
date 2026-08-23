package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File

class AniyomiApkLoader(private val context: Context) {

    private val TAG = "AniyomiApkLoader"

    /**
     * Descobre a classe principal da extensão Aniyomi lendo os metadados do APK
     */
    fun getMainClassName(apkFile: File): String? {
        return try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_META_DATA
            ) ?: return null

            val appInfo = packageInfo.applicationInfo ?: return null
            appInfo.sourceDir = apkFile.absolutePath
            appInfo.publicSourceDir = apkFile.absolutePath

            val metaData = appInfo.metaData
            if (metaData != null && metaData.containsKey("animeextension.class")) {
                return metaData.getString("animeextension.class")
            }

            // Fallback caso esteja declarado em formato alternativo
            packageInfo.services?.firstOrNull()?.name
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao extrair a classe principal do APK: ${apkFile.name}", e)
            null
        }
    }

    /**
     * Carrega dinamicamente a classe da extensão injetando o Context do Android
     */
    fun loadExtensionClass(apkFile: File, className: String): Any? {
        return try {
            val optimizedDir = context.codeCacheDir
            val classLoader = DexClassLoader(
                apkFile.absolutePath,
                optimizedDir.absolutePath,
                null,
                context.classLoader
            )

            val loadedClass = classLoader.loadClass(className)

            // Tenta instanciar passando o Context (exigido por extensions do Aniyomi)
            try {
                val constructor = loadedClass.getConstructor(Context::class.java)
                constructor.newInstance(context)
            } catch (e: NoSuchMethodException) {
                // Fallback para construtor padrão sem parâmetros
                val defaultConstructor = loadedClass.getDeclaredConstructor()
                defaultConstructor.isAccessible = true
                defaultConstructor.newInstance()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao instanciar $className do arquivo ${apkFile.name}", e)
            null
        }
    }
}
