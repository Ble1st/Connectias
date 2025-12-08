package com.ble1st.connectias.feature.document.di

import android.content.Context
import com.ble1st.connectias.feature.document.export.PdfExporter
import com.ble1st.connectias.feature.document.ocr.OcrEngine
import com.ble1st.connectias.feature.document.ocr.TrainedDataManager
import com.ble1st.connectias.feature.document.scan.ImageProcessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DocumentModule {

    @Provides
    @Singleton
    fun provideImageProcessor(): ImageProcessor = ImageProcessor()

    @Provides
    @Singleton
    fun provideTrainedDataManager(@ApplicationContext context: Context): TrainedDataManager =
        TrainedDataManager(context)

    @Provides
    @Singleton
    fun provideOcrEngine(trainedDataManager: TrainedDataManager): OcrEngine =
        OcrEngine(trainedDataManager)

    @Provides
    @Singleton
    fun providePdfExporter(): PdfExporter = PdfExporter()
}
