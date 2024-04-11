package com.ivy.data.repository.impl

import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.DataWriteEvent
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.dao.write.WriteCategoryDao
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.RepositoryMemoFactory
import com.ivy.data.repository.mapper.CategoryMapper
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val mapper: CategoryMapper,
    private val writeCategoryDao: WriteCategoryDao,
    private val categoryDao: CategoryDao,
    private val dispatchersProvider: DispatchersProvider,
    memoFactory: RepositoryMemoFactory,
) : CategoryRepository {

    private val memo = memoFactory.createMemo(
        getDataWriteSaveEvent = DataWriteEvent::SaveCategories,
        getDateWriteDeleteEvent = DataWriteEvent::DeleteCategories,
    )

    override suspend fun findAll(deleted: Boolean): List<Category> = memo.findAll(
        findAllOperation = {
            categoryDao.findAll(deleted).mapNotNull {
                with(mapper) { it.toDomain() }.getOrNull()
            }
        },
        sortMemo = { sortedBy(Category::orderNum) }
    )

    override suspend fun findById(id: CategoryId): Category? = memo.findById(
        id = id,
        findByIdOperation = {
            categoryDao.findById(it.value)?.let {
                with(mapper) { it.toDomain() }.getOrNull()
            }
        }
    )

    override suspend fun findMaxOrderNum(): Double = if (memo.findAllMemoized) {
        memo.items.maxOfOrNull { (_, acc) -> acc.orderNum } ?: 0.0
    } else {
        withContext(dispatchersProvider.io) {
            categoryDao.findMaxOrderNum() ?: 0.0
        }
    }

    override suspend fun save(value: Category): Unit = memo.save(
        value = value,
    ) {
        writeCategoryDao.save(
            with(mapper) { it.toEntity() }
        )
    }

    override suspend fun saveMany(values: List<Category>): Unit = memo.saveMany(
        values = values,
    ) {
        writeCategoryDao.saveMany(
            values.map { with(mapper) { it.toEntity() } }
        )
    }

    override suspend fun deleteById(id: CategoryId) = memo.deleteById(
        id = id
    ) {
        writeCategoryDao.deleteById(it.value)
    }

    override suspend fun deleteAll() = memo.deleteAll(writeCategoryDao::deleteAll)
}
