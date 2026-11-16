package com.folioscan.core.database

import androidx.room.TypeConverter
import com.folioscan.core.model.DocumentType
import com.folioscan.core.model.OcrStatus
import com.folioscan.core.model.PageFilter

class FolioTypeConverters {
    @TypeConverter
    fun fromDocumentType(value: DocumentType): String = value.name

    @TypeConverter
    fun toDocumentType(value: String): DocumentType = DocumentType.valueOf(value)

    @TypeConverter
    fun fromOcrStatus(value: OcrStatus): String = value.name

    @TypeConverter
    fun toOcrStatus(value: String): OcrStatus = OcrStatus.valueOf(value)

    @TypeConverter
    fun fromPageFilter(value: PageFilter): String = value.name

    @TypeConverter
    fun toPageFilter(value: String): PageFilter = PageFilter.valueOf(value)
}
