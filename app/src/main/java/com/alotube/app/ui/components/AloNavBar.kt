package com.alotube.app.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alotube.app.R
import com.alotube.app.ui.theme.AloColors
import com.alotube.app.ui.theme.accents

private data class NavItem(@StringRes val label: Int, @DrawableRes val icon: Int)

private val items = listOf(
    NavItem(R.string.nav_home, R.drawable.ic_nav_home),
    NavItem(R.string.nav_music, R.drawable.ic_nav_music),
    NavItem(R.string.nav_video_player, R.drawable.ic_nav_player),
    NavItem(R.string.nav_library, R.drawable.ic_nav_library),
    NavItem(R.string.nav_settings, R.drawable.ic_nav_settings)
)

/** Navegación inferior con íconos de trazo propios; la pestaña activa usa el acento dinámico. */
@Composable
fun AloNavBar(selected: Int, onSelect: (Int) -> Unit) {
    val accents = MaterialTheme.accents
    Column {
        HorizontalDivider(color = AloColors.Ln)
        NavigationBar(containerColor = AloColors.Bg, tonalElevation = 0.dp) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = selected == index,
                    onClick = { onSelect(index) },
                    icon = {
                        Icon(painterResource(item.icon), contentDescription = null)
                    },
                    label = { Text(stringResource(item.label)) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accents.hi,
                        selectedTextColor = accents.hi,
                        indicatorColor = accents.bg,
                        unselectedIconColor = AloColors.Mut,
                        unselectedTextColor = AloColors.Mut
                    )
                )
            }
        }
    }
}
