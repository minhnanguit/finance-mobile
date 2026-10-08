package com.uit.finance.core.ledger.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.id.UuidGenerator
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.ledger.data.local.LedgerStore
import com.uit.finance.core.ledger.data.local.rejectWith
import com.uit.finance.core.ledger.data.local.save
import com.uit.finance.core.ledger.data.local.toDomain
import com.uit.finance.core.ledger.data.local.toPayload
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryDraft
import com.uit.finance.core.ledger.domain.model.LedgerErrorCode
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.ledger.domain.validation.LedgerLimits
import com.uit.finance.core.ledger.domain.validation.LedgerRules
import com.uit.finance.core.ledger.domain.validation.normalizedOrNull
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.network.api.model.SyncPayloadCodec
import com.uit.finance.core.sync.outbox.OutboxWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class CategoryRepositoryImpl(
    private val store: LedgerStore,
    private val outbox: OutboxWriter,
    private val codec: SyncPayloadCodec,
    private val uuidGenerator: UuidGenerator,
    private val dispatchers: DispatcherProvider,
) : CategoryRepository {

    override fun observeCategories(): Flow<List<Category>> = store.observe(emptyList()) { database ->
        database.categoryQueries.selectActive().asFlow().mapToList(dispatchers.io).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun create(draft: CategoryDraft): AppResult<Category> {
        val icon = draft.icon.normalizedOrNull()
        val color = draft.color.normalizedOrNull()
        val errors = LedgerRules.name(draft.name) + LedgerRules.icon(icon) + LedgerRules.color(color)
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))

        return store.write { database ->
            if (database.categoryQueries.countActive().executeAsOne() >= LedgerLimits.MAX_CATEGORIES) {
                rejectWith("category", LedgerErrorCode.LIMIT_EXCEEDED)
            }
            val category = Category(
                id = uuidGenerator.generate(),
                kind = draft.kind,
                name = draft.name.trim(),
                parentId = draft.parentId,
                icon = icon,
                color = color?.uppercase(),
                templateKey = null,
                archived = false,
            )
            checkNewParent(database, category, previousParentId = null)
            category.also { persist(database, it) }
        }
    }

    override suspend fun update(category: Category): AppResult<Category> {
        val icon = category.icon.normalizedOrNull()
        val color = category.color.normalizedOrNull()
        val errors = LedgerRules.name(category.name) + LedgerRules.icon(icon) + LedgerRules.color(color)
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))

        return store.write { database ->
            val existing = requireActive(database, category.id)
            if (existing.kind != category.kind) rejectWith("kind", LedgerErrorCode.KIND_IMMUTABLE)
            val updated = category.copy(
                name = category.name.trim(),
                icon = icon,
                color = color?.uppercase(),
                templateKey = existing.templateKey, // server quản lý, client không đổi được
            )
            checkNewParent(database, updated, previousParentId = existing.parentId)
            updated.also { persist(database, it) }
        }
    }

    override suspend fun setArchived(categoryId: String, archived: Boolean): AppResult<Unit> = store.write { database ->
        val existing = requireActive(database, categoryId)
        if (existing.archived != archived) persist(database, existing.copy(archived = archived))
    }

    override suspend fun delete(categoryId: String): AppResult<Unit> = store.write { database ->
        requireActive(database, categoryId)
        val inUse = database.categoryQueries.isReferenced(categoryId).executeAsOne() > 0 ||
            database.categoryQueries.hasActiveChildren(categoryId).executeAsOne() > 0
        if (inUse) rejectWith("category", LedgerErrorCode.IN_USE)
        database.categoryQueries.markDeleted(categoryId)
        outbox.delete(database, SyncEntity.CATEGORY, categoryId)
    }

    /** Tối đa 2 cấp, cha cùng kind, cha chưa archive; chỉ kiểm khi cha thật sự đổi (ADR-005 §2). */
    private fun checkNewParent(database: FinanceDatabase, category: Category, previousParentId: String?) {
        val parentId = category.parentId ?: return
        if (parentId == previousParentId) return
        if (parentId == category.id) rejectWith("parentId", LedgerErrorCode.INVALID_PARENT)
        if (previousParentId == null && database.categoryQueries.hasActiveChildren(category.id).executeAsOne() > 0) {
            rejectWith("parentId", LedgerErrorCode.INVALID_PARENT)
        }
        val parent = requireActive(database, parentId, field = "parentId")
        if (parent.parentId != null || parent.kind != category.kind) rejectWith("parentId", LedgerErrorCode.INVALID_PARENT)
        if (parent.archived) rejectWith("parentId", LedgerErrorCode.ARCHIVED)
    }

    private fun persist(database: FinanceDatabase, category: Category) {
        database.save(category)
        outbox.upsert(database, SyncEntity.CATEGORY, category.id, codec.encode(category.toPayload()))
    }

    private fun requireActive(database: FinanceDatabase, categoryId: String, field: String = "category"): Category =
        database.categoryQueries.selectById(categoryId).executeAsOneOrNull()
            ?.takeIf { it.deleted == 0L }
            ?.toDomain()
            ?: rejectWith(field, LedgerErrorCode.NOT_FOUND)
}
