package com.ivy.reports

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ivy.core.action.FlowViewModel
import com.ivy.core.action.calculate.transaction.GroupTrnsFlow
import com.ivy.core.action.currency.BaseCurrencyFlow
import com.ivy.core.ui.temp.trash.TimePeriod
import com.ivy.data.CurrencyCode
import com.ivy.data.transaction.*
import com.ivy.reports.ReportFilterEvent.*
import com.ivy.reports.ReportFilterEvent.SelectAmount.AmountType
import com.ivy.reports.ReportFilterEvent.SelectKeyword.KeywordsType
import com.ivy.reports.ReportViewModel.DataHolder
import com.ivy.reports.actions.ReportAccountsFlow
import com.ivy.reports.actions.ReportCategoriesFlow
import com.ivy.reports.actions.ReportFilterTrnsFlow
import com.ivy.reports.actions.calculate.CalculateWithTransfersFlow
import com.ivy.reports.actions.calculate.ExtendedStats
import com.ivy.reports.data.*
import com.ivy.reports.extensions.*
import com.ivy.reports.template.TemplateDataHolder
import com.ivy.reports.template.actions.TemplateListFlow
import com.ivy.reports.template.functions.toTemplate
import com.ivy.reports.template.functions.toUiState
import com.ivy.wallet.domain.deprecated.logic.csv.ExportCSVLogic
import com.ivy.wallet.utils.computationThread
import com.ivy.wallet.utils.replace
import com.ivy.wallet.utils.uiThread
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ReportViewModel @Inject constructor(
    private val baseCurrencyFlow: BaseCurrencyFlow,
    private val reportCategoriesFLow: ReportCategoriesFlow,
    private val reportsAccountsFlow: ReportAccountsFlow,
    private val calculateWithTransfersFlow: CalculateWithTransfersFlow,
    private val reportFilterTrnsFlow: ReportFilterTrnsFlow,
    private val groupTrnsFlow: GroupTrnsFlow,
    private val exportCSVLogic: ExportCSVLogic,
    private val templateListFlow: TemplateListFlow
) : FlowViewModel<DataHolder, ReportUiState, ReportsEvent>() {

    private val loading = MutableStateFlow(false)
    private val filterVisible = MutableStateFlow(false)

    private val templateScreenVisible = MutableStateFlow(false)
    private val templateSaveScreenVisible = MutableStateFlow(false)
    private val templateName = MutableStateFlow("")

    private val selectedTrnTypes = MutableStateFlow<List<TrnType>>(emptyList())
    private val period = MutableStateFlow<TimePeriod?>(null)
    private val selectedAccounts = MutableStateFlow<List<SelectableAccount>>(emptyList())
    private val selectedCategories = MutableStateFlow<List<SelectableReportsCategory>>(emptyList())
    private val minAmount = MutableStateFlow<Double?>(null)
    private val maxAmount = MutableStateFlow<Double?>(null)
    private val includeKeywords = MutableStateFlow<List<String>>(emptyList())
    private val excludeKeywords = MutableStateFlow<List<String>>(emptyList())
    private val selectedPlannedPayments =
        MutableStateFlow<List<ReportPlannedPaymentType>>(emptyList())
    private val transfersAsIncomeExpense = MutableStateFlow(false)

    init {
        viewModelScope.launch(Dispatchers.Default) {
            reportsAccountsFlow(Unit).collect {
                selectedAccounts.value = it
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            reportCategoriesFLow(Unit).collect {
                selectedCategories.value = it
            }
        }
    }

    override fun initialState(): DataHolder = DataHolder.empty()

    override fun stateFlow(): Flow<DataHolder> = dataFlow()

    override fun mapToUiState(state: StateFlow<DataHolder>): StateFlow<ReportUiState> =
        state.toUiState()

    override suspend fun handleEvent(event: ReportsEvent) {
        computationThread {
            handleReportFilterEvent(event)
        }
    }

    private fun dataFlow() = combine(
        baseCurrencyFlow(),
        loading,
        statsFlow(),
        trnsFlow(),
        grpTrnsListFlow(),
        filterVisible,
        reportFilterStateFlow(),
        reportTemplateFlow(),
        templateScreenVisible,
        templateSaveScreenVisible,
    ) { baseCurr, loading, stats, allTrnsList, grpTrnsList, visible, filter, templateFlow, templateScreenVisible, templateSaveScreenVisible ->
        DataHolder(
            baseCurrency = baseCurr,
            loading = loading,
            extendedStats = stats,
            allTransactions = allTrnsList,
            grpTrnsList = grpTrnsList,
            filterVisible = visible,
            filterState = filter,
            templateVisible = templateScreenVisible,
            templateSaveScreenVisible = templateSaveScreenVisible,
            templateState = templateFlow
        )
    }.onStart {
        DataHolder.empty()
    }

    private fun statsFlow() = trnsFlow().flatMapLatest { list ->
        baseCurrencyFlow().flatMapMerge { currencyCode ->
            calculateWithTransfersFlow(
                CalculateWithTransfersFlow.Input(
                    trns = list,
                    outputCurrency = currencyCode,
                    accounts = getSelectedAccounts(),
                    treatTransfersAsIncExp = state.value.filterState.transfersAsIncomeExpense
                )
            )
        }
    }.onStart { emit(ExtendedStats.empty()) }

    private fun grpTrnsListFlow() = trnsFlow()
        .flatMapLatest {
            groupTrnsFlow(it)
        }
        .onStart { emptyTransactionList() }

    private fun trnsFlow() = reportFilterStateFlow().flatMapLatest {
        reportFilterTrnsFlow(it)
    }.onStart { emit(emptyList()) }

    private fun reportFilterStateFlow() = combine(
        selectedTrnTypes,
        period,
        selectedAccounts,
        selectedCategories,
        minAmount,
        maxAmount,
        includeKeywords,
        excludeKeywords,
        selectedPlannedPayments,
        transfersAsIncomeExpense
    ) { trnTypes, period, acc, cat, minAmt, maxAmt, incKeywords, excKeywords, planPymt, transferIncExp ->

        ReportFilterState(
            selectedTrnTypes = trnTypes,
            period = period,
            selectedAccounts = acc,
            selectedCategories = cat,
            minAmount = minAmt,
            maxAmount = maxAmt,
            includeKeywords = incKeywords,
            excludeKeywords = excKeywords,
            selectedPlannedPayments = planPymt,
            transfersAsIncomeExpense = transferIncExp
        )
    }.onStart {
        emit(ReportFilterState.empty())
    }

    private fun StateFlow<DataHolder>.toUiState() = this.map {
        val transactionStats = it.extendedStats

        val transactionsOld = it.allTransactions.toOld()
        val accountIdFilters = getSelectedAccounts().map { a -> a.id }

        val header = HeaderUiState(
            balance = transactionStats.balance,
            income = transactionStats.income,
            expenses = transactionStats.expense,

            incomeTransactionsCount = transactionStats.incomesCount,
            expenseTransactionsCount = transactionStats.expensesCount,

            transactionsOld = transactionsOld.toImmutableItem(),
            accountIdFilters = accountIdFilters.toImmutableItem(),
            treatTransfersAsIncExp = it.filterState.transfersAsIncomeExpense
        )

        val filterUiState = with(it.filterState) {
            FilterUiState(
                selectedTrnTypes = selectedTrnTypes.toImmutableItem(),
                period = ImmutableData(period),
                selectedAcc = selectedAccounts.toImmutableItem(),
                selectedCat = selectedCategories.toImmutableItem(),

                minAmount = minAmount,
                maxAmount = maxAmount,

                includeKeywords = includeKeywords.toImmutableItem(),
                excludeKeywords = excludeKeywords.toImmutableItem(),

                selectedPlannedPayments = selectedPlannedPayments.toImmutableItem(),
                treatTransfersAsIncExp = transfersAsIncomeExpense,

                showSaveTemplateOption = it.templateState.selectedTemplate != null
            )
        }

        ReportUiState(
            baseCurrency = it.baseCurrency,
            loading = it.loading,

            headerUiState = header.toImmutableItem(),
            trnsList = it.grpTrnsList.toImmutableItem(),

            filterVisible = it.filterVisible,
            filterUiState = filterUiState,

            templateVisible = it.templateVisible,
            templateSaveModalVisible = it.templateSaveScreenVisible,
            selectedTemplateUiState = it.templateState.selectedTemplate?.toUiState(),

            templateDataHolder = it.templateState
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyReportUiState(""),
        )

    private suspend fun handleReportFilterEvent(event: ReportsEvent) {
        when (event) {
            is ReportsEvent.FilterOptions -> filterVisible.value = event.visible

            is ReportsEvent.Template -> templateScreenVisible.value = event.visible

            is ReportsEvent.Export -> {
                export(event.context, event.fileUri, event.onFinish)
            }

            is ReportsEvent.SaveTemplate -> {
                templateSaveScreenVisible.value = event.visible
            }

            is ReportsEvent.TemplateName -> templateName.value = event.name

            is ReportsEvent.FilterEvent -> {
                handleFilterEvent(event.filterEvent)
            }
        }
    }

    private suspend fun export(context: Context, fileUri: Uri, onFinish: (Uri) -> Unit) {
        val allTrns = state.value.allTransactions

        if (allTrns.isEmpty()) return

        loading.update { true }

        exportCSVLogic.exportToFile(
            context = context,
            fileUri = fileUri,
            exportScope = {
                allTrns.toOld()
            }
        )

        uiThread {
            onFinish(fileUri)
        }

        loading.update { false }
    }

    private fun handleFilterEvent(filterEvent: ReportFilterEvent) {
        when (filterEvent) {
            is SelectTrnsType ->
                updateTrnsTypeSelection(filterEvent.type, filterEvent.checked)

            is SelectPeriod ->
                updatePeriodSelection(timePeriod = filterEvent.timePeriod)

            is SelectAccount -> updateAccountSelection(
                account = filterEvent.account
            )

            is SelectCategory ->
                updateCategorySelection(category = filterEvent.category)

            is SelectAmount ->
                updateAmountSelection(amt = filterEvent.amt, amountType = filterEvent.amountType)

            is SelectKeyword -> updateIncludeExcludeKeywords(
                keyword = filterEvent.keyword,
                keywordsType = filterEvent.keywordsType,
                add = filterEvent.add
            )

            is SelectPlannedPayment ->
                updatePlannedPaymentSelection(type = filterEvent.type, add = filterEvent.add)

            is TreatTransfersAsIncExp -> transfersAsIncomeExpense.value =
                filterEvent.transfersAsIncExp

            is Clear ->
                onFilterClear(event = filterEvent)

            is SelectAll ->
                onSelectAll(event = filterEvent)
        }
    }

    private fun updateTrnsTypeSelection(
        type: TrnType,
        add: Boolean
    ) {
        selectedTrnTypes.value = selectedTrnTypes.value.addOrRemove(add = add, type)
    }

    private fun updatePeriodSelection(
        timePeriod: TimePeriod
    ) {
        period.value = timePeriod
    }

    private fun updateAccountSelection(
        account: SelectableAccount
    ) {
        val newAccount = account.switchSelected()
        val selectableAccounts = selectedAccounts.value.replace(oldComp = { a ->
            a.account.id == newAccount.account.id
        }, newAccount)

        selectedAccounts.value = selectableAccounts
    }

    private fun updateCategorySelection(
        category: SelectableReportsCategory
    ) {
        val newCategory = category.switchSelected()
        val allCats = selectedCategories.value.replace(oldComp = { c ->
            when {
                (newCategory.selectableCategory is ReportCategoryType.Cat) &&
                        (c.selectableCategory is ReportCategoryType.Cat) &&
                        c.selectableCategory.cat.id == newCategory.selectableCategory.cat.id -> true

                newCategory.selectableCategory is ReportCategoryType.None
                        && c.selectableCategory is ReportCategoryType.None -> true

                else -> false
            }
        }, newCategory)

        selectedCategories.value = allCats
    }

    private fun updateAmountSelection(
        amt: Double?,
        amountType: AmountType
    ) {
        when (amountType) {
            AmountType.MIN -> minAmount.value = amt
            AmountType.MAX -> maxAmount.value = amt
        }
    }

    private fun updateIncludeExcludeKeywords(
        keyword: String,
        keywordsType: KeywordsType,
        add: Boolean
    ) {
        val trimmedKeyword = keyword.trim()

        val existingKeywords = when (keywordsType) {
            KeywordsType.INCLUDE -> includeKeywords.value
            KeywordsType.EXCLUDE -> excludeKeywords.value
        }

        val updatedKeywords = if (add && trimmedKeyword !in existingKeywords)
            existingKeywords + listOf(trimmedKeyword)
        else if (!add)
            existingKeywords - listOf(trimmedKeyword).toSet()  //Converting to Set for performance benefits
        else
            existingKeywords

        when (keywordsType) {
            KeywordsType.INCLUDE -> includeKeywords.value = updatedKeywords
            KeywordsType.EXCLUDE -> excludeKeywords.value = updatedKeywords
        }
    }

    private fun updatePlannedPaymentSelection(
        type: ReportPlannedPaymentType,
        add: Boolean
    ) {
        selectedPlannedPayments.value =
            selectedPlannedPayments.value.addOrRemove(add = add, item = type)
    }

    private fun onFilterClear(
        event: Clear
    ) {
        when (event) {
            Clear.Accounts ->
                selectedAccounts.value = selectedAccounts.value.map { it.copy(selected = false) }
            Clear.Categories ->
                selectedCategories.value =
                    selectedCategories.value.map { it.copy(selected = false) }

            is Clear.Filter -> {
                selectedTrnTypes.value = emptyList()
                period.value = null
                selectedAccounts.value = selectedAccounts.value.map { it.copy(selected = false) }
                selectedCategories.value =
                    selectedCategories.value.map { it.copy(selected = false) }
                minAmount.value = null
                maxAmount.value = null

                includeKeywords.value = emptyList()
                excludeKeywords.value = emptyList()

                selectedPlannedPayments.value = emptyList()
                transfersAsIncomeExpense.value = false
            }
        }
    }

    private fun onSelectAll(
        event: SelectAll
    ) {
        when (event) {
            is SelectAll.Accounts ->
                selectedAccounts.value = selectedAccounts.value.map { it.copy(selected = true) }
            SelectAll.Categories ->
                selectedCategories.value = selectedCategories.value.map { it.copy(selected = true) }
        }
    }

    private fun getSelectedAccounts() =
        state.value.filterState.selectedAccounts.filter { it.selected }.map { it.account }

    private fun <T> List<T>.addOrRemove(add: Boolean, item: T): List<T> {
        return if (add)
            this + item
        else
            this - item
    }

    private fun List<Transaction>.toOld(): List<TransactionOld> {
        return this.map { newTrans ->

            val toOldTrans = TransactionOld(
                accountId = newTrans.account.id,
                type = TrnType.INCOME,
                amount = newTrans.value.amount.toBigDecimal(),
                title = newTrans.title,
                description = newTrans.description,
                dateTime = (newTrans.time as? TrnTime.Actual)?.actual,
                categoryId = newTrans.category?.id,
                dueDate = (newTrans.time as? TrnTime.Due)?.due,
                recurringRuleId = newTrans.metadata.recurringRuleId,
                loanId = newTrans.metadata.loanId,
                loanRecordId = newTrans.metadata.loanRecordId,
                id = newTrans.id,
            )

            when (newTrans.type) {
                TransactionType.Income -> toOldTrans.copy(type = TrnType.INCOME)
                TransactionType.Expense -> toOldTrans.copy(type = TrnType.EXPENSE)
                else -> toOldTrans.copy(
                    type = TrnType.TRANSFER,
                    toAmount = (newTrans.type as TransactionType.Transfer).toValue.amount.toBigDecimal(),
                    toAccountId = (newTrans.type as TransactionType.Transfer).toAccount.id
                )
            }
        }
    }

    data class DataHolder(
        val baseCurrency: CurrencyCode,
        val loading: Boolean,
        val extendedStats: ExtendedStats,
        val allTransactions: List<Transaction>,
        val grpTrnsList: TransactionsList,
        val filterVisible: Boolean,
        val filterState: ReportFilterState,
        val templateVisible: Boolean,
        val templateSaveScreenVisible: Boolean = false,
        val templateState: TemplateDataHolder
    ) {
        companion object {
            fun empty() =
                DataHolder(
                    baseCurrency = "",
                    loading = false,
                    extendedStats = ExtendedStats.empty(),
                    allTransactions = emptyList(),
                    grpTrnsList = emptyTransactionList(),
                    filterVisible = false,
                    filterState = ReportFilterState.empty(),
                    templateVisible = false,
                    templateState = TemplateDataHolder.empty()
                )
        }
    }

    private fun reportTemplateFlow() =
        combine(
            templateListFlow(Unit),
            selectedTemplate(),
        ) { list, selectedTemplate ->
            TemplateDataHolder(
                templateList = list,
                selectedTemplate = selectedTemplate
            )
        }.flowOn(Dispatchers.Default)
            .onStart { emit(TemplateDataHolder.empty()) }

//    private fun selectedTemplate2() = combine(
//        validFilterOptionsFlow(), templateName
//    ) { state, name ->
//        state?.toTemplate(templateName = name)
//    }.onEmpty { emit(null) }
//        .onStart { emit(null) }

    private fun selectedTemplate() =
        reportFilterStateFlow()
            .takeWhile { f ->
                //Log.d("GGGG", ""+f.isFilterValid())
                f.isFilterValid()
            }
            .flatMapLatest { state ->
                //Log.d("GGGG", "here"+state.isFilterValid())
                templateName.map { name -> state.toTemplate(name) }
            }.onEmpty { emit(null) }


//    private fun validFilterOptionsFlow(): Flow<ReportFilterState?> =
//        reportFilterStateFlow().flatMapLatest { flowOf(if (it.isFilterValid()) it else null) }
//            .distinctUntilChanged()
}

//    private fun selectedTemplateUiState() =
//        selectedTemplate().flatMapLatest {
//            flowOf(it?.toUiState())
//        }.onEmpty { emit(null) }.onStart { emit(null) }


//    private fun saveTemplate(): TemplateDataHolder {
//        val timePeriod = period.value
//        timePeriod ?: return TemplateDataHolder.empty()
//
//        val today = timeNowLocal()
//
//        val reportPeriodRule = when {
//            timePeriod.month != null && timePeriod.year != null &&
//                    today.month.value == timePeriod.month!!.monthValue && timePeriod.year == today.year -> ReportPeriodRule.CurrentMonth
//
//            timePeriod.lastNRange != null -> ReportPeriodRule.LastN(
//                lastN = timePeriod.lastNRange!!.periodN,
//                intervalType = timePeriod.lastNRange!!.periodType
//            )
//
//            timePeriod.fromToRange?.from == null && timePeriod.fromToRange?.to != null &&
//                    (timePeriod.fromToRange?.to?.toLocalDate()?.isEqual(today.toLocalDate())
//                        ?: false) -> ReportPeriodRule.AllTime
//
//            else ->
//                ReportPeriodRule.Custom
//
//        }
//
//        val template = ReportTemplateEntity(
//            title = "",
//            selectedTrnTypes = selectedTrnTypes.value,
//            reportPeriodRule = reportPeriodRule,
//            selectedAccounts = selectedAccounts.value.filter { it.selected }.map { it.account.id },
//            selectedCategories = selectedCategories.value.filter { it.selected }.map {
//                when (it.selectableCategory) {
//                    is ReportCategoryType.Cat -> it.selectableCategory.cat.id
//                    else -> null
//                }
//            },
//            minAmount = minAmount.value,
//            maxAmount = maxAmount.value,
//
//            includeKeywords = includeKeywords.value,
//            excludeKeywords = excludeKeywords.value,
//
//            selectedPlannedPayments = selectedPlannedPayments.value,
//            transfersAsIncomeExpense = transfersAsIncomeExpense.value
//        )
//
//        val alternativeReportPeriodRule: ReportPeriodRule = when (reportPeriodRule) {
//            is ReportPeriodRule.Custom -> {
//                if (timePeriod.fromToRange?.from != null && timePeriod.fromToRange?.to != null) {
//                    val fromDate = timePeriod.fromToRange!!.from!!.toLocalDate()
//                    val toDate = timePeriod.fromToRange!!.to!!.toLocalDate()
//
//                    if (toDate.isBefore(today.toLocalDate()) || toDate.isEqual(today.toLocalDate())) {
//                        val days = ChronoUnit.DAYS.between(fromDate, toDate.plusDays(1))
//                        ReportPeriodRule.LastN(
//                            lastN = days.toInt(),
//                            intervalType = IntervalType.DAY
//                        )
//                    } else
//                        ReportPeriodRule.Custom
//                } else
//                    ReportPeriodRule.Custom
//            }
//
//            else -> reportPeriodRule
//        }
//
//        return TemplateDataHolder(
//            visible = true,
//            title = "",
//            alternativePeriod = alternativeReportPeriodRule,
//            template = template
//        )
//    }

//    private fun templateFlow() = templateSaveScreenVisible.flatMapLatest {
//        flowOf(it)
//            .flatMapLatest {
//                flowOf(saveTemplate())
//            }.catch { x ->
//                Log.d("GGGG", "" + x.message)
//            }
//            .flowOn(Dispatchers.Default)
//            .onStart { TemplateDataHolder.empty() }
//    }.flowOn(Dispatchers.Default)
//        .onStart { TemplateDataHolder.empty() }