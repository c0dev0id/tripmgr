package com.tripmgr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.tripmgr.data.drive.DriveServiceWrapper
import com.tripmgr.data.drive.GoogleAuthHelper
import com.tripmgr.ui.navigation.TripNavGraph
import com.tripmgr.ui.theme.TripMgrTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authHelper: GoogleAuthHelper
    @Inject lateinit var driveServiceWrapper: DriveServiceWrapper

    private var isSignedIn = mutableStateOf(false)
    private var signInError = mutableStateOf<String?>(null)

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                driveServiceWrapper.driveService = authHelper.getDriveService(account)
                isSignedIn.value = true
                signInError.value = null
            }
        } catch (e: ApiException) {
            signInError.value = "Sign-in failed: ${e.statusCode} - ${e.message}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if already signed in
        val account = authHelper.getSignedInAccount()
        if (account != null) {
            driveServiceWrapper.driveService = authHelper.getDriveService(account)
            isSignedIn.value = true
        }

        setContent {
            TripMgrTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val signedIn by isSignedIn
                    val error by signInError

                    if (signedIn) {
                        val navController = rememberNavController()
                        TripNavGraph(navController = navController)
                    } else {
                        SignInScreen(
                            error = error,
                            onSignIn = {
                                signInLauncher.launch(authHelper.getSignInIntent())
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignInScreen(
    error: String?,
    onSignIn: () -> Unit
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
            "Organize your trips with Google Drive",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))

        Button(onClick = onSignIn) {
            Text("Sign in with Google")
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
