package com.ivy.loans

import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.ivy.loans.loandetails.LoanDetailScreenUiTest
import com.ivy.ui.testing.PaparazziScreenshotTest
import com.ivy.ui.testing.PaparazziTheme
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class LoanDetailScreenPaparazziTest(
    @TestParameter
    private val theme: PaparazziTheme,
) : PaparazziScreenshotTest() {
    @Test
    fun `snapshot Loan Detail Screen`() {
        snapshot(theme) {
            LoanDetailScreenUiTest(theme == PaparazziTheme.Dark)
        }
    }
}