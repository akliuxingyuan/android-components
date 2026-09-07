/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.menu.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.Role.Companion.Button
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import mozilla.components.compose.base.badge.BADGE_SIZE_SMALL
import mozilla.components.compose.base.badge.BadgedIcon
import mozilla.components.compose.base.badge.StatusBadge
import mozilla.components.compose.base.button.IconButton
import mozilla.components.compose.base.text.Text
import mozilla.components.compose.base.text.value
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.compose.base.theme.information
import mozilla.components.compose.menu.R
import mozilla.components.compose.menu.ui.MenuItemState.ACTIVE
import mozilla.components.compose.menu.ui.MenuItemState.DEFAULT
import mozilla.components.compose.menu.ui.MenuItemState.DISABLED
import mozilla.components.compose.menu.ui.MenuItemState.WARNING
import mozilla.components.ui.icons.R as iconsR

/**
 * A menu item shown as a row in a vertical list.
 *
 * @param title The title of the menu item.
 * @param contentDescription The content description of the menu item.
 * @param modifier The modifier to apply to the menu item.
 * @param role The [Role] of the menu item.
 * @param summary An optional summary of the menu item.
 * @param icon An optional icon of the menu item.
 * @param showNewIndicator Whether to show a new indicator.
 * @param badge An optional badge to show.
 * @param actionButton An optional action button to show.
 * @param state The state of this menu item.
 */
@Composable
internal fun MenuListItem(
    title: Text,
    contentDescription: Text,
    modifier: Modifier = Modifier,
    role: Role = Button,
    summary: MenuListItemSummary? = null,
    icon: MenuItemIcon? = null,
    showNewIndicator: Boolean = false,
    badge: MenuListItemBadge? = null,
    actionButton: MenuListItemActionButton? = null,
    state: MenuItemState = DEFAULT,
) {
    Row(
        modifier =
            modifier
                .clip(shape = MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceBright)
                .minimumInteractiveComponentSize()
                .height(IntrinsicSize.Min),
        verticalAlignment = CenterVertically,
    ) {
        val contentDescriptionValue = contentDescription.value
        Row(
            modifier =
                Modifier.weight(1f)
                    .padding(
                        horizontal = AcornTheme.layout.space.static200,
                        vertical = AcornTheme.layout.space.static100,
                    )
                    .semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescriptionValue
                        this.role = role
                    },
            verticalAlignment = CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AcornTheme.layout.space.static200),
        ) {
            MenuListItemIcon(icon, state)

            MenuListItemText(title, state, summary)

            MenuListItemNewIndicator(showNewIndicator, state)

            MenuListItemBadge(badge, state)
        }

        MenuListItemActionButton(actionButton)
    }
}

@Composable
private fun MenuListItemActionButton(actionButton: MenuListItemActionButton?) {
    if (actionButton != null) {
        if (actionButton.showDivider) {
            VerticalDivider(
                modifier = Modifier.padding(vertical = AcornTheme.layout.space.static100),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        IconButton(
            onClick = {},
            contentDescription = actionButton.contentDescription.value,
            modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
            Icon(
                painter = painterResource(actionButton.icon),
                contentDescription = null,
                modifier = Modifier.size(AcornTheme.layout.space.static300),
            )
        }
    }
}

@Composable
private fun MenuListItemBadge(
    badge: MenuListItemBadge?,
    state: MenuItemState,
) {
    if (badge != null) {
        Text(
            text = badge.text.value,
            color = MaterialTheme.colorScheme.onSurface,
            style = AcornTheme.typography.headline8,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier =
                Modifier.clip(shape = MaterialTheme.shapes.extraLarge)
                    .background(state.badgeContainerColor)
                    .padding(
                        horizontal = AcornTheme.layout.space.static200,
                        vertical = AcornTheme.layout.space.static100,
                    ),
        )
    }
}

@Composable
private fun MenuListItemNewIndicator(
    showNewIndicator: Boolean,
    state: MenuItemState,
) {
    if (showNewIndicator) {
        StatusBadge(
            containerColor = state.newIndicatorContainerColor,
            status = stringResource(R.string.mozac_menu_item_new_indicator_label),
        )
    }
}

@Composable
private fun RowScope.MenuListItemText(
    title: Text,
    state: MenuItemState,
    summary: MenuListItemSummary?,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = title.value,
            style = AcornTheme.typography.subtitle1.copy(hyphens = Hyphens.Auto),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
            maxLines = 2,
            softWrap = true,
            color = state.contentColor,
        )

        if (summary != null) {
            Text(
                text = summary.text.value,
                style = AcornTheme.typography.caption,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                color = summary.state.secondaryContentColor,
            )
        }
    }
}

