package com.ivy.planned.list

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.utils.navigationBarInset
import com.ivy.legacy.utils.toDensityDp
import com.ivy.ui.R
import com.ivy.ui.annotation.IvyPreviews
import com.ivy.wallet.ui.theme.components.ActionsRow
import com.ivy.wallet.ui.theme.components.CloseButton
import com.ivy.wallet.ui.theme.components.IvyOutlinedButton
import com.ivy.wallet.ui.theme.gradientCutBackgroundTop

@Composable
fun BoxWithConstraintsScope.PlannedPaymentsBottomBar(
    bottomInset: Dp = navigationBarInset().toDensityDp(),
    onClose: () -> Unit,
    onAdd: () -> Unit,
) {
    ActionsRow(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .gradientCutBackgroundTop(UI.colors.pure, LocalDensity.current)
            .padding(bottom = bottomInset)
            .padding(bottom = 24.dp)
    ) {
        Spacer(Modifier.width(20.dp))

        CloseButton {
            onClose()
        }

        Spacer(Modifier.weight(1f))

        IvyOutlinedButton(
            iconStart = R.drawable.ic_planned_payments,
            text = stringResource(R.string.add_payment),
            solidBackground = true
        ) {
            onAdd()
        }

        Spacer(Modifier.width(20.dp))
    }
}

@Suppress("UnusedPrivateMember")
@IvyPreviews
@Composable
private fun PreviewBottomBar() {
    IvyWalletPreview {
        PlannedPaymentsBottomBar(
            bottomInset = 16.dp,
            onAdd = {},
            onClose = {}
        )
    }
}
