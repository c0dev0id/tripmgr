package com.tripmgr

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.tripmgr.data.storage.StorageService
import com.tripmgr.ui.navigation.TripNavGraph
import com.tripmgr.ui.theme.TripMgrTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var storageService: StorageService

    private var isStorageReady = mutableStateOf(false)

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            storageService.setStorageRoot(it)
            isStorageReady.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isStorageReady.value = storageService.hasStorageRoot()

        setContent {
            TripMgrTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val ready by isStorageReady

                    if (ready) {
                        val navController = rememberNavController()
                        TripNavGraph(navController = navController)
                    } else {
                        WelcomeScreen(
                            onChooseFolder = {
                                folderPickerLauncher.launch(null)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(
    onChooseFolder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Trip Manager",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Organize your trips",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))

        Button(onClick = onChooseFolder) {
            Text("Choose storage folder")
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Select a folder to store your trips.\nYou can use local storage or Google Drive.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
