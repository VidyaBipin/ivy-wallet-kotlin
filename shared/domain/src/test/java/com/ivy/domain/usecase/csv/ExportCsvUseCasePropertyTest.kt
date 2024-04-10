package com.ivy.domain.usecase.csv

import arrow.core.Some
import com.ivy.base.TestDispatchersProvider
import com.ivy.data.file.FileSystem
import com.ivy.data.model.Transaction
import com.ivy.data.model.getFromAccount
import com.ivy.data.model.getToAccount
import com.ivy.data.model.testing.account
import com.ivy.data.model.testing.category
import com.ivy.data.model.testing.transaction
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ExportCsvUseCasePropertyTest {

    private val accountRepository = mockk<AccountRepository>()
    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)
    private val transactionRepository = mockk<TransactionRepository>()
    private val fileSystem = mockk<FileSystem>()

    private lateinit var useCase: ExportCsvUseCase

    @Before
    fun setup() {
        useCase = ExportCsvUseCase(
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            transactionRepository = transactionRepository,
            dispatchers = TestDispatchersProvider,
            fileSystem = fileSystem,
        )
    }

    @Test
    fun `property - num of columns matches the format`() = runTest {
        checkAll(Arb.list(Arb.transaction())) { trns ->
            // given
            val accounts = trns.flatMap {
                listOfNotNull(it.getFromAccount(), it.getToAccount())
            }.map {
                Arb.account(accountId = Some(it)).next()
            }
            coEvery { accountRepository.findAll(any()) } returns accounts
            val categories = trns
                .mapNotNull(Transaction::category)
                .map {
                    Arb.category(categoryId = Some(it)).next()
                }.run {
                    drop(Arb.int(indices).bind()).shuffled()
                }
            coEvery { categoryRepository.findAll(any()) } returns categories

            // when
            val csv = useCase.exportCsv { trns }

            // then
            val rows = ReadCsvUseCase().readCsv(csv)
            rows.shouldNotBeEmpty()
            rows.forEach { row ->
                // Matches the expected # of columns
                println(row)
                row.size shouldBe IvyCsvRow.Columns.size
            }
        }
    }
}