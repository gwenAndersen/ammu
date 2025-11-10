package com.ammu.social

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ammu.social.models.ClassifiedComment
import com.ammu.social.models.FacebookPage
import com.ammu.social.ui.PageSelectionScreen
import com.ammu.social.ui.theme.NewAndroidProjectTheme
import com.ammu.social.utils.TokenManager
import com.facebook.*
import com.facebook.login.LoginBehavior
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import org.json.JSONException

class MainActivity : ComponentActivity() {

    private val callbackManager = CallbackManager.Factory.create()
    private lateinit var sharedViewModel: SharedViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "APP VERSION: 1.0 - Debugging logs enabled!") // Prominent log for version check
        sharedViewModel = ViewModelProvider(this).get(SharedViewModel::class.java)

        setContent {
            NewAndroidProjectTheme(dynamicColor = true) {
                MainScreen(callbackManager, sharedViewModel)
            }
        }
    }
}

sealed class Screen(val route: String, val label: String? = null, val icon: ImageVector? = null) {
    object Inbox : Screen("inbox", "Inbox", Icons.Default.Email)
    object Strategy : Screen("strategy", "Strategy", Icons.Default.Insights)
    object PageSelection : Screen("page_selection")
}

val items = listOf(
    Screen.Inbox,
    Screen.Strategy,
)

@Composable
fun MainScreen(callbackManager: CallbackManager, sharedViewModel: SharedViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val inboxViewModel: InboxViewModel = viewModel()

    var isLoggedIn by remember { mutableStateOf(false) } // Initialize to false, user must select a page

    val loginLauncher = rememberLauncherForActivityResult(
        contract = LoginManager.getInstance().createLogInActivityResultContract(callbackManager, null),
        onResult = { }
    )

    fun fetchPageList(userAccessToken: AccessToken) {
        Log.d("MainScreen", "fetchPageList called with userAccessToken: ${userAccessToken.token}")
        val request = GraphRequest.newMeRequest(userAccessToken) { _, response ->
            Log.d("MainScreen", "GraphRequest response: ${response?.rawResponse}")
            try {
                val pages = mutableListOf<FacebookPage>()
                val jsonObject = response?.jsonObject
                Log.d("MainScreen", "Response jsonObject: $jsonObject")
                val accountsObject = jsonObject?.optJSONObject("accounts") // Get the accounts JSONObject
                Log.d("MainScreen", "Accounts JSONObject: $accountsObject")
                val accountsArray = accountsObject?.optJSONArray("data") // Get the data JSONArray from accountsObject
                Log.d("MainScreen", "Accounts JSONArray (data): $accountsArray")

                if (accountsArray != null) {
                    Log.d("MainScreen", "Accounts data array is not null, processing pages.")
                    for (i in 0 until accountsArray.length()) {
                        val page = accountsArray.getJSONObject(i)
                        val pageId = page.getString("id")
                        val pageName = page.getString("name")
                        val pageAccessToken = page.getString("access_token")
                        Log.d("MainScreen", "Parsing page: id=$pageId, name=$pageName, accessToken=$pageAccessToken")
                        pages.add(
                            FacebookPage(
                                id = pageId,
                                name = pageName,
                                accessToken = pageAccessToken
                            )
                        )
                    }
                } else {
                    Log.d("MainScreen", "Accounts data array is null or empty, no pages to process.")
                }
                sharedViewModel.pages = pages
                Log.d("MainScreen", "Fetched pages: ${pages.size}")
                if (pages.isNotEmpty()) {
                    Log.d("MainScreen", "Navigating to PageSelection screen.")
                    navController.navigate(Screen.PageSelection.route) {
                        popUpTo(Screen.PageSelection.route) { inclusive = true }
                    }
                } else {
                    Log.d("MainScreen", "No pages found, staying on PageSelectionScreen.")
                    // Stay on PageSelectionScreen if no pages are found, which will display the "No Facebook pages found" message.
                    // Or navigate to a dedicated "No Pages" screen if one exists. For now, staying here is sufficient.
                    navController.navigate(Screen.PageSelection.route)
                }

            } catch (e: JSONException) {
                Log.e("MainScreen", "Error parsing page accounts response", e)
            }
        }
        val parameters = Bundle()
        parameters.putString("fields", "accounts{name,access_token}")
        request.parameters = parameters
        request.executeAsync()
    }

    LaunchedEffect(Unit) {
        LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                fetchPageList(result.accessToken)
            }
            override fun onCancel() { Log.w("MainScreen", "Facebook Login cancelled.") }
            override fun onError(error: FacebookException) { Log.e("MainScreen", "Facebook Login error.", error) }
        })
    }

    Scaffold(
        bottomBar = {
            if (isLoggedIn) { // Only show bottom bar if logged in
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon!!, contentDescription = null) },
                            label = { Text(screen.label!!) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController, 
            startDestination = Screen.PageSelection.route, // Always start at page selection 
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Inbox.route) { 
                InboxScreen(
                    inboxViewModel = inboxViewModel,
                    onLogout = { 
                        LoginManager.getInstance().logOut()
                        TokenManager.clearToken(context)
                        isLoggedIn = false
                        navController.navigate(Screen.PageSelection.route) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Strategy.route) { StrategyScreen() }
            composable(Screen.PageSelection.route) {
                PageSelectionScreen(
                    navController = navController,
                    sharedViewModel = sharedViewModel,
                    inboxViewModel = inboxViewModel,
                    onLoginClick = {
                        LoginManager.getInstance().setLoginBehavior(LoginBehavior.WEB_ONLY)
                        loginLauncher.launch(listOf("pages_read_engagement", "pages_show_list", "pages_manage_posts"))
                    },
                    onPageSelected = { isLoggedIn = true }
                )
            }
        }
    }
}

