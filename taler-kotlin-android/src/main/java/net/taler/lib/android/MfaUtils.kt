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

package net.taler.lib.android

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Color.TRANSPARENT
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.taler.common.Challenge
import net.taler.common.R
import kotlin.coroutines.resume

enum class ChallengeRetryDecision {
    Retry,
    Resend,
    Abort
}

class ChallengeCancelledException : Exception()

suspend fun Fragment.handleChallengeResponse(
    challenges: List<Challenge>,
    combiAnd: Boolean,
    onRequestChallenge: suspend (challengeId: String) -> Unit,
    onConfirmChallenge: suspend (challengeId: String, tan: String) -> Unit,
): List<String> {
    if (challenges.isEmpty()) return emptyList()
    val challengesToSolve = if (combiAnd) {
        challenges
    } else {
        val selected = selectChallenge(challenges) ?: return emptyList()
        listOf(selected)
    }

    val solvedIds = mutableListOf<String>()
    for (challenge in challengesToSolve) {
        onRequestChallenge(challenge.challengeId)

        while (true) {
            val tan = promptForTan(challenge) ?: return emptyList()
            try {
                onConfirmChallenge(challenge.challengeId, tan)
                solvedIds.add(challenge.challengeId)
                break
            } catch (e: Exception) {
                when (handleChallengeConfirmError(e)) {
                    ChallengeRetryDecision.Retry -> continue
                    ChallengeRetryDecision.Resend -> {
                        onRequestChallenge(challenge.challengeId)
                        continue
                    }
                    ChallengeRetryDecision.Abort -> return emptyList()
                }
            }
        }
    }
    return solvedIds
}

