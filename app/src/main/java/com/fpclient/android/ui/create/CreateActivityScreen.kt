package com.fpclient.android.ui.create

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fpclient.android.AppContainer
import com.fpclient.android.ui.AppViewModel

private val MODES = listOf("Upload file", "Manual entry")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateActivityScreen(
    container: AppContainer,
    appViewModel: AppViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val vm: CreateViewModel = viewModel(factory = CreateViewModel.factory(container))
    val ui by vm.ui.collectAsState()
    var mode by rememberSaveable { mutableIntStateOf(0) }

    // Navigate back from a side effect, never directly during composition.
    LaunchedEffect(ui.done) {
        if (ui.done) onDone()
    }
    if (ui.done) return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New activity") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                MODES.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = mode == index,
                        onClick = { mode = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = MODES.size),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (mode == 0) UploadForm(ui = ui, vm = vm) else ManualForm(vm = vm)
        }
    }
}
