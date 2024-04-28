package com.ivy.domain.usecase.csv

import com.ivy.base.TestDispatchersProvider
import com.ivy.data.file.FileSystem
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import io.mockk.mockk
import org.junit.Before

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

//    @Test
//    fun `property - num of row and columns matches the format`() = runTest {
//        checkAll(Arb.list(Arb.transaction())) { trns ->
//            // given
//            val accounts = trns.flatMap {
//                listOfNotNull(it.getFromAccount(), it.getToAccount())
//            }.map {
//                Arb.account(accountId = Some(it)).next()
//            }
//            coEvery { accountRepository.findAll(any()) } returns accounts
//            val categories = trns
//                .mapNotNull(Transaction::category)
//                .map {
//                    Arb.category(categoryId = Some(it)).next()
//                }.run {
//                    if (isNotEmpty()) {
//                        drop(Arb.int(indices).bind()).shuffled()
//                    } else {
//                        this
//                    }
//                }
//            coEvery { categoryRepository.findAll(any()) } returns categories
//
//            // when
//            val csv = useCase.exportCsv { trns }
//
//            // then
//            val rows = ReadCsvUseCase().readCsv(csv)
//            rows.size shouldBe trns.size + 1 // +1 for the header
//            rows.forEach { row ->
//                // Matches the expected # of columns
//                val hasExpectedNumOfColumns = row.size == IvyCsvRow.Columns.size
//                if (!hasExpectedNumOfColumns) {
//                    println("(${row.size} cols) $row")
//                }
//                hasExpectedNumOfColumns shouldBe true
//            }
//        }
//    }
}