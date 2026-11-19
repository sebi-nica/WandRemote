package com.example.wandremote

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.wandremote.navigation.ModeDestination
import com.example.wandremote.navigation.Routes
import com.example.wandremote.ui.components.ConnectionStatusChip
import com.example.wandremote.ui.screens.calibration.CalibrationScreen
import com.example.wandremote.ui.screens.connect.ConnectScreen
import com.example.wandremote.ui.screens.debug.SensorDebugScreen
import com.example.wandremote.ui.screens.pointer.PointerScreen
import com.example.wandremote.ui.screens.presenter.PresenterScreen
import com.example.wandremote.ui.screens.settings.SettingsScreen
import com.example.wandremote.ui.screens.config.ConfigScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WandRemoteApp() {
    val navController = rememberNavController()
    val vm: WandViewModel = viewModel()
    val connectionManager = vm.connection
    val connection by connectionManager.state.collectAsState()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isMode = ModeDestination.entries.any { it.route == route }
    val showBars = route != null && route != Routes.CONNECT
    var menuOpen by remember { mutableStateOf(false) }

    val pairing by vm.pendingPairing.collectAsState()
    LaunchedEffect(pairing) {
        val info = pairing ?: return@LaunchedEffect
        vm.consumePairing()
        vm.connect("PC (${info.host})", info.host, info.udpPort)
        navController.navigate(Routes.POINTER) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun openSecondary(target: String) {
        menuOpen = false
        navController.navigate(target) { launchSingleTop = true }
    }

    fun disconnect() {
        menuOpen = false
        connectionManager.disconnect()
        navController.navigate(Routes.CONNECT) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    Scaffold(
        topBar = {
            if (showBars) {
                TopAppBar(
                    title = { Text(Routes.title(route)) },
                    navigationIcon = {
                        if (!isMode) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        ConnectionStatusChip(connection)
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Calibration") }, onClick = { openSecondary(Routes.CALIBRATION) })
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { openSecondary(Routes.SETTINGS) })
                            DropdownMenuItem(text = { Text("Sensor debug") }, onClick = { openSecondary(Routes.DEBUG) })
                            DropdownMenuItem(text = { Text("Disconnect") }, onClick = { disconnect() })
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (isMode) {
                NavigationBar {
                    ModeDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = route == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CONNECT,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.CONNECT) {
                ConnectScreen(vm = vm, onConnect = { name, host, port ->
                    vm.connect(name, host, port)
                    navController.navigate(Routes.POINTER) {
                        popUpTo(Routes.CONNECT) { inclusive = true }
                    }
                })
            }
            composable(Routes.POINTER) { PointerScreen(vm) }
            composable(Routes.CONFIG) { ConfigScreen() }
            composable(Routes.PRESENTER) { PresenterScreen() }
            composable(Routes.CALIBRATION) { CalibrationScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.DEBUG) { SensorDebugScreen(connection) }
        }
    }
}
