package com.noobdev.waffle.Screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobdev.waffle.ui.components.*
import com.noobdev.waffle.ui.theme.*
import com.noobdev.waffle.viewmodel.AlertsViewModel
import com.noobdev.waffle.viewmodel.DisasterAlert

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(navController: NavController, viewModel: AlertsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    val filterOptions =
            listOf(
                    null to "All",
                    "flood" to "Floods",
                    "earthquake" to "Earthquakes",
                    "wildfire" to "Wildfires",
                    "storm" to "Storms"
            )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // === HEADER ===
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                            text = "Disaster Alerts",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                            text = "${uiState.alerts.size} active alerts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // === FILTER CHIPS ===
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filterOptions) { (type, label) ->
                            FilterChip(
                                    selected = uiState.filterType == type,
                                    onClick = { viewModel.setFilter(type) },
                                    label = { Text(label) },
                                    leadingIcon =
                                            if (uiState.filterType == type) {
                                                {
                                                    Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else null,
                                    colors =
                                            FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor =
                                                            MaterialTheme.colorScheme.primary.copy(
                                                                    alpha = 0.15f
                                                            ),
                                                    selectedLabelColor =
                                                            MaterialTheme.colorScheme.primary
                                            )
                            )
                        }
                    }
                }
            }

            // === ALERTS LIST ===
            PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        viewModel.refresh()
                        isRefreshing = false
                    },
                    modifier = Modifier.fillMaxSize()
            ) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        WaffleLoadingSpinner()
                    }
                } else {
                    val filteredAlerts = viewModel.getFilteredAlerts()

                    if (filteredAlerts.isEmpty()) {
                        EmptyState(
                                filter =
                                        filterOptions
                                                .find { it.first == uiState.filterType }
                                                ?.second
                        )
                    } else {
                        LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredAlerts) { alert ->
                                AlertDetailCard(
                                        alert = alert,
                                        onClick = { /* Navigate to detail */}
                                )
                            }

                            // Bottom padding for nav bar
                            item { Spacer(modifier = Modifier.height(60.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertDetailCard(alert: DisasterAlert, onClick: () -> Unit) {
    val borderColor =
            when (alert.status ?: StatusLevel.MONITORING) {
                StatusLevel.CRITICAL -> DangerRed
                StatusLevel.WARNING -> WarningOrange
                StatusLevel.MONITORING -> InfoBlue
                StatusLevel.SAFE -> SafeGreen
            }

    Card(
            modifier = Modifier.fillMaxWidth().clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Status indicator bar
            Box(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(borderColor)
            )

            Spacer(modifier = Modifier.height(12.dp))

              Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
              ) {
                  DisasterTypeBadge(type = alert.type ?: "alert")
                  StatusBadge(status = alert.status ?: StatusLevel.MONITORING)
              }

              Spacer(modifier = Modifier.height(12.dp))

              Text(
                      text = alert.title ?: "System Alert",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                                        val location = alert.location ?: "Unknown Location"
                                        Text(
                                                text = location,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details section
            val safeDetails = alert.details ?: emptyMap()
            if (safeDetails.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                safeDetails.forEach { (key, value) ->
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                                text = key,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = alert.timestamp ?: "Just now",
                        style = WaffleTextStyles.timestamp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(onClick = onClick) {
                    Text("View Details")
                    Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(filter: String?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = SafeGreen
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                    text = "All Clear",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                    text =
                            if (filter != null) "No $filter alerts at this time"
                            else "No active alerts in your area",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
