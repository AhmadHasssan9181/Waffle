package com.noobdev.waffle.map

import android.R.attr.shape
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.noobdev.waffle.api.LocationAwareMap
import com.noobdev.waffle.api.mapTilerStyles

/*
* i am making a parent map screen here
* the plan is to add params to it and modify it
* ex for earthquake and other natural disasters
* --be creative with it don't modify the parent func just the params*/

@Composable
fun ParentMapScreen() {

    var currentStyleIndex by remember { mutableStateOf(1) }
    val styleCount = mapTilerStyles.size
    Box(
        modifier = Modifier
            .windowInsetsPadding(
                androidx.compose.foundation.layout.WindowInsets.safeDrawing
            )
            .background(Color(0xFFEFEFEF))
            .clip(RoundedCornerShape(26.dp))
    ) {
        Column(
            modifier = Modifier
        ) {
            LocationAwareMap(currentStyleIndex)

        }
    }
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
