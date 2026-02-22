package com.tripmgr.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tripmgr.ui.tripdetail.TripDetailScreen
import com.tripmgr.ui.tripdetail.TripDetailViewModel
import com.tripmgr.ui.triplist.TripListScreen
import com.tripmgr.ui.triplist.TripListViewModel

object Routes {
    const val TRIP_LIST = "trip_list"
    const val TRIP_DETAIL = "trip_detail/{tripFolderId}"

    fun tripDetail(tripFolderId: String) = "trip_detail/$tripFolderId"
}

@Composable
fun TripNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.TRIP_LIST
    ) {
        composable(Routes.TRIP_LIST) {
            val viewModel: TripListViewModel = hiltViewModel()
            TripListScreen(
                viewModel = viewModel,
                onTripClick = { tripFolderId ->
                    navController.navigate(Routes.tripDetail(tripFolderId))
                }
            )
        }

        composable(
            route = Routes.TRIP_DETAIL,
            arguments = listOf(
                navArgument("tripFolderId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val tripFolderId = backStackEntry.arguments?.getString("tripFolderId") ?: return@composable
            val viewModel: TripDetailViewModel = hiltViewModel()
            TripDetailScreen(
                tripFolderId = tripFolderId,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
