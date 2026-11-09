package com.pulse.bluetoothdisable.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.ui.tour.MainTourTarget
import com.pulse.bluetoothdisable.updates.UpdateIcon

internal enum class BottomDestination {
    THEME, LANGUAGE, ABOUT, COVER_MODE, UPDATES
}

/**
 * Compact icon-only five-item capsule; names stay available to screen readers.
 * Actions are owned
 * by MainScreen; this component intentionally does not create another drawer.
 */
@Composable
internal fun FloatingBottomNavigation(
    selected: BottomDestination?,
    enabled: Boolean,
    onSelect: (BottomDestination) -> Unit,
    targetModifier: (MainTourTarget) -> Modifier,
    modifier: Modifier = Modifier,
) {
    val destinations = BottomDestination.entries
    Surface(
        modifier = modifier.fillMaxWidth().height(64.dp).testTag("floating_bottom_navigation"),
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF242426),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                val active = selected == destination
                val itemColor by animateColorAsState(
                    targetValue = if (active) Color(0xFF64B5FF) else Color.White,
                    label = "bottomNavItemColor",
                )
                val haloColor by animateColorAsState(
                    targetValue = if (active) Color(0xFF406B9D).copy(alpha = 0.4f) else Color.Transparent,
                    label = "bottomNavHaloColor",
                )
                val target = when (destination) {
                    BottomDestination.THEME -> MainTourTarget.THEME
                    BottomDestination.LANGUAGE -> MainTourTarget.LANGUAGE
                    BottomDestination.ABOUT -> MainTourTarget.ABOUT
                    BottomDestination.COVER_MODE -> MainTourTarget.COVER_MODE
                    BottomDestination.UPDATES -> MainTourTarget.UPDATES
                }
                val label = when (destination) {
                    BottomDestination.THEME -> stringResource(R.string.navigation_theme)
                    BottomDestination.LANGUAGE -> stringResource(R.string.navigation_language)
                    BottomDestination.ABOUT -> stringResource(R.string.about_app_title)
                    BottomDestination.COVER_MODE -> stringResource(R.string.navigation_cover_mode)
                    BottomDestination.UPDATES -> stringResource(R.string.navigation_updates)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(targetModifier(target))
                        .testTag(if (destination == BottomDestination.UPDATES) "check_updates" else "bottom_nav_${destination.name.lowercase()}")
                        .clip(RoundedCornerShape(30.dp))
                        .clickable(enabled = enabled, role = Role.Tab, onClick = { onSelect(destination) })
                        .semantics {
                            this.selected = active
                            contentDescription = label
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(haloColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (destination) {
                            BottomDestination.THEME -> Icon(Icons.Default.Palette, null, Modifier.size(28.dp), tint = itemColor)
                            BottomDestination.LANGUAGE -> Icon(Icons.Default.Language, null, Modifier.size(28.dp), tint = itemColor)
                            BottomDestination.ABOUT -> Icon(Icons.Default.Info, null, Modifier.size(28.dp), tint = itemColor)
                            BottomDestination.COVER_MODE -> Image(
                                painter = painterResource(R.drawable.cover_mode_nav_icon),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                colorFilter = ColorFilter.tint(itemColor),
                            )
                            BottomDestination.UPDATES -> Icon(UpdateIcon, null, Modifier.size(28.dp), tint = itemColor)
                        }
                    }
                }
            }
        }
    }
}
