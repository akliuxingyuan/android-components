/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.addons.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.R
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.utils.ext.getParcelableCompat

internal const val KEY_ADDON = "KEY_ADDON"
private const val KEY_DIALOG_GRAVITY = "KEY_DIALOG_GRAVITY"
private const val KEY_DIALOG_WIDTH_MATCH_PARENT = "KEY_DIALOG_WIDTH_MATCH_PARENT"
private const val KEY_POSITIVE_BUTTON_BACKGROUND_COLOR = "KEY_POSITIVE_BUTTON_BACKGROUND_COLOR"
private const val KEY_POSITIVE_BUTTON_TEXT_COLOR = "KEY_POSITIVE_BUTTON_TEXT_COLOR"
private const val KEY_POSITIVE_BUTTON_DISABLED_BACKGROUND_COLOR = "KEY_POSITIVE_BUTTON_DISABLED_BACKGROUND_COLOR"
private const val KEY_POSITIVE_BUTTON_DISABLED_TEXT_COLOR = "KEY_POSITIVE_BUTTON_DISABLED_TEXT_COLOR"
private const val KEY_POSITIVE_BUTTON_RADIUS = "KEY_POSITIVE_BUTTON_RADIUS"
private const val KEY_LEARN_MORE_LINK_TEXT_COLOR = "KEY_LEARN_MORE_LINK_TEXT_COLOR"
private const val KEY_FOR_OPTIONAL_PERMISSIONS = "KEY_FOR_OPTIONAL_PERMISSIONS"
internal const val KEY_PERMISSIONS = "KEY_PERMISSIONS"
internal const val KEY_ORIGINS = "KEY_ORIGINS"
internal const val KEY_DATA_COLLECTION_PERMISSIONS = "KEY_DATA_COLLECTION_PERMISSIONS"
private const val DEFAULT_VALUE = Int.MAX_VALUE
private const val TECHNICAL_AND_INTERACTION_PERM = "technicalAndInteraction"

/**
 * A dialog that shows a set of permission required by an [Addon].
 */
class PermissionsDialogFragment : AddonDialogFragment() {

    /**
     * A lambda called when the allow button is clicked which contains the [Addon] and
     * whether the addon is allowed in private browsing mode.
     */
    var onPositiveButtonClicked: ((Addon, Boolean, Boolean) -> Unit)? = null

    /**
     * A lambda called when the deny button is clicked.
     */
    var onNegativeButtonClicked: (() -> Unit)? = null

    /**
     * A lambda called when the learn more link is clicked.
     */
    var onLearnMoreClicked: (() -> Unit)? = null

    internal val addon
        get() = requireNotNull(
            safeArguments.getParcelableCompat(
                KEY_ADDON,
                Addon::class.java,
            ),
        )

    internal val positiveButtonRadius
        get() =
            safeArguments.getFloat(KEY_POSITIVE_BUTTON_RADIUS, DEFAULT_VALUE.toFloat())

    internal val dialogGravity: Int
        get() =
            safeArguments.getInt(
                KEY_DIALOG_GRAVITY,
                DEFAULT_VALUE,
            )
    internal val dialogShouldWidthMatchParent: Boolean
        get() =
            safeArguments.getBoolean(KEY_DIALOG_WIDTH_MATCH_PARENT)

    internal val positiveButtonBackgroundColor
        get() =
            safeArguments.getInt(
                KEY_POSITIVE_BUTTON_BACKGROUND_COLOR,
                DEFAULT_VALUE,
            )

    internal val positiveButtonTextColor
        get() =
            safeArguments.getInt(
                KEY_POSITIVE_BUTTON_TEXT_COLOR,
                DEFAULT_VALUE,
            )
    internal val positiveButtonDisabledBackgroundColor
        get() =
            safeArguments.getInt(
                KEY_POSITIVE_BUTTON_DISABLED_BACKGROUND_COLOR,
                DEFAULT_VALUE,
            )

    internal val positiveButtonDisabledTextColor
        get() =
            safeArguments.getInt(
                KEY_POSITIVE_BUTTON_DISABLED_TEXT_COLOR,
                DEFAULT_VALUE,
            )

