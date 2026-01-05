package com.ble1st.connectias.feature.barcode.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BarcodeRepository @Inject constructor(
    private val barcodeDao: BarcodeDao
) {
    val allBarcodes: Flow<List<ScannedBarcode>> = barcodeDao.getAllBarcodes().map { entities ->
        entities.map { entity ->
            ScannedBarcode(
                content = entity.content,
                type = entity.type,
                timestamp = entity.timestamp
            )
        }
    }

    suspend fun saveBarcode(barcode: ScannedBarcode) {
        val entity = BarcodeEntity(
            content = barcode.content,
            type = barcode.type,
            timestamp = barcode.timestamp
        )
        barcodeDao.insertBarcode(entity)
    }

    suspend fun clearHistory() {
        barcodeDao.clearAll()
    }
}
