package com.ble1st.connectias.api

import kotlin.reflect.KClass

interface StorageService {
    suspend fun putString(key: String, value: String)
    suspend fun getString(key: String): String?
    suspend fun putObject(key: String, value: Any)
    suspend fun <T : Any> getObject(key: String, type: KClass<T>): T?
    suspend fun remove(key: String)
    suspend fun clear()
}
