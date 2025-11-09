package com.ammu.social.ui

import android.util.Log // Added import
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ammu.social.Screen
import com.ammu.social.SharedViewModel
import com.ammu.social.utils.TokenManager

@Composable
fun PageSelectionScreen(
    navController: NavController, 
    sharedViewModel: SharedViewModel, 
    onLoginClick: () -> Unit,
    onPageSelected: () -> Unit
) {
    val context = LocalContext.current
    val pages = sharedViewModel.pages

    if (pages == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = onLoginClick) {
                Text("Login with Facebook")
            }
        }
    } else if (pages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No Facebook pages found for this account.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(pages) { page ->
                ListItem(
                    headlineContent = { Text(page.name) },
                    modifier = Modifier.clickable {
                        Log.d("PageSelectionScreen", "Page clicked: ${page.name} (ID: ${page.id})")
                        Log.d("PageSelectionScreen", "Attempting to save page data for page: ${page.name} (ID: ${page.id})")
                        TokenManager.savePageData(context, page.id, page.accessToken)
                        onPageSelected()
                        navController.navigate(Screen.Inbox.route) {
                            popUpTo(Screen.PageSelection.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
