package com.noobdev.waffle

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.noobdev.waffle.Screens.Home

/*
* Here is the main navigation graph for the entire ui stack
* steps
* 1-Whatever that u are trynna navigate to add it in the sealed class
* 2-Then declare it in the navigation function pay attention to context
* 3-Use it in ur composable's navigate tro and fro */

/*Todo :- Add all the relevant routing i.e clicking what leads to what*/

sealed class Screen(val route:String){
    object Home : Screen("home")
    object Map : Screen("map")
    object Settings : Screen("settings")
    object Rescue : Screen("rescue")
}
@Composable
fun Navigation(navController: NavHostController) {
    /*
    * Here we will declare the nav graph for the entire app
    * */
    NavHost( navController = navController,
        startDestination = Screen.Home.route){
     composable (Screen.Home.route){
            Home(navController)
     }
    }
}