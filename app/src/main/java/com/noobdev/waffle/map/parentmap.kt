package com.noobdev.waffle.map

import android.R.attr.shape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.noobdev.waffle.api.LocationAwareMap
import com.noobdev.waffle.api.mapTilerStyles
import org.maplibre.android.maps.MapLibreMap

/*
* i am making a parent map screen here
* the plan is to add params to it and modify it
* ex for earthquake and other natural disasters
* --be creative with it don't modify the parent func just the params*/

@Composable
fun ParentMapScreen(mapConfiguration : (MapLibreMap) ->Unit = {}) {

    var currentStyleIndex by remember { mutableStateOf(1) }
    val styleCount = mapTilerStyles.size
    Box(
        modifier = Modifier
            .windowInsetsPadding(
                androidx.compose.foundation.layout.WindowInsets.safeDrawing
            )
            .background(Color(0xFFEFEFEF))
            .clip(RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier
        ) {
            LocationAwareMap(currentStyleIndex=currentStyleIndex, onMapReady = mapConfiguration)
        }
    }
    Column (modifier = Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing),verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.End){
        Spacer(Modifier.size(90.dp))
        SideBar() }
    Column(
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End,
        modifier = Modifier.fillMaxSize()
    ) {
        FloatingActionButton(
            onClick = {
                currentStyleIndex = (currentStyleIndex + 1) % styleCount
            }
        ) {
            Image(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Switch Map Style"
            )
        }
    }
}
@Preview
@Composable
fun SideBar() {
    var expanded by remember { mutableStateOf(false) }

    val panelHeight by animateDpAsState(
        targetValue = if (expanded) 700.dp else 280.dp,
        label = "panelAnim"
    )

    Row(
        modifier = Modifier.fillMaxHeight(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            modifier = Modifier
                .width(40.dp)
                .height(panelHeight)
                .clip(RoundedCornerShape(16.dp))
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            color = Color.LightGray,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = {}) {
                        Text("F", style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = {}) {
                        Text("E", style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = {}) {
                        Text("FF", style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = {}) {
                        Text("A", style = MaterialTheme.typography.headlineSmall)
                    }
                    AnimatedVisibility(visible = expanded) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(onClick = {}) {
                                Text("FB", style = MaterialTheme.typography.headlineSmall)
                            }
                            IconButton(onClick = {}) {
                                Text("R", style = MaterialTheme.typography.headlineSmall)
                            }
                            IconButton(onClick = {}) {
                                Text("H", style = MaterialTheme.typography.headlineSmall)
                            }
                            IconButton(onClick = {}) {
                                Text("S️", style = MaterialTheme.typography.headlineSmall)
                            }
                            IconButton(onClick = {}) {
                                Text("-", style = MaterialTheme.typography.headlineSmall)
                            }
                            IconButton(onClick = {}) {
                                Text("L", style = MaterialTheme.typography.headlineSmall)
                            }
                            IconButton(onClick = {}) {
                                Text("i", style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { expanded = !expanded }
                ) {
                    Icon(
                        imageVector = if (expanded)
                            Icons.Default.ExpandLess
                        else
                            Icons.Default.ExpandMore,
                        contentDescription = "Toggle Sidebar"
                    )
                }
            }
        }
    }
}