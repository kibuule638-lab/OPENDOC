package com.folioscan.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.folioscan.core.model.DocumentType
import com.folioscan.core.model.OcrStatus
import com.folioscan.core.model.PageFilter

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["updatedAt"]),
        Index(value = ["isVault", "isTrashed"])
    ]
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: DocumentType,
    val folderId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val pdfPath: String? = null,
    val ocrStatus: OcrStatus = OcrStatus.NONE,
    val isVault: Boolean = false,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val coverThumbPath: String? = null,
    val totalSizeBytes: Long = 0L
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId"])]
)
data class PageEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val index: Int,
    val imagePath: String,
    val thumbPath: String,
    val width: Int,
    val height: Int,
    val rotation: Int = 0,
    val filter: PageFilter = PageFilter.ORIGINAL,
    val adjustmentsJson: String = "{}",
    val redactionsJson: String = "[]",
    val signatureJson: String = "[]"
)

@Entity(
    tableName = "folders",
    indices = [Index(value = ["parentId"])]
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val parentId: String? = null,
    val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String
)

@Entity(
    tableName = "document_tag_cross_ref",
    primaryKeys = ["documentId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["tagId"])
    ]
)
data class DocumentTagCrossRef(
    val documentId: String,
    val tagId: String
)

@Entity(
    tableName = "page_texts",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId"])]
)
data class PageTextEntity(
    @PrimaryKey val pageId: String,
    val documentId: String,
    val text: String
)

@Fts4(contentEntity = PageTextEntity::class)
@Entity(tableName = "page_texts_fts")
data class PageTextFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int,
    val documentId: String,
    val text: String
)

@Entity(
    tableName = "identity_data",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId"], unique = true)]
)
data class IdentityDataEntity(
    @PrimaryKey val documentId: String,
    val fieldsJson: String, // Encrypted via KeystoreCryptoManager
    val mrzRaw: String?,    // Encrypted via KeystoreCryptoManager
    val mrzValid: Boolean
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long
)

data class DocumentWithTags(
    @Embedded val document: DocumentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = DocumentTagCrossRef::class,
            parentColumn = "documentId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)

data class PageWithText(
    @Embedded val page: PageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "pageId"
    )
    val pageText: PageTextEntity?
)

data class FolderWithCount(
    @Embedded val folder: FolderEntity,
    val itemCount: Int
)

data class FtsSearchRow(
    val documentId: String,
    val matchedText: String
)