    internal val learnMoreLinkTextColor
        get() =
            safeArguments.getInt(
                KEY_LEARN_MORE_LINK_TEXT_COLOR,
                DEFAULT_VALUE,
            )

    /**
     * This flag is used to adjust the permissions prompt for optional permissions (instead of asking
     * users to grant the required permissions at install time, which is the default).
     */
    internal val forOptionalPermissions: Boolean
        get() =
            safeArguments.getBoolean(KEY_FOR_OPTIONAL_PERMISSIONS)

    internal val permissions get() = requireNotNull(safeArguments.getStringArray(KEY_PERMISSIONS))
    internal val origins get() = requireNotNull(safeArguments.getStringArray(KEY_ORIGINS))
    internal val dataCollectionPermissions
        get() = requireNotNull(
            safeArguments.getStringArray(
                KEY_DATA_COLLECTION_PERMISSIONS,
            ),
        )
    internal val hasDataCollectionOnly
        get() = permissions.isEmpty() && origins.isEmpty() && dataCollectionPermissions.isNotEmpty()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val sheetDialog = Dialog(requireContext())
        sheetDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        sheetDialog.setCanceledOnTouchOutside(true)

        val rootView = createContainer()

        sheetDialog.setContainerView(rootView)

        sheetDialog.window?.apply {
            if (dialogGravity != DEFAULT_VALUE) {
                setGravity(dialogGravity)
            }

            if (dialogShouldWidthMatchParent) {
                setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                // This must be called after addContentView, or it won't fully fill to the edge.
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        return sheetDialog
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        onNegativeButtonClicked?.invoke()
    }

    private fun Dialog.setContainerView(rootView: View) {
        if (dialogShouldWidthMatchParent) {
            setContentView(rootView)
        } else {
            addContentView(
                rootView,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    @SuppressLint("InflateParams")
    @Suppress("LongMethod")
    private fun createContainer(): View {
        val rootView = LayoutInflater.from(requireContext()).inflate(
            R.layout.mozac_feature_addons_fragment_dialog_addon_permissions,
            null,
            false,
        )

        loadIcon(addon = addon, iconView = rootView.findViewById(R.id.icon))

        rootView.findViewById<TextView>(R.id.title).text = buildTitleText(
            forOptionalPermissions = forOptionalPermissions,
            hasDataCollectionOnly = hasDataCollectionOnly,
        )

        val classifyOriginPermissionsResult = Addon.classifyOriginPermissions(origins = origins.toList())
        val hostPermissions = classifyOriginPermissionsResult.getOrNull()

        if (classifyOriginPermissionsResult.isFailure || hostPermissions == null) {
            handleOriginPermissionsException(classifyOriginPermissionsResult.exceptionOrNull())
            return rootView
        }

        val allUrlsPermissionFound =
            Addon.permissionsListContainsAllUrls(permissions.toList()) ||
                !hostPermissions.allUrls.isNullOrEmpty()

        val displayDomainList = if (allUrlsPermissionFound) {
            // Show the All Urls permission instead of the list of domains
            setOf()
        } else {
            hostPermissions.wildcards + hostPermissions.sites
        }

        // "userScripts" can only be requested without other permissions, and
        // only with forOptionalPermissions=true. This is enforced at the Gecko
        // layer, in ext-permissions.js (via OptionalOnlyPermission).
        val isUserScriptsPermission = permissions.size == 1 && permissions[0] == "userScripts"

        val listPermissions = buildPermissionsList(allUrlsPermissionFound)
        val requiredPermissionsTitle = rootView.findViewById<TextView>(R.id.optional_or_required_text)
        if (listPermissions.isNotEmpty() || displayDomainList.isNotEmpty()) {
            requiredPermissionsTitle.isVisible = true
            requiredPermissionsTitle.text = buildOptionalOrRequiredText()
        } else {
            requiredPermissionsTitle.isVisible = false
        }

        val learnMoreLink = rootView.findViewById<TextView>(R.id.learn_more_link)
        learnMoreLink.paintFlags = Paint.UNDERLINE_TEXT_FLAG

        val permissionsRecyclerView = rootView.findViewById<RecyclerView>(R.id.permissions)
        val positiveButton = rootView.findViewById<Button>(R.id.allow_button)
        val negativeButton = rootView.findViewById<Button>(R.id.deny_button)
        val optionalsSettingsTitle = rootView.findViewById<TextView>(R.id.optional_settings_title)
        val allowedInPrivateBrowsing =
            rootView.findViewById<AppCompatCheckBox>(R.id.allow_in_private_browsing)
        val technicalAndInteraction =
            rootView.findViewById<AppCompatCheckBox>(R.id.technical_and_interaction_data)

        var extraPermissionWarning: String? = null
        if (isUserScriptsPermission) {
            extraPermissionWarning = requireContext()
                .getString(R.string.mozac_feature_addons_permissions_user_scripts_extra_warning)
        }

        renderDataCollectionPermissions(rootView)

        permissionsRecyclerView.adapter = RequiredPermissionsAdapter(
            permissions = listPermissions,
            permissionRequiresOptIn = isUserScriptsPermission,
            onPermissionOptInChanged = { enabled ->
                setButtonEnabled(positiveButton, enabled)
            },
            domains = displayDomainList,
            domainsHeaderText = requireContext()
                .getString(
                    R.string.mozac_feature_addons_permissions_all_domain_count_description,
                    displayDomainList.size,
                ),
            extraPermissionWarning = extraPermissionWarning,
        )
        permissionsRecyclerView.layoutManager = LinearLayoutManager(context)

        if (forOptionalPermissions) {
            positiveButton.text =
                requireContext().getString(R.string.mozac_feature_addons_permissions_dialog_allow)
            negativeButton.text =
                requireContext().getString(R.string.mozac_feature_addons_permissions_dialog_deny)
        }

        if (addon.incognito == Addon.Incognito.NOT_ALLOWED ||
            forOptionalPermissions
        ) {
            optionalsSettingsTitle.isVisible = false
            allowedInPrivateBrowsing.isVisible = false
        }

        if (dataCollectionPermissions.contains(TECHNICAL_AND_INTERACTION_PERM) && !forOptionalPermissions) {
            optionalsSettingsTitle.isVisible = true
            technicalAndInteraction.isVisible = true
            // This is an opt-out setting.
            technicalAndInteraction.isChecked = true
        }

        positiveButton.setOnClickListener {
            onPositiveButtonClicked?.invoke(
                addon,
                allowedInPrivateBrowsing.isChecked,
                technicalAndInteraction.isChecked,
            )
            dismiss()
        }

        if (isUserScriptsPermission) {
            // "userScripts" permission requires double-confirmation.
            // Disable "Allow" button until the user confirmed via opt-in.
            setButtonEnabled(positiveButton, false)
        } else {
            setButtonEnabled(positiveButton, true)
        }

        negativeButton.setOnClickListener {
            onNegativeButtonClicked?.invoke()
            dismiss()
        }

        if (learnMoreLinkTextColor != DEFAULT_VALUE) {
            val color = ContextCompat.getColor(requireContext(), learnMoreLinkTextColor)
            learnMoreLink.setTextColor(color)
        }
        learnMoreLink.setOnClickListener {
            onLearnMoreClicked?.invoke()
        }
        return rootView
    }

    private fun renderDataCollectionPermissions(rootView: View) {
        val dataCollectionTitle = rootView.findViewById<TextView>(R.id.optional_or_required_data_collection_text)
        val dataCollectionItem = rootView.findViewById<LinearLayout>(R.id.data_collection_permissions_item)

        if (dataCollectionPermissions.isNotEmpty()) {
            dataCollectionTitle.text = buildOptionalOrRequiredDataCollectionTitleText()

            val dataCollectionPermissionsView = rootView.findViewById<TextView>(R.id.data_collection_permissions)
            val text = buildDataCollectionPermissionsText(dataCollectionPermissions.toList(), forOptionalPermissions)
            dataCollectionPermissionsView.text = text

            // In case we don't have any text to display, let's hide the whole item.
            dataCollectionTitle.isVisible = !text.isNullOrEmpty()
            dataCollectionItem.isVisible = !text.isNullOrEmpty()
        } else {
            dataCollectionTitle.isVisible = false
            dataCollectionItem.isVisible = false
        }
    }

    /**
     * When an origin permission exception occurs we need to dismiss the permissions dialog, log
     * the error, and show the user a visual notification.
     *
     * @param throwable exception for why classification failed
     */
    private fun handleOriginPermissionsException(throwable: Throwable?) {
        Toast.makeText(
            requireContext(),
            R.string.mozac_feature_addons_extension_failed_to_install_corrupt_error,
            Toast.LENGTH_LONG,
        ).show()

        Logger.error(
            "Addon ID ${addon.id} has an incorrectly formatted host " +
                "permissions which has caused the Addon installation to fail",
            throwable,
        )

        this.dismiss()
    }

    @VisibleForTesting
    internal fun buildPermissionsList(
        isAllUrlsPermissionFound: Boolean,
    ): List<String> {
        val result = if (isAllUrlsPermissionFound) {
            permissions
                .toMutableList()
                .apply {
                    if (contains("<all_urls>")) {
                        // If found, move it to the front
                        remove("<all_urls>")
                    }

                    add(0, "<all_urls>")
                }
        } else {
            permissions.toList()
        }
        return Addon.localizePermissions(result, requireContext())
    }

    private fun buildDataCollectionPermissionsText(
        permissions: List<String>,
        forOptionalPermissions: Boolean,
    ): String? {
        // We shouldn't list the `technicalAndInteraction` permission in the sentence that indicates which (required)
        // data collection permissions are used in the extension during the installation flow. That's why we filter it
        // out.
        // That being said, we are using a variant of the same dialog for optional permission requests. In this case,
        // we want the `technicalAndInteraction` permission to be listed, hence the condition below.
        val filteredPermissions = if (forOptionalPermissions) {
            permissions
        } else {
            permissions.filter { it != "technicalAndInteraction" }
        }

        if (filteredPermissions.isEmpty()) {
            return null
        }

        if (filteredPermissions.size == 1 && filteredPermissions.contains("none")) {
            return requireContext().getString(
                R.string.mozac_feature_addons_permissions_none_required_data_collection_description,
            )
        }

        val localizedPermissions = Addon.localizeDataCollectionPermissions(filteredPermissions, requireContext())
        val formattedList = Addon.formatLocalizedDataCollectionPermissions(localizedPermissions)

        return requireContext().getString(
            if (forOptionalPermissions) {
                R.string.mozac_feature_addons_permissions_data_collection_optional_description
            } else {
                R.string.mozac_feature_addons_permissions_required_data_collection_description_2
            },
            formattedList,
        )
    }

    private fun buildTitleText(forOptionalPermissions: Boolean, hasDataCollectionOnly: Boolean): String {
        return requireContext().getString(
            if (forOptionalPermissions) {
                if (hasDataCollectionOnly) {
                    R.string.mozac_feature_addons_optional_permissions_with_data_collection_only_dialog_title
                } else {
                    R.string.mozac_feature_addons_optional_permissions_with_data_collection_dialog_title
                }
            } else {
                R.string.mozac_feature_addons_permissions_dialog_title_2
            },
            addon.translateName(requireContext()),
        )
    }

    private fun buildOptionalOrRequiredText(): String {
        val optionalOrRequiredText = if (forOptionalPermissions) {
            getString(R.string.mozac_feature_addons_permissions_dialog_heading_optional_permissions)
        } else {
            getString(R.string.mozac_feature_addons_permissions_dialog_heading_required_permissions)
        }

        return optionalOrRequiredText
    }

    private fun buildOptionalOrRequiredDataCollectionTitleText(): String {
        val optionalOrRequiredText = if (forOptionalPermissions) {
            getString(R.string.mozac_feature_addons_permissions_dialog_heading_optional_data_collection)
        } else {
            getString(R.string.mozac_feature_addons_permissions_dialog_heading_required_data_collection)
        }

        return optionalOrRequiredText
    }

    internal fun setButtonEnabled(button: Button, enabled: Boolean) {
        button.isEnabled = enabled

        val backgroundColor = if (enabled) {
            positiveButtonBackgroundColor
        } else {
            positiveButtonDisabledBackgroundColor
        }
        val textColor = if (enabled) {
            positiveButtonTextColor
        } else {
            positiveButtonDisabledTextColor
        }
        if (backgroundColor != DEFAULT_VALUE) {
            val backgroundTintList =
                AppCompatResources.getColorStateList(requireContext(), backgroundColor)
            button.backgroundTintList = backgroundTintList
        }

        if (textColor != DEFAULT_VALUE) {
            val color = ContextCompat.getColor(requireContext(), textColor)
            button.setTextColor(color)
        }
        if (positiveButtonRadius != DEFAULT_VALUE.toFloat()) {
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            shape.setColor(
                ContextCompat.getColor(
                    requireContext(),
                    backgroundColor,
                ),
            )
            shape.cornerRadius = positiveButtonRadius
            button.background = shape
        }
    }

    companion object {
        /**
         * Returns a new instance of [PermissionsDialogFragment].
         * @param addon The addon to show in the dialog.
         * @param forOptionalPermissions Whether to show a permission dialog for optional permissions
         * requested by the extension.
         * @param permissions The permissions requested by the extension.
         * @param origins The host permissions requested by the extension.
         * @param promptsStyling Styling properties for the dialog.
         * @param onPositiveButtonClicked A lambda called when the allow button is clicked.
         * @param onNegativeButtonClicked A lambda called when the deny button is clicked.
         * @param onLearnMoreClicked A lambda called when the learn more button is clicked.
         */
        fun newInstance(
            addon: Addon,
            permissions: List<String>,
            origins: List<String>,
            dataCollectionPermissions: List<String>,
            forOptionalPermissions: Boolean = false,
            promptsStyling: PromptsStyling? = PromptsStyling(
                gravity = Gravity.BOTTOM,
                shouldWidthMatchParent = true,
            ),
            onPositiveButtonClicked: ((Addon, Boolean, Boolean) -> Unit)? = null,
            onNegativeButtonClicked: (() -> Unit)? = null,
            onLearnMoreClicked: (() -> Unit)? = null,
        ): PermissionsDialogFragment {
            val fragment = PermissionsDialogFragment()
            val arguments = fragment.arguments ?: Bundle()

            arguments.apply {
                putParcelable(KEY_ADDON, addon)
                putBoolean(KEY_FOR_OPTIONAL_PERMISSIONS, forOptionalPermissions)
                putStringArray(KEY_PERMISSIONS, permissions.toTypedArray())
                putStringArray(KEY_ORIGINS, origins.toTypedArray())
                putStringArray(KEY_DATA_COLLECTION_PERMISSIONS, dataCollectionPermissions.toTypedArray())

                promptsStyling?.gravity?.apply {
                    putInt(KEY_DIALOG_GRAVITY, this)
                }
                promptsStyling?.shouldWidthMatchParent?.apply {
                    putBoolean(KEY_DIALOG_WIDTH_MATCH_PARENT, this)
                }
                promptsStyling?.confirmButtonBackgroundColor?.apply {
                    putInt(KEY_POSITIVE_BUTTON_BACKGROUND_COLOR, this)
                }
                promptsStyling?.confirmButtonTextColor?.apply {
                    putInt(KEY_POSITIVE_BUTTON_TEXT_COLOR, this)
                }
                promptsStyling?.confirmButtonDisabledBackgroundColor?.apply {
                    putInt(KEY_POSITIVE_BUTTON_DISABLED_BACKGROUND_COLOR, this)
                }
                promptsStyling?.confirmButtonDisabledTextColor?.apply {
                    putInt(KEY_POSITIVE_BUTTON_DISABLED_TEXT_COLOR, this)
                }
                promptsStyling?.learnMoreLinkTextColor?.apply {
                    putInt(KEY_LEARN_MORE_LINK_TEXT_COLOR, this)
                }
            }
            fragment.onPositiveButtonClicked = onPositiveButtonClicked
            fragment.onNegativeButtonClicked = onNegativeButtonClicked
            fragment.onLearnMoreClicked = onLearnMoreClicked
            fragment.arguments = arguments
            return fragment
        }
    }
}
