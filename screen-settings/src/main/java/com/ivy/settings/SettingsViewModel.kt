package com.ivy.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ivy.core.ComposeViewModel
import com.ivy.core.RootScreen
import com.ivy.core.datamodel.legacy.Theme
import com.ivy.core.db.read.SettingsDao
import com.ivy.core.db.write.SettingsWriter
import com.ivy.core.util.refreshWidget
import com.ivy.frp.monad.Res
import com.ivy.frp.test.TestIdlingResource
import com.ivy.legacy.IvyWalletCtx
import com.ivy.legacy.LogoutLogic
import com.ivy.legacy.data.SharedPrefs
import com.ivy.legacy.domain.action.exchange.SyncExchangeRatesAct
import com.ivy.legacy.domain.action.settings.UpdateSettingsAct
import com.ivy.legacy.domain.deprecated.logic.zip.BackupLogic
import com.ivy.legacy.utils.asLiveData
import com.ivy.legacy.utils.formatNicelyWithTime
import com.ivy.legacy.utils.ioThread
import com.ivy.legacy.utils.sendToCrashlytics
import com.ivy.legacy.utils.timeNowUTC
import com.ivy.legacy.utils.uiThread
import com.ivy.wallet.domain.action.global.StartDayOfMonthAct
import com.ivy.wallet.domain.action.global.UpdateStartDayOfMonthAct
import com.ivy.wallet.domain.action.settings.SettingsAct
import com.ivy.wallet.domain.deprecated.logic.csv.ExportCSVLogic
import com.ivy.widget.balance.WalletBalanceWidgetReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDao: SettingsDao,
    private val ivyContext: IvyWalletCtx,
    private val exportCSVLogic: ExportCSVLogic,
    private val logoutLogic: LogoutLogic,
    private val sharedPrefs: SharedPrefs,
    private val backupLogic: BackupLogic,
    private val startDayOfMonthAct: StartDayOfMonthAct,
    private val updateStartDayOfMonthAct: UpdateStartDayOfMonthAct,
    private val syncExchangeRatesAct: SyncExchangeRatesAct,
    private val settingsAct: SettingsAct,
    private val updateSettingsAct: UpdateSettingsAct,
    private val settingsWriter: SettingsWriter,
) : ComposeViewModel<SettingsState, SettingsEvent>() {

    @Composable
    override fun uiState(): SettingsState {
        TODO("Not yet implemented")
    }

    private val _nameLocalAccount = MutableLiveData<String?>()
    val nameLocalAccount = _nameLocalAccount.asLiveData()

    private val _currencyCode = MutableLiveData<String>()
    val currencyCode = _currencyCode.asLiveData()

    private val _currentTheme = MutableLiveData<Theme>()
    val currentTheme = _currentTheme.asLiveData()

    private val _lockApp = MutableLiveData<Boolean>()
    val lockApp = _lockApp.asLiveData()

    private val _hideCurrentBalance = MutableStateFlow(false)
    val hideCurrentBalance = _hideCurrentBalance.asStateFlow()

    private val _showNotifications = MutableStateFlow(true)
    val showNotifications = _showNotifications.asStateFlow()

    private val _treatTransfersAsIncomeExpense = MutableStateFlow(false)
    val treatTransfersAsIncomeExpense = _treatTransfersAsIncomeExpense.asStateFlow()

    private val _progressState = MutableStateFlow(false)
    val progressState = _progressState.asStateFlow()

    private val _startDateOfMonth = MutableLiveData<Int>()
    val startDateOfMonth = _startDateOfMonth

    fun start() {
        viewModelScope.launch {
            TestIdlingResource.increment()

            val settings = ioThread {
                settingsDao.findFirst()
            }

            _nameLocalAccount.value = settings.name

            _startDateOfMonth.value = startDayOfMonthAct(Unit)!!

            _currencyCode.value = settings.currency

            _currentTheme.value = settingsAct(Unit).theme

            _lockApp.value = sharedPrefs.getBoolean(SharedPrefs.APP_LOCK_ENABLED, false)
            _hideCurrentBalance.value =
                sharedPrefs.getBoolean(SharedPrefs.HIDE_CURRENT_BALANCE, false)

            _showNotifications.value = sharedPrefs.getBoolean(SharedPrefs.SHOW_NOTIFICATIONS, true)

            _treatTransfersAsIncomeExpense.value =
                sharedPrefs.getBoolean(SharedPrefs.TRANSFERS_AS_INCOME_EXPENSE, false)

            TestIdlingResource.decrement()
        }
    }

    fun exportToCSV(context: Context) {
        ivyContext.createNewFile(
            "Ivy Wallet (${
                timeNowUTC().formatNicelyWithTime(noWeekDay = true)
            }).csv"
        ) { fileUri ->
            viewModelScope.launch {
                TestIdlingResource.increment()

                exportCSVLogic.exportToFile(
                    context = context,
                    fileUri = fileUri
                )

                (context as RootScreen).shareCSVFile(
                    fileUri = fileUri
                )

                TestIdlingResource.decrement()
            }
        }
    }

    fun exportToZip(context: Context) {
        ivyContext.createNewFile(
            "Ivy Wallet (${
                timeNowUTC().formatNicelyWithTime(noWeekDay = true)
            }).zip"
        ) { fileUri ->
            viewModelScope.launch(Dispatchers.IO) {
                TestIdlingResource.increment()

                _progressState.value = true
                backupLogic.exportToFile(zipFileUri = fileUri)
                _progressState.value = false

                sharedPrefs.putBoolean(SharedPrefs.DATA_BACKUP_COMPLETED, true)
                ivyContext.dataBackupCompleted = true

                uiThread {
                    (context as RootScreen).shareZipFile(
                        fileUri = fileUri
                    )
                }

                TestIdlingResource.decrement()
            }
        }
    }

    fun login() {
        ivyContext.googleSignIn { idToken ->
            if (idToken != null) {
                viewModelScope.launch {
                    TestIdlingResource.increment()

                    try {
                    } catch (e: Exception) {
                        e.sendToCrashlytics(
                            "Settings - GOOGLE_SIGN_IN ERROR: generic exception when logging with GOOGLE"
                        )
                        e.printStackTrace()
                        Timber.e("Settings - Login with Google failed on Ivy server - ${e.message}")
                    }

                    TestIdlingResource.decrement()
                }
            } else {
                sendToCrashlytics("Settings - GOOGLE_SIGN_IN ERROR: idToken is null!!")
                Timber.e("Settings - Login with Google failed while getting idToken")
            }
        }
    }

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SetCurrency -> setCurrency(event.newCurrency)
            is SettingsEvent.SetName -> setName(event.newName)
            SettingsEvent.SwitchTheme -> switchTheme()
            is SettingsEvent.SetLockApp -> setLockApp(event.lockApp)
            is SettingsEvent.SetShowNotifications -> setShowNotifications(event.showNotifications)
            is SettingsEvent.SetHideCurrentBalance -> setHideCurrentBalance(
                event.hideCurrentBalance
            )

            is SettingsEvent.SetTransfersAsIncomeExpense -> setTransfersAsIncomeExpense(
                event.treatTransfersAsIncomeExpense
            )

            is SettingsEvent.SetStartDateOfMonth -> setStartDateOfMonth(event.startDate)

            SettingsEvent.DeleteCloudUserData -> deleteCloudUserData()
            SettingsEvent.DeleteAllUserData -> deleteAllUserData()
        }
    }

    fun setCurrency(newCurrency: String) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            ioThread {
                settingsWriter.save(
                    settingsDao.findFirst().copy(
                        currency = newCurrency
                    )
                )

                syncExchangeRatesAct(
                    SyncExchangeRatesAct.Input(
                        baseCurrency = newCurrency
                    )
                )
            }
            start()

            TestIdlingResource.decrement()
        }
    }

    fun setName(newName: String) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            ioThread {
                settingsWriter.save(
                    settingsDao.findFirst().copy(
                        name = newName
                    )
                )
            }
            start()

            TestIdlingResource.decrement()
        }
    }

    private fun switchTheme() {
        viewModelScope.launch {
            val currentSettings = settingsAct(Unit)
            val newTheme = when (currentSettings.theme) {
                Theme.LIGHT -> Theme.DARK
                Theme.DARK -> Theme.AUTO
                Theme.AUTO -> Theme.LIGHT
            }
            updateSettingsAct(
                currentSettings.copy(
                    theme = newTheme
                )
            )
            ivyContext.switchTheme(newTheme)
            _currentTheme.value = newTheme
        }
    }

    private fun setLockApp(lockApp: Boolean) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            sharedPrefs.putBoolean(SharedPrefs.APP_LOCK_ENABLED, lockApp)
            _lockApp.value = lockApp
            refreshWidget(WalletBalanceWidgetReceiver::class.java)

            TestIdlingResource.decrement()
        }
    }

    private fun setShowNotifications(showNotifications: Boolean) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            sharedPrefs.putBoolean(SharedPrefs.SHOW_NOTIFICATIONS, showNotifications)
            _showNotifications.value = showNotifications

            TestIdlingResource.decrement()
        }
    }

    private fun setHideCurrentBalance(hideCurrentBalance: Boolean) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            sharedPrefs.putBoolean(SharedPrefs.HIDE_CURRENT_BALANCE, hideCurrentBalance)
            _hideCurrentBalance.value = hideCurrentBalance

            TestIdlingResource.decrement()
        }
    }

    private fun setTransfersAsIncomeExpense(treatTransfersAsIncomeExpense: Boolean) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            sharedPrefs.putBoolean(
                SharedPrefs.TRANSFERS_AS_INCOME_EXPENSE,
                treatTransfersAsIncomeExpense
            )
            _treatTransfersAsIncomeExpense.value = treatTransfersAsIncomeExpense

            TestIdlingResource.decrement()
        }
    }

    private fun setStartDateOfMonth(startDate: Int) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            when (val res = updateStartDayOfMonthAct(startDate)) {
                is Res.Err -> {}
                is Res.Ok -> {
                    _startDateOfMonth.value = res.data!!
                }
            }

            TestIdlingResource.decrement()
        }
    }

    private fun deleteCloudUserData() {
        viewModelScope.launch {
            cloudLogout()
        }
    }

    private fun cloudLogout() {
        viewModelScope.launch {
            TestIdlingResource.increment()

            logoutLogic.cloudLogout()

            TestIdlingResource.decrement()
        }
    }

    private fun deleteAllUserData() {
        viewModelScope.launch {
            logout()
        }
    }

    private fun logout() {
        viewModelScope.launch {
            TestIdlingResource.increment()

            logoutLogic.logout()

            TestIdlingResource.decrement()
        }
    }
}
