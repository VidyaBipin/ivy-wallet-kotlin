package com.ivy.contributors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.navigation.Navigation
import com.ivy.navigation.navigation
import kotlinx.collections.immutable.persistentListOf

@Composable
fun ContributorsScreenImpl() {
    val viewModel: ContributorsViewModel = viewModel()
    val uiState = viewModel.uiState()

    ContributorsUi(
        uiState = uiState,
        onEvent = {
            viewModel.onEvent(it)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContributorsUi(
    uiState: ContributorsState,
    onEvent: (ContributorsEvent) -> Unit
) {
    val nav = navigation()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TopAppBarTitle(text = "Contributors")
                },
                navigationIcon = {
                    BackButton(nav = nav)
                }
            )
        },
        content = {
            ContributorsContent(
                paddingValues = it,
                contributorsStage = uiState.contributors,
                onEvent = { contributorsEvent ->
                    onEvent(contributorsEvent)
                }
            )
        }
    )
}

@Composable
private fun TopAppBarTitle(text: String) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = UI.typo.h2.style(
            fontWeight = FontWeight.Black
        )
    )
}

@Composable
private fun BackButton(nav: Navigation) {
    IconButton(onClick = {
        nav.back()
    }) {
        Icon(
            imageVector = Icons.Filled.ArrowBack,
            contentDescription = "Back"
        )
    }
}

@Composable
private fun ContributorsContent(
    paddingValues: PaddingValues,
    contributorsStage: ContributorsStage,
    onEvent: (ContributorsEvent) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (contributorsStage) {
            is ContributorsStage.Error -> item(key = "Error") {
                ErrorStage(message = contributorsStage.errorMessage) {
                    onEvent(ContributorsEvent.TryAgainButtonClicked)
                }
            }

            ContributorsStage.Loading -> TODO()
            is ContributorsStage.Success -> TODO()
        }
    }
}

@Composable
fun LazyItemScope.ErrorStage(
    message: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(text = message)
    IconButton(
        onClick = onClick
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Try again"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContributorCard(contributor: Contributor) {
    val browser = LocalUriHandler.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        onClick = {
            browser.openUri(contributor.link)
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                modifier = Modifier.size(72.dp),
                model = contributor.photo,
                contentDescription = null
            )

            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = contributor.name
                )
                Text(
                    text = "${contributor.contributions} contributions"
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewSuccess() {
    IvyWalletPreview {
        ContributorsUi(
            uiState = ContributorsState(
                contributors = ContributorsStage.Success(
                    persistentListOf(
                        Contributor(
                            name = "Iliyan",
                            photo = "",
                            contributions = "564",
                            link = ""
                        )
                    )
                )
            ),
            onEvent = {}
        )
    }
}

@Preview
@Composable
private fun PreviewError() {
    IvyWalletPreview {
        ContributorsUi(
            uiState = ContributorsState(
                contributors = ContributorsStage.Error(
                    errorMessage = "Error. Try again."
                )
            ),
            onEvent = {}
        )
    }
}

@Preview
@Composable
private fun PreviewLoading() {
    IvyWalletPreview {
        ContributorsUi(
            uiState = ContributorsState(
                contributors = ContributorsStage.Loading
            ),
            onEvent = {}
        )
    }
}