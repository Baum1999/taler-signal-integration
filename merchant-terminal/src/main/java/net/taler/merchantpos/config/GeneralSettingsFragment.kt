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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.navigation.fragment.findNavController
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.taler.merchantpos.compose.PosOutlinedCard
import net.taler.merchantpos.compose.PosTheme
import net.taler.merchantpos.R
import java.util.Locale

private data class LanguageOption(
    val languageTag: String,
    val label: String,
)

class GeneralSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val options = createLanguageOptions()
        val selectedTag = getCurrentLanguageTag()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GeneralSettingsScreen(
                    options = options,
                    initialSelectedTag = selectedTag,
                    onLanguageSelected = ::applyLanguage,
                    onInstanceSettingsClick = {
                        findNavController().navigate(R.id.action_settings_to_instanceSettings)
                    },
                )
            }
        }
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
    options: List<LanguageOption>,
    initialSelectedTag: String,
    onLanguageSelected: (String) -> Unit,
    onInstanceSettingsClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedTag by rememberSaveable { mutableStateOf(initialSelectedTag) }
    val selectedLabel = options.firstOrNull { it.languageTag == selectedTag }?.label
        ?: options.firstOrNull()?.label.orEmpty()

    PosTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsCard(
                title = stringResource(R.string.settings_language_label),
                description = stringResource(R.string.settings_language_description),
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(
                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = true,
                            ),
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_language_hint)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    selectedTag = option.languageTag
                                    expanded = false
                                    onLanguageSelected(option.languageTag)
                                },
                            )
                        }
                    }
                }
            }

            SettingsCard(
                title = stringResource(R.string.settings_instance_title),
                description = stringResource(R.string.settings_instance_description),
            ) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onInstanceSettingsClick,
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
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    description: String,
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
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
            )
            content()
        }
    }
}
