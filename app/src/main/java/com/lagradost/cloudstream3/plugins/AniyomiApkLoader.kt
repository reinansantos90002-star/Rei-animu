package com.lagradost.cloudstream3.plugins

import android.content.Context
import dalvik.system.PathClassLoader
import java.io.File

class AniyomiApkLoader(private val context: Context) {

    /**
     * Carrega uma classe principal de uma extensão Aniyomi (.apk) via PathClassLoader.
     * 
     * @param apkFile Arquivo .apk da extensão salvo localmente.
     * @param mainClassName Nome completo da classe (ex: "eu.kanade.tachiyomi.animeextension.pt.smartanimes.SmartAnimes")
     */
    fun loadExtensionClass(apkFile: File, mainClassName: String): Any? {
        if (!apkFile.exists()) {
            throw IllegalArgumentException("Arquivo APK não encontrado em: ${apkFile.absolutePath}")
        }

        // 1. Diretório onde o sistema extrai bibliotecas nativas (.so), se houver
        val nativeLibDir = File(context.codeCacheDir, "aniyomi_libs_${apkFile.nameWithoutExtension}").apply {
            if (!exists()) mkdirs()
        }

        // 2. Cria o ClassLoader isolado apontando para o APK e usando o ClassLoader da aplicação como pai
        val classLoader = PathClassLoader(
            apkFile.absolutePath,
            nativeLibDir.absolutePath,
            context.classLoader
        )

        return try {
            // 3. Carrega a classe principal da extensão
            val loadedClass = classLoader.loadClass(mainClassName)
            
            // 4. Instancia a classe via construtor padrão (sem argumentos)
            loadedClass.getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Executa a busca popular/latest invocando métodos por reflexão no objeto instanciado.
     */
    fun fetchPopularAnimesReflection(instance: Any, page: Int = 1): Any? {
        return try {
            // Aniyomi/Tachiyomi usam 'popularAnimeRequest(page)' que retorna um okhttp3.Request
            // ou 'fetchPopularAnime(page)' que retorna um rx.Observable/Single
            val method = instance.javaClass.getMethod("popularAnimeRequest", Int::class.javaPrimitiveType)
            method.invoke(instance, page)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

