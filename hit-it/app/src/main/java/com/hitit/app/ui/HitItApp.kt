package com.hitit.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hitit.app.ui.navigation.Dest
import com.hitit.app.ui.screens.grid.GridScreen
import com.hitit.app.ui.screens.hits.HitEditScreen
import com.hitit.app.ui.screens.hits.HitsScreen
import com.hitit.app.ui.screens.profile.ProfileScreen
import com.hitit.app.ui.screens.reps.RepDetailScreen
import com.hitit.app.ui.screens.reps.RepEditScreen
import com.hitit.app.ui.screens.reps.RepsScreen
import com.hitit.app.ui.screens.today.TodayScreen

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(Dest.TODAY, "Today", Icons.Filled.CalendarToday),
    BottomItem(Dest.REPS, "Reps", Icons.Filled.Repeat),
    BottomItem(Dest.HITS, "Hits", Icons.Filled.CheckCircle),
    BottomItem(Dest.GRID, "Grid", Icons.Filled.GridView),
    BottomItem(Dest.PROFILE, "Profile", Icons.Filled.EmojiEvents),
)

@Composable
fun HitItApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination?.route in bottomItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Dest.TODAY,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Dest.TODAY) {
                TodayScreen(
                    onAddRep = { navController.navigate(Dest.repEdit()) },
                    onOpenRep = { navController.navigate(Dest.repDetail(it)) },
                )
            }
            composable(Dest.REPS) {
                RepsScreen(
                    onAddRep = { navController.navigate(Dest.repEdit()) },
                    onOpenRep = { navController.navigate(Dest.repDetail(it)) },
                )
            }
            composable(Dest.HITS) {
                HitsScreen(
                    onAddHit = { navController.navigate(Dest.hitEdit()) },
                    onOpenHit = { navController.navigate(Dest.hitEdit(it)) },
                )
            }
            composable(Dest.GRID) { GridScreen() }
            composable(Dest.PROFILE) { ProfileScreen() }
            composable(
                route = Dest.REP_DETAIL,
                arguments = listOf(navArgument(Dest.ARG_REP_ID) { type = NavType.LongType }),
            ) {
                RepDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Dest.repEdit(it)) },
                )
            }
            composable(
                route = Dest.REP_EDIT,
                arguments = listOf(
                    navArgument(Dest.ARG_REP_ID) {
                        type = NavType.LongType
                        defaultValue = Dest.NEW_REP_ID
                    },
                ),
            ) {
                RepEditScreen(onDone = { navController.popBackStack() })
            }
            composable(
                route = Dest.HIT_EDIT,
                arguments = listOf(
                    navArgument(Dest.ARG_TASK_ID) {
                        type = NavType.LongType
                        defaultValue = Dest.NEW_TASK_ID
                    },
                ),
            ) {
                HitEditScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
