package app.portfolio.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

/** Короткая обёртка: строка или карточка, по которой можно нажать. */
fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
