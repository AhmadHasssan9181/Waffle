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
import com.noobdev.waffle.viewmodel.AlertSortOrder
import androidx.compose.foundation.lazy.rememberLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(navController: NavController, viewModel: AlertsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Infinite scroll detection
    val isScrollToEnd by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1

            // Load more when we are 2 items away from the end
            lastVisibleItemIndex > (totalItemsNumber - 2)
        }
    }

    LaunchedEffect(isScrollToEnd) {
        if (isScrollToEnd && !uiState.isLoading && !uiState.isPaginating && uiState.hasMore) {
            viewModel.loadPage()
        }
    }

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

                    Spacer(modifier = Modifier.height(12.dp))

                    // === SORT TOGGLE ===
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sort By: ${uiState.sortOrder.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort Options",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                AlertSortOrder.values().forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.displayName) },
                                        onClick = {
                                            viewModel.setSortOrder(order)
                                            showSortMenu = false
                                        },
                                        trailingIcon = {
                                            if (uiState.sortOrder == order) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    )
                                }
                            }
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
                                state = listState,
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

                            if (uiState.isPaginating) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
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
    val safeStatus = alert.status ?: StatusLevel.INFO
    val safeType = alert.type ?: "unknown"
    val safeTitle = alert.title ?: "Alert"
    val safeTimestamp = alert.timestamp ?: ""

    val borderColor =
            when (safeStatus) {
                StatusLevel.CRITICAL -> DangerRed
                StatusLevel.WARNING -> WarningOrange
                StatusLevel.INFO -> InfoBlue
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
                DisasterTypeBadge(type = safeType)
                StatusBadge(status = safeStatus)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                    text = safeTitle,
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
                                        val location = alert.locationName ?: "Unknown Location"
                                        Text(
                                                text = location,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details section
            if (alert.details?.isNotEmpty() == true) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                alert.details.forEach { (key, value) ->
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
                                text = value.toString(),
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
                        text = safeTimestamp,
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