suspend fun Fragment.selectChallenge(challenges: List<Challenge>): Challenge? =
    showMfaDialog(cancelResult = null) { finish ->
        MfaDialogSurface {
            Text(
                text = stringResource(R.string.mfa_choose_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                challenges.forEach { challenge ->
                    Button(
                        onClick = { finish(challenge) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${challenge.tanChannel}: ${challenge.tanInfo}")
                    }
                }
            }
            DialogActions(
                onCancel = { finish(null) },
            )
        }
    }

suspend fun Fragment.promptForTan(
    challenge: Challenge,
): String? =
    showMfaDialog(cancelResult = null) { finish ->
        var code by remember { mutableStateOf("") }
        var showIncompleteError by remember { mutableStateOf(false) }
        val submit = {
            val normalizedCode = normalizeMfaCode(code)
            if (normalizedCode == null) {
                showIncompleteError = true
            } else {
                finish(formatMfaCode(normalizedCode))
            }
        }

        MfaDialogSurface {
            Text(
                text = stringResource(R.string.mfa_challenge_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(
                    R.string.mfa_challenge_message,
                    challenge.tanChannel.name,
                    challenge.tanInfo,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MfaCodeInput(
                code = code,
                isError = showIncompleteError,
                onCodeChanged = {
                    code = it
                    showIncompleteError = false
                },
                onSubmit = submit,
            )
            if (showIncompleteError) {
                Text(
                    text = stringResource(R.string.mfa_challenge_code_incomplete),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            DialogActions(
                onCancel = { finish(null) },
                onConfirm = submit,
            )
        }
    }

@Composable
private fun MfaDialogSurface(
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@Composable
private fun MfaCodeInput(
    code: String,
    isError: Boolean,
    onCodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val codeHint = stringResource(R.string.mfa_challenge_code_hint)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BasicTextField(
        value = code,
        onValueChange = { value ->
            onCodeChanged(value.filter(Char::isDigit).take(MFA_CODE_DIGITS))
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { contentDescription = codeHint },
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        interactionSource = interactionSource,
        decorationBox = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { focusRequester.requestFocus() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(MFA_CODE_DIGITS) { index ->
                    if (index == MFA_CODE_DIGITS / 2) {
                        Text(
                            text = "-",
                            modifier = Modifier.padding(horizontal = 3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    MfaCodeDigit(
                        digit = code.getOrNull(index),
                        isActive = index == code.length.coerceAtMost(MFA_CODE_DIGITS - 1),
                        isError = isError,
                    )
                }
            }
        },
    )
}

@Composable
private fun MfaCodeDigit(
    digit: Char?,
    isActive: Boolean,
    isError: Boolean,
) {
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .size(width = 30.dp, height = 52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.matchParentSize(),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(if (isActive) 2.dp else 1.dp, borderColor),
        ) {}
        Text(
            text = digit?.toString().orEmpty(),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DialogActions(
    onCancel: () -> Unit,
    onConfirm: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(android.R.string.cancel))
        }
        if (onConfirm != null) {
            Button(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        }
    }
}

private suspend fun <T> Fragment.showMfaDialog(
    cancelResult: T,
    content: @Composable ((T) -> Unit) -> Unit,
): T = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { continuation ->
        val dialog = Dialog(requireContext())
        var completed = false

        fun finish(result: T) {
            if (completed) return
            completed = true
            if (continuation.isActive) continuation.resume(result)
            dialog.dismiss()
        }

        val composeView = ComposeView(requireContext()).apply {
            setViewTreeLifecycleOwner(viewLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(this@showMfaDialog)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MaterialTheme(colorScheme = mfaColorScheme(context)) {
                    content(::finish)
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener { finish(cancelResult) }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(TRANSPARENT))
        dialog.window?.setLayout(MATCH_PARENT, WRAP_CONTENT)

        continuation.invokeOnCancellation {
            composeView.post { dialog.dismiss() }
        }
    }
}

suspend fun Fragment.handleChallengeConfirmError(e: Exception): ChallengeRetryDecision =
    withContext(Dispatchers.Main) {
        if (e is ClientRequestException) {
            when (e.response.status.value) {
                409 -> {
                    Toast.makeText(
                        requireContext(),
                        R.string.mfa_challenge_invalid,
                        Toast.LENGTH_LONG,
                    ).show()
                    return@withContext ChallengeRetryDecision.Retry
                }
                429 -> {
                    Toast.makeText(
                        requireContext(),
                        R.string.mfa_challenge_retry,
                        Toast.LENGTH_LONG,
                    ).show()
                    return@withContext ChallengeRetryDecision.Resend
                }
            }
        }
        Toast.makeText(
            requireContext(),
            R.string.mfa_challenge_failed,
            Toast.LENGTH_LONG,
        ).show()
        ChallengeRetryDecision.Abort
    }

internal fun normalizeMfaCode(value: String): String? {
    val digits = value.filter(Char::isDigit)
    return digits.takeIf { it.length == MFA_CODE_DIGITS }
}

internal fun formatMfaCode(value: String): String =
    value.take(MFA_CODE_DIGITS / 2) + "-" + value.drop(MFA_CODE_DIGITS / 2)

private fun mfaColorScheme(context: Context): ColorScheme {
    val isDark = context.resources.configuration.uiMode and UI_MODE_NIGHT_MASK ==
        UI_MODE_NIGHT_YES
    val primary = context.materialColor(androidx.appcompat.R.attr.colorPrimary)
    val onPrimary = context.materialColor(com.google.android.material.R.attr.colorOnPrimary)
    val primaryContainer = context.materialColor(
        com.google.android.material.R.attr.colorPrimaryContainer,
    )
    val onPrimaryContainer = context.materialColor(
        com.google.android.material.R.attr.colorOnPrimaryContainer,
    )
    val surface = context.materialColor(com.google.android.material.R.attr.colorSurface)
    val onSurface = context.materialColor(com.google.android.material.R.attr.colorOnSurface)
    val surfaceVariant = context.materialColor(
        com.google.android.material.R.attr.colorSurfaceVariant,
    )
    val onSurfaceVariant = context.materialColor(
        com.google.android.material.R.attr.colorOnSurfaceVariant,
    )
    val outline = context.materialColor(com.google.android.material.R.attr.colorOutline)

    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
        )
    }
}

private fun Context.materialColor(attr: Int): Color =
    Color(MaterialColors.getColor(this, attr, android.graphics.Color.MAGENTA))

private const val MFA_CODE_DIGITS = 8
