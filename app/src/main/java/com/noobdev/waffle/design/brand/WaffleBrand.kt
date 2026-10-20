package com.noobdev.waffle.design.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noobdev.waffle.R

/**
 * The waffle-quadcopter mark. Source artwork lives in `branding/mark.svg`; the PNG is rendered
 * from it at 288px so it stays crisp up to 72dp on xxxhdpi screens.
 */
@Composable
fun WaffleMark(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Image(
            painter = painterResource(R.drawable.ic_waffle_mark),
            contentDescription = "Waffle",
            modifier = modifier.size(size)
    )
}

@Composable
fun WaffleWordmark(
        modifier: Modifier = Modifier,
        markSize: Dp = 34.dp,
        subtitle: String? = null
) {
    Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        WaffleMark(size = markSize)
        Column {
            Text(
                    text = "Waffle",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Horizontal lockup used on the permission screen and empty states. */
@Composable
fun WaffleLockup(modifier: Modifier = Modifier, markSize: Dp = 96.dp) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        WaffleMark(size = markSize)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                    text = "Waffle",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.width(6.dp))
            Text(
                    text = "." ,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
