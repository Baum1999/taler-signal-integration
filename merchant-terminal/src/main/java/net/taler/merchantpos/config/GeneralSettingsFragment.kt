/*
 * This file is part of GNU Taler
 * (C) 2026 Taler Systems S.A.
 *
 * GNU Taler is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3, or (at your option) any later version.
 *
 * GNU Taler is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * GNU Taler; see the file COPYING.  If not, see <http://www.gnu.org/licenses/>
 */

package net.taler.merchantpos.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import net.taler.merchantpos.compose.PosOutlinedCard
import net.taler.merchantpos.compose.PosTheme
import net.taler.merchantpos.R
import net.taler.merchantpos.PosDestination
import java.util.Locale

private data class LanguageOption(
    val languageTag: String,
    val label: String,
)

private data class InitialOrderOption(
    val screen: InitialOrderScreen,
    val label: String,
)

private val SettingsControlShape: Shape = RoundedCornerShape(14.dp)

class GeneralSettingsFragment : Fragment() {

    private val viewModel: net.taler.merchantpos.MainViewModel by activityViewModels()
    private val configManager by lazy { viewModel.configManager }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val languageOptions = createLanguageOptions()
        val initialOrderOptions = createInitialOrderOptions()
        val selectedTag = getCurrentLanguageTag()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GeneralSettingsScreen(
                    languageOptions = languageOptions,
                    initialSelectedTag = selectedTag,
                    onLanguageSelected = ::applyLanguage,
                    initialOrderOptions = initialOrderOptions,
                    initialSelectedOrderScreen = configManager.initialOrderScreen,
                    onInitialOrderSelected = { configManager.initialOrderScreen = it },
                    onInstanceSettingsClick = {
                        (requireActivity() as net.taler.merchantpos.MainActivity).navigateTo(PosDestination.Config)
                    },
                    onLogoutClick = {
                        configManager.logout()
                        (requireActivity() as net.taler.merchantpos.MainActivity).navigateTo(
                            PosDestination.Config,
                            clearBackStack = true,
                        )
                    },
                )
            }
        }
    }

    private fun createInitialOrderOptions(): List<InitialOrderOption> {
        return listOf(
            InitialOrderOption(
                screen = InitialOrderScreen.AmountEntry,
                label = getString(R.string.menu_amount_entry),
            ),
            InitialOrderOption(
                screen = InitialOrderScreen.Inventory,
                label = getString(R.string.menu_order),
            ),
        )
    }

    private fun createLanguageOptions(): List<LanguageOption> {
        val values = resources.getStringArray(R.array.settings_language_values)
        val labels = resources.getStringArray(R.array.settings_language_labels)
        return values.indices.map { index ->
            val tag = values[index]
            val label = if (index < labels.size && labels[index].isNotBlank()) {
                labels[index]
            } else if (tag.isBlank()) {
                getString(R.string.settings_language_system_default)
            } else {
                val locale = Locale.forLanguageTag(tag)
                locale.getDisplayName(Locale.getDefault()).replaceFirstChar { it.titlecase(locale) }
            }
            LanguageOption(tag, label)
        }
    }

    private fun getCurrentLanguageTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) "" else locales[0]?.toLanguageTag().orEmpty()
    }

    private fun applyLanguage(languageTag: String) {
        val locales = if (languageTag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralSettingsScreen(
    languageOptions: List<LanguageOption>,
    initialSelectedTag: String,
    onLanguageSelected: (String) -> Unit,
    initialOrderOptions: List<InitialOrderOption>,
    initialSelectedOrderScreen: InitialOrderScreen,
    onInitialOrderSelected: (InitialOrderScreen) -> Unit,
    onInstanceSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    var languageExpanded by remember { mutableStateOf(false) }
    var selectedTag by rememberSaveable { mutableStateOf(initialSelectedTag) }
    val selectedLabel = languageOptions.firstOrNull { it.languageTag == selectedTag }?.label
        ?: languageOptions.firstOrNull()?.label.orEmpty()
    var initialOrderExpanded by remember { mutableStateOf(false) }
    var selectedInitialOrderScreen by rememberSaveable {
        mutableStateOf(initialSelectedOrderScreen)
    }
    val selectedInitialOrderLabel =
        initialOrderOptions.firstOrNull { it.screen == selectedInitialOrderScreen }?.label
            ?: initialOrderOptions.firstOrNull()?.label.orEmpty()

    PosTheme {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsCard(
                    title = stringResource(R.string.settings_app_title),
                ) {
                    SettingsDropdown(
                        expanded = languageExpanded,
                        onExpandedChange = { languageExpanded = it },
                        value = selectedLabel,
                        label = stringResource(R.string.settings_language_hint),
                        options = languageOptions,
                        optionLabel = { it.label },
                        onOptionSelected = { option ->
                            selectedTag = option.languageTag
                            languageExpanded = false
                            onLanguageSelected(option.languageTag)
                        },
                    )
                    SettingsDropdown(
                        expanded = initialOrderExpanded,
                        onExpandedChange = { initialOrderExpanded = it },
                        value = selectedInitialOrderLabel,
                        label = stringResource(R.string.settings_initial_order_hint),
                        options = initialOrderOptions,
                        optionLabel = { it.label },
                        onOptionSelected = { option ->
                            selectedInitialOrderScreen = option.screen
                            initialOrderExpanded = false
                            onInitialOrderSelected(option.screen)
                        },
                    )
                }
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_instance_title),
                    description = stringResource(R.string.settings_instance_description),
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onInstanceSettingsClick,
                        shape = SettingsControlShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu_manage),
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_instance_button),
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onLogoutClick,
                        shape = SettingsControlShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_logout_button),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    PosOutlinedCard(
        contentPadding = PaddingValues(16.dp),
    ) { contentPadding ->
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingsDropdown(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    value: String,
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { onExpandedChange(!expanded) },
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                ),
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = SettingsControlShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
                unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { onOptionSelected(option) },
                )
            }
        }
    }
}