@Composable
fun InboxScreen(inboxViewModel: InboxViewModel, onLogout: () -> Unit) {
    val context = LocalContext.current
    val pageId = TokenManager.getPageId(context)
    val pageToken = TokenManager.getPageToken(context)

    Log.d("InboxScreen", "InboxScreen: pageId = $pageId, pageToken = ${pageToken?.take(10)}...") // Log pageId and a snippet of pageToken

    LaunchedEffect(pageId, pageToken) {
        if (pageId != null && pageToken != null) {
            Log.d("InboxScreen", "InboxScreen: Calling fetchAndAnalyzeComments with valid pageId and pageToken.")
            inboxViewModel.fetchAndAnalyzeComments(pageId, pageToken)
        } else {
            Log.w("InboxScreen", "InboxScreen: pageId or pageToken is null. Not calling fetchAndAnalyzeComments.")
        }
    }

    val paginatedComments by inboxViewModel.paginatedComments.collectAsState()
    if (paginatedComments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
            Text("Loading comments...", modifier = Modifier.padding(top = 60.dp))
        }
    } else {
        Column {
             Button(onClick = onLogout) { Text("Logout") }
            LazyColumn(
                modifier = Modifier.weight(1f), // Make LazyColumn fill available space
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
            ) {
                items(paginatedComments) { comment ->
                    val detailedErrorLogs by inboxViewModel.detailedErrorLogs.collectAsState()
                    CommentCard(
                        comment = comment,
                        detailedErrorLogs = detailedErrorLogs,
                        inboxViewModel = inboxViewModel,
                        pageToken = pageToken
                    )
                }
            }

            // Pagination Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { inboxViewModel.goToPreviousPage() },
                    enabled = inboxViewModel.getCurrentPageNumber() > 1
                ) {
                    Text("Previous")
                }
                Text(
                    text = "Page ${inboxViewModel.getCurrentPageNumber()} of ${inboxViewModel.getTotalPages()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { inboxViewModel.goToNextPage() },
                    enabled = inboxViewModel.getCurrentPageNumber() < inboxViewModel.getTotalPages()
                ) {
                    Text("Next")
                }
            }
        }
    }
}

@Composable
fun CommentCard(
    comment: ClassifiedComment,
    detailedErrorLogs: Map<String, String>,
    inboxViewModel: InboxViewModel,
    pageToken: String?
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showReplyBox by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf(comment.reply) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (comment.from.isNotEmpty()) {
                Text(
                    text = "From: ${comment.from}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Priority: ",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = comment.priority,
                    color = when (comment.priority) {
                        "High" -> Color(0xFFD32F2F) // Red
                        "Low" -> Color(0xFF388E3C) // Green
                        "Error" -> Color.Gray
                        else -> Color.Black
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (comment.reason.isNotEmpty() && comment.reason != "-") {
                Spacer(modifier = Modifier.height(4.dp))
                 Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "Reason: ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = comment.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    var debugInfo = """
                        Comment ID: ${comment.id}
                        Comment Text: ${comment.text}
                        From: ${comment.from}
                        Priority: ${comment.priority}
                        AI Reason: ${comment.reason}
                    """.trimIndent()

                    val detailedError = detailedErrorLogs[comment.id]
                    if (!detailedError.isNullOrEmpty()) {
                        debugInfo += "\n\n--- Detailed Error Log ---\n$detailedError"
                    }

                    clipboardManager.setText(AnnotatedString(debugInfo))
                    android.widget.Toast.makeText(context, "Debug info copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy Debug Info")
                }
            }
            if (comment.reply.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { showReplyBox = !showReplyBox }) {
                    Text(if (showReplyBox) "Cancel" else "Reply")
                }
            }

            if (showReplyBox) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Suggested Reply:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                TextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Edit reply...") }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = {
                    if (pageToken != null) {
                        inboxViewModel.sendReply(comment.id, replyText, pageToken)
                        android.widget.Toast.makeText(context, "Reply sent!", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Error: Page token not available.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    showReplyBox = false
                }) {
                    Text("Send")
                }
            }
        }
    }
}


@Composable
fun StrategyScreen() {
    Text(text = "Strategy Report Screen")
}
