package com.pczumbel.navigator.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pczumbel.navigator.data.CaveRepository
import com.pczumbel.navigator.tiles.TilePackRepository

object Routes {
    const val MAP = "map"
    const val PACKS = "packs"
}

@Composable
fun NavRoot() {
    val context = LocalContext.current
    val navController = rememberNavController()

    // One repository for the whole app; Phase 2 replaces this with a proper container
    // once Room and the cave catalog arrive.
    val repo = remember { TilePackRepository(context) }
    val caveRepo = remember { CaveRepository(context) }
    LaunchedEffect(Unit) { repo.load() }

    NavHost(navController = navController, startDestination = Routes.MAP) {
        composable(Routes.MAP) {
            MapScreen(
                repo = repo,
                caveRepo = caveRepo,
                onOpenPacks = { navController.navigate(Routes.PACKS) },
            )
        }
        composable(Routes.PACKS) {
            TilePacksScreen(
                repo = repo,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
