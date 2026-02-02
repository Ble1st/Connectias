@file:Suppress("unused")

package com.ble1st.connectias.plugin.streaming

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.io.File
import javax.inject.Singleton

/**
 * Hilt module for serialization configuration
 */
@Module
@InstallIn(SingletonComponent::class)
object SerializationModule {
    
    @Provides
    @Singleton
    fun provideSerializersModule(): SerializersModule {
        return SerializersModule {
            contextual(FileSerializer)
        }
    }
}

/**
 * Custom serializer for File
 */
@Serializer(forClass = File::class)
object FileSerializer : KSerializer<File> {
    override fun serialize(encoder: Encoder, value: File) {
        encoder.encodeString(value.absolutePath)
    }
    
    override fun deserialize(decoder: Decoder): File {
        return File(decoder.decodeString())
    }
}