@Composable
private fun MenuListItemIcon(
    icon: MenuItemIcon?,
    state: MenuItemState,
) {
    if (icon != null) {
        BadgedIcon(
            painter = icon.painter,
            isHighlighted = icon.isHighlighted,
            modifier = Modifier.size(AcornTheme.layout.size.static300),
            size = BADGE_SIZE_SMALL,
            contentDescription = null,
            containerColor = MaterialTheme.colorScheme.information,
            tint = state.secondaryContentColor,
        )
    } else {
        Spacer(Modifier.size(AcornTheme.layout.size.static300))
    }
}

/**
 * Configuration of the summary text to shown in a [MenuListItem].
 *
 * @param text The text to show.
 * @param state The [MenuItemState] of this summary.
 */
internal data class MenuListItemSummary(
    val text: Text,
    val state: MenuItemState = DEFAULT,
)

/**
 * Configuration of a badge to shown in a [MenuListItem].
 *
 * @param text The text to show.
 * @param state The [MenuItemState] of this badge.
 */
internal data class MenuListItemBadge(
    val text: Text,
    val state: MenuItemState = DEFAULT,
)

/**
 * Configuration of an action button to shown in a [MenuListItem].
 *
 * @param icon The icon to show.
 * @param contentDescription The content description of this button.
 * @param showDivider Whether to show a divider before this button.
 * @param state The [MenuItemState] of this action button.
 */
internal data class MenuListItemActionButton(
    @DrawableRes val icon: Int,
    val contentDescription: Text,
    val showDivider: Boolean = false,
    val state: MenuItemState = DEFAULT,
)

@PreviewLightDark
@Composable
private fun MenuListItemPreview() {
    AcornTheme {
        MenuListItem(
            title = Text.String("Title"),
            contentDescription = Text.String(""),
            modifier = Modifier.fillMaxWidth(),
            role = Button,
            summary = MenuListItemSummary(Text.String("Summary")),
            icon = MenuItemIconRes(iconsR.drawable.mozac_ic_globe_24),
            showNewIndicator = true,
            badge = MenuListItemBadge(Text.String("Badge")),
            actionButton =
                MenuListItemActionButton(
                    icon = iconsR.drawable.mozac_ic_chevron_right_24,
                    contentDescription = Text.String(""),
                    showDivider = true,
                ),
        )
    }
}

@PreviewLightDark
@Composable
private fun ActiveListItemPreview() {
    AcornTheme {
        MenuListItem(
            title = Text.String("Title"),
            contentDescription = Text.String(""),
            modifier = Modifier.fillMaxWidth(),
            role = Button,
            summary = MenuListItemSummary(Text.String("Summary"), ACTIVE),
            icon = MenuItemIconRes(iconsR.drawable.mozac_ic_globe_24),
            showNewIndicator = true,
            badge = MenuListItemBadge(Text.String("Badge"), ACTIVE),
            actionButton =
                MenuListItemActionButton(
                    icon = iconsR.drawable.mozac_ic_chevron_right_24,
                    contentDescription = Text.String(""),
                    showDivider = true,
                    state = ACTIVE,
                ),
            state = ACTIVE,
        )
    }
}

@PreviewLightDark
@Composable
private fun DisabledMenuListItemPreview() {
    AcornTheme {
        MenuListItem(
            title = Text.String("Title"),
            contentDescription = Text.String(""),
            modifier = Modifier.fillMaxWidth(),
            role = Button,
            summary = MenuListItemSummary(Text.String("Summary"), DISABLED),
            icon = MenuItemIconRes(iconsR.drawable.mozac_ic_globe_24),
            showNewIndicator = true,
            badge = MenuListItemBadge(Text.String("Badge"), DISABLED),
            actionButton =
                MenuListItemActionButton(
                    icon = iconsR.drawable.mozac_ic_chevron_right_24,
                    contentDescription = Text.String(""),
                    showDivider = true,
                    state = DISABLED,
                ),
            state = DISABLED,
        )
    }
}

@PreviewLightDark
@Composable
private fun WarningMenuListItemPreview() {
    AcornTheme {
        MenuListItem(
            title = Text.String("Title"),
            contentDescription = Text.String(""),
            modifier = Modifier.fillMaxWidth(),
            role = Button,
            summary = MenuListItemSummary(Text.String("Summary"), WARNING),
            icon = MenuItemIconRes(iconsR.drawable.mozac_ic_globe_24),
            showNewIndicator = true,
            badge = MenuListItemBadge(Text.String("Badge"), WARNING),
            actionButton =
                MenuListItemActionButton(
                    icon = iconsR.drawable.mozac_ic_chevron_right_24,
                    contentDescription = Text.String(""),
                    showDivider = true,
                    state = WARNING,
                ),
            state = WARNING,
        )
    }
}

@PreviewLightDark
@Composable
private fun TitleOnlyMenuListItemPreview() {
    AcornTheme {
        MenuListItem(
            title = Text.String("Title"),
            contentDescription = Text.String(""),
            role = Button,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
