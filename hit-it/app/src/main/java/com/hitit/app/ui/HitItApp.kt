package com.hitit.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hitit.app.ui.components.AuroraBackground
import com.hitit.app.ui.components.SpotlightOverlay
import com.hitit.app.ui.components.SpotlightStep
import com.hitit.app.ui.components.rememberSpotlightState
import com.hitit.app.ui.components.spotlightTarget
import com.hitit.app.ui.navigation.Dest
import com.hitit.app.ui.screens.bigplays.BigPlayDetailScreen
import com.hitit.app.ui.screens.bigplays.BigPlayEditScreen
import com.hitit.app.ui.screens.bigplays.BigPlaysScreen
import com.hitit.app.ui.screens.bodyflow.BodyFlowScreen
import com.hitit.app.ui.screens.moneyvibe.MoneyVibeScreen
import com.hitit.app.ui.screens.checkin.CheckInScreen
import com.hitit.app.ui.screens.coach.CoachScreen
import com.hitit.app.ui.screens.grid.GridScreen
import com.hitit.app.ui.screens.hits.HitEditScreen
import com.hitit.app.ui.screens.hits.HitsScreen
import com.hitit.app.ui.screens.locker.LockerScreen
import com.hitit.app.ui.screens.lockin.LockInScreen
import com.hitit.app.ui.screens.profile.ProfileScreen
import com.hitit.app.ui.screens.reps.RepDetailScreen
import com.hitit.app.ui.screens.reps.RepEditScreen
import com.hitit.app.ui.screens.reps.RepsScreen
import com.hitit.app.ui.screens.today.TodayScreen

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

// The five pillars of VibeOS. Reps + Hits stay one tap away from Home (rail "see all" links),
// keeping the bar at Material's 5-item sweet spot instead of a cramped 7.
private val bottomItems = listOf(
    BottomItem(Dest.TODAY, "Home", Icons.Filled.CalendarToday),
    BottomItem(Dest.BODYFLOW, "Body", Icons.Filled.FavoriteBorder),
    BottomItem(Dest.MONEYVIBE, "Money", Icons.Filled.Paid),
    BottomItem(Dest.GRID, "Grid", Icons.Filled.GridView),
    BottomItem(Dest.PROFILE, "Profile", Icons.Filled.EmojiEvents),
)

@Composable
fun HitItApp(
    showSpotlight: Boolean = false,
    onSpotlightFinished: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    // Reps/Hits left the bar but are still top-level lists — keep the bar visible there.
    val showBottomBar = currentRoute in bottomItems.map { it.route } + listOf(Dest.REPS, Dest.HITS)
    val spotlight = rememberSpotlightState()

    AuroraBackground(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color.White.copy(alpha = 0.78f), tonalElevation = 0.dp) {
                    bottomItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            modifier = if (item.route == Dest.GRID) {
                                Modifier.spotlightTarget("grid_tab", spotlight)
                            } else {
                                Modifier
                            },
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
                    onLockIn = { navController.navigate(Dest.LOCK_IN) },
                    onCheckIn = { navController.navigate(Dest.CHECK_IN) },
                    onSeeAllReps = { navController.navigate(Dest.REPS) },
                    onSeeAllHits = { navController.navigate(Dest.HITS) },
                )
            }
            composable(Dest.BODYFLOW) {
                BodyFlowScreen(onCheckIn = { navController.navigate(Dest.CHECK_IN) })
            }
            composable(Dest.MONEYVIBE) { MoneyVibeScreen() }
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
            composable(Dest.PROFILE) {
                ProfileScreen(
                    onOpenBigPlays = { navController.navigate(Dest.BIG_PLAYS) },
                    onOpenLocker = { navController.navigate(Dest.locker()) },
                    onOpenCoach = { navController.navigate(Dest.COACH) },
                )
            }
            composable(Dest.LOCK_IN) {
                LockInScreen(onBack = { navController.popBackStack() })
            }
            composable(Dest.CHECK_IN) {
                CheckInScreen(onBack = { navController.popBackStack() })
            }
            composable(Dest.COACH) {
                CoachScreen(onBack = { navController.popBackStack() })
            }
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
            composable(Dest.BIG_PLAYS) {
                BigPlaysScreen(
                    onBack = { navController.popBackStack() },
                    onAddPlay = { navController.navigate(Dest.bigPlayEdit()) },
                    onOpenPlay = { navController.navigate(Dest.bigPlayDetail(it)) },
                )
            }
            composable(
                route = Dest.BIG_PLAY_DETAIL,
                arguments = listOf(navArgument(Dest.ARG_PLAN_ID) { type = NavType.LongType }),
            ) {
                BigPlayDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Dest.bigPlayEdit(it)) },
                )
            }
            composable(
                route = Dest.BIG_PLAY_EDIT,
                arguments = listOf(
                    navArgument(Dest.ARG_PLAN_ID) {
                        type = NavType.LongType
                        defaultValue = Dest.NEW_PLAN_ID
                    },
                ),
            ) {
                BigPlayEditScreen(onDone = { navController.popBackStack() })
            }
            composable(
                route = Dest.LOCKER,
                arguments = listOf(
                    navArgument(Dest.ARG_NOTE_ID) {
                        type = NavType.LongType
                        defaultValue = Dest.ROOT_NOTE_ID
                    },
                ),
            ) {
                LockerScreen(
                    onOpenNote = { navController.navigate(Dest.locker(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }

        // First-run coachmarks, overlaid on the real (seeded) app. Only on the Home route.
        if (showSpotlight && currentRoute == Dest.TODAY) {
            SpotlightOverlay(
                state = spotlight,
                steps = ONBOARDING_STEPS,
                onFinish = onSpotlightFinished,
            )
        }
    }
    }
}

private val ONBOARDING_STEPS = listOf(
    SpotlightStep(
        targetKey = "intro",
        title = "Welcome to Hit it ⚡",
        body = "Your flame and Momentum sit up top. Every rep you log feeds them.",
    ),
    SpotlightStep(
        targetKey = "intro",
        title = "Hit your reps",
        body = "Tap a rep's circle to log it. Finish them all for a Perfect Day bonus.",
    ),
    SpotlightStep(
        targetKey = "grid_tab",
        title = "Watch your year fill in",
        body = "The Grid lights up every day you show up. Tap here anytime.",
    ),
)
