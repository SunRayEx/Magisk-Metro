import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            // Home Screen
        }
        composable("module_detail") {
            // Module Detail Screen
        }
        composable("app_detail") {
            // App Detail Screen
        }
        composable("deny_list_detail") {
            // Deny List Detail Screen
        }
        composable("log_detail") {
            // Log Detail Screen
        }
        composable("contributor_detail") {
            // Contributor Detail Screen
        }
    }
}