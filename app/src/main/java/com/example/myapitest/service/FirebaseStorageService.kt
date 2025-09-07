package com.example.myapitest.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.util.UUID
import androidx.core.content.edit

object FirebaseStorageService {

    private val storage = FirebaseStorage.getInstance()
    private val imagesRef: StorageReference = storage.reference.child("car_images")

    /**
     * Faz upload de uma imagem apenas se necessário
     * @return URL da imagem (nova ou existente)
     */
    suspend fun uploadImageIfNeeded(uri: Uri, context: Context): String {
        return try {
            //Verifica se a URI já é uma URL do Firebse
            if (isFirebaseStorageUri(uri.toString())) {
                return uri.toString()
            }
            
            //Verifica se é uma URI local que já foi enviada anteriormente
            val cacheUrl = checkIfAlreadyUploaded(uri, context)
            if (cacheUrl.isNotEmpty()) {
                return cacheUrl
            }
            
            uploadNewImage(uri)
        } catch (e: Exception) {
            throw Exception("Erro no upload da imagem $e.message")
        }
    }

    /**
     * Faz upload de uma nova imagem
     */
    private suspend fun uploadNewImage(uri: Uri): String {
        val fileName = "${UUID.randomUUID()}.jpg"
        val imageRef = imagesRef.child(fileName)

        val uploadTask = imageRef.putFile(uri).await()
        val downloadUrl = imageRef.downloadUrl.await()

        return downloadUrl.toString()
    }

    private suspend fun checkIfAlreadyUploaded(uri: Uri, context: Context): String {
        return try {
            //Gera hash do único arquivo para a verificação
            val fileHash = generateFileHash(uri, context)

            //Verifica no cache local se este arquivo já foi enviado
            val prefs = context.getSharedPreferences("image_uploads", Context.MODE_PRIVATE)
            val cachedUrl = prefs.getString(fileHash, "")

            cachedUrl ?: ""
        } catch (e: Exception) {
            Log.e("FirebaseStorageService", "Erro ao verificar se a imagem já foi enviada $e.message")
            ""
        }
    }

    /**
     * Gera um hash único para o arquivo baseado no conteúdo
     */
    private fun generateFileHash(uri: Uri, context: Context): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: byteArrayOf()
            val hash = bytes.contentHashCode()
            hash.toString()
        } catch (e: Exception) {
            // Fallback: usa URI + tamanho do arquivo
            "${uri.toString()}_${getFileSize(uri, context)}"
        }
    }

    private fun getFileSize(uri: Uri, context: Context): Long {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val size = cursor?.use {
                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                it.moveToFirst()
                it.getLong(sizeIndex)
            } ?: 0L
            cursor?.close()
            size
        } catch (e: Exception) {
            0L
        }
    }

    private fun saveUploadCache(uri: Uri, imageUrl: String, context: Context) {
        try {
            val fileHash = generateFileHash(uri, context)
            val prefs = context.getSharedPreferences("image_uploads", Context.MODE_PRIVATE)
            prefs.edit().putString(fileHash, imageUrl).apply()
        } catch (e: Exception) {
            //Ignorar erros de cache
        }
    }

    /**
     * Upload seguro com todas as validações
     */
    suspend fun safeUploadImage(uri: Uri, context: Context): String {
        return try {
            // Se já é uma URL do Firebase, retorna direto
            if (isFirebaseStorageUri(uri.toString())) {
                return uri.toString()
            }

            // Verifica se já foi enviada
            val cachedUrl = checkIfAlreadyUploaded(uri, context)
            if (cachedUrl.isNotEmpty() && isFirebaseStorageUrl(cachedUrl)) {
                return cachedUrl
            }

            // Faz upload da nova imagem
            val newImageUrl = uploadNewImage(uri)

            // Salva no cache
            saveUploadCache(uri, newImageUrl, context)

            newImageUrl

        } catch (e: Exception) {
            throw Exception("Erro no upload seguro: ${e.message}")
        }
    }

    /**
     * Limpa o cache de uploads
     */
    fun clearUploadCache(context: Context) {
        val prefs = context.getSharedPreferences("image_uploads", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    /**
     * metodo original mantido para compatibilidade
     */
    suspend fun uploadImage(uri: Uri): String {
        return uploadNewImage(uri)
    }

    suspend fun deleteImage(imageUrl: String) {
        try {
            if (imageUrl.isNotEmpty()) {
                val httpsRefrence = storage.getReferenceFromUrl(imageUrl)
                httpsRefrence.delete().await()
            }
        }   catch (e: Exception) {
            println("Erro ao deletar imagem ${e.message}")
        }
    }

    /**
     * Verifica se uma URL é do Firebase Storage
     */
    fun isFirebaseStorageUrl(url: String): Boolean {
        return url.startsWith("https://firebasestorage.googleapis.com/") ||
                url.startsWith("gs://") ||
                url.contains("firebasestorage")
    }

    private fun isFirebaseStorageUri(uriString: String): Boolean {
        return uriString.startsWith("https://") && isFirebaseStorageUrl(uriString)
    }
}