package io.github.nilsmorczinietz.nod.ui.pairing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.github.nilsmorczinietz.nod.R
import io.github.nilsmorczinietz.nod.ui.components.Eyebrow
import io.github.nilsmorczinietz.nod.ui.components.NodButton
import io.github.nilsmorczinietz.nod.ui.components.NodMark
import io.github.nilsmorczinietz.nod.ui.components.NodTextField
import io.github.nilsmorczinietz.nod.ui.components.openUrl
import io.github.nilsmorczinietz.nod.ui.theme.NodMotion
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme
import io.github.nilsmorczinietz.nod.ui.theme.NodType

private const val SETUP_GUIDE_URL = "https://github.com/NilsMorczinietz/nod/blob/main/docs/setup.md"
private const val CHIP = "chip"

/** Mirrors the desktop "Pair this PC" screen: three steps, one link field with Pair, manual entry on demand. */
@Composable
fun PairingScreen(viewModel: PairingViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = NodTheme.colors
    val context = LocalContext.current
    var link by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf(false) }

    val scan = {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                barcode.rawValue?.let {
                    link = it
                    viewModel.onPairingLink(it)
                }
            }
    }
    val pairLink = { if (link.isNotBlank() && !state.busy) viewModel.onPairingLink(link.trim()) }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        NodMark(colors.primary, size = 28.dp)
        Spacer(Modifier.height(32.dp))
        Eyebrow(stringResource(R.string.pairing_eyebrow))
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.pairing_title), style = NodType.h2.copy(fontSize = 32.sp, lineHeight = 36.sp), color = colors.foreground)
        Spacer(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Step(1, stringResource(R.string.pairing_step_run))
            Step(2, stringResource(R.string.pairing_step_scan))
            Step(3, stringResource(R.string.pairing_step_notifications))
        }
        Spacer(Modifier.height(28.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            NodTextField(
                value = link,
                onValueChange = { link = it },
                placeholder = stringResource(R.string.pairing_link_placeholder),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { pairLink() }),
                modifier = Modifier.weight(1f),
                trailing = {
                    IconButton(onClick = { scan() }, enabled = !state.busy) {
                        QrScanIcon(colors.mutedForeground, stringResource(R.string.action_scan_qr))
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            NodButton(
                stringResource(R.string.action_pair),
                onClick = pairLink,
                modifier = Modifier.widthIn(min = 72.dp),
                enabled = link.isNotBlank() && !state.busy,
                height = 48.dp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.pairing_manual_hint),
            style = NodType.meta,
            color = colors.mutedForeground,
            modifier = Modifier.clickable { manual = !manual }.padding(vertical = 4.dp),
        )

        AnimatedVisibility(visible = manual, enter = fadeIn(NodMotion.enter()) + expandVertically(), exit = fadeOut(NodMotion.enter()) + shrinkVertically()) {
            ManualEntry(state, viewModel, Modifier.padding(top = 12.dp))
        }

        state.error?.let { error ->
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(
                    when (error) {
                        PairingError.InvalidLink -> R.string.pairing_error_link
                        PairingError.InsecureUrl -> R.string.pairing_error_https
                        PairingError.InvalidCode -> R.string.pairing_error_code
                        PairingError.Network -> R.string.pairing_error_network
                    },
                ),
                color = colors.danger,
                style = NodType.meta,
            )
        }

        Spacer(Modifier.height(40.dp))
        val guide = stringResource(R.string.pairing_setup_guide)
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.pairing_footnote))
                append(" ")
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(guide) }
            },
            style = NodType.meta,
            color = colors.mutedForeground,
            modifier = Modifier.clickable { context.openUrl(SETUP_GUIDE_URL) }.padding(vertical = 4.dp),
        )
    }
}

/** "01" in Geist Mono, then the sentence in muted body text with `code` as inline chips. */
@Composable
private fun Step(number: Int, text: String) {
    val colors = NodTheme.colors
    Row {
        Text(
            "%02d".format(number),
            style = NodType.mono.copy(fontSize = 13.sp),
            color = colors.accentText,
            modifier = Modifier.width(36.dp).padding(top = 3.dp),
        )
        CodeChipText(text, Modifier.weight(1f))
    }
}

/**
 * Body text where `backticked` parts render like the desktop's inline code:
 * Geist Mono 13 in the foreground color on a `muted` chip with radius 6.
 */
@Composable
private fun CodeChipText(text: String, modifier: Modifier = Modifier) {
    val colors = NodTheme.colors
    val mono = NodType.code.fontFamily
    val annotated = remember(text, mono, colors.foreground) { withCodeChips(text, SpanStyle(fontFamily = mono, fontSize = 13.sp, color = colors.foreground)) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        annotated,
        style = NodType.body,
        color = colors.mutedForeground,
        onTextLayout = { layout = it },
        // Screen readers get the plain sentence, without the joiners that keep chips on one line.
        modifier = modifier.semantics { contentDescription = text.replace("`", "") }.drawBehind {
            val result = layout ?: return@drawBehind
            annotated.getStringAnnotations(CHIP, 0, annotated.length).forEach { chip ->
                drawChip(result, chip.start, chip.end, colors.muted)
            }
        },
    )
}

/**
 * Wraps each `code` part in narrow no-break spaces (the chip's padding) and marks its range.
 * Chips never break across lines: spaces become NBSP and characters are glued with WORD JOINER.
 */
private fun withCodeChips(text: String, code: SpanStyle): AnnotatedString = buildAnnotatedString {
    val padding = SpanStyle(fontSize = 8.sp)
    text.split('`').forEachIndexed { index, part ->
        if (index % 2 == 0) {
            append(part)
        } else {
            val start = length
            withStyle(padding) { append(' ') }
            withStyle(code) { append(part.replace(' ', '\u00A0').toList().joinToString("\u2060")) }
            withStyle(padding) { append(' ') }
            addStringAnnotation(CHIP, part, start, length)
        }
    }
}

/** Rounded background behind [start, end), split per line if the chip wraps. */
private fun DrawScope.drawChip(layout: TextLayoutResult, start: Int, end: Int, color: Color) {
    val height = 21.dp.toPx()
    val radius = CornerRadius(6.dp.toPx())
    for (line in layout.getLineForOffset(start)..layout.getLineForOffset(end - 1)) {
        val from = maxOf(start, layout.getLineStart(line))
        val to = minOf(end, layout.getLineEnd(line, visibleEnd = true))
        if (from >= to) continue
        val left = layout.getHorizontalPosition(from, usePrimaryDirection = true)
        val right = if (to == layout.getLineEnd(line, visibleEnd = true)) layout.getLineRight(line) else layout.getHorizontalPosition(to, usePrimaryDirection = true)
        val center = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
        drawRoundRect(color, topLeft = Offset(left, center - height / 2f), size = Size(right - left, height), cornerRadius = radius)
    }
}

/** Relay URL and code as separate fields, for when only the parts are at hand. */
@Composable
private fun ManualEntry(state: PairingUiState, viewModel: PairingViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NodTextField(
            value = state.relayUrl,
            onValueChange = viewModel::onRelayChange,
            placeholder = stringResource(R.string.pairing_relay_placeholder),
            contentDescription = stringResource(R.string.pairing_relay_url),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            NodTextField(
                value = state.code,
                onValueChange = viewModel::onCodeChange,
                placeholder = stringResource(R.string.pairing_code_placeholder),
                contentDescription = stringResource(R.string.pairing_code),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.pair() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            NodButton(
                stringResource(R.string.action_pair),
                onClick = viewModel::pair,
                modifier = Modifier.widthIn(min = 72.dp),
                enabled = state.relayUrl.isNotBlank() && state.code.isNotBlank() && !state.busy,
                height = 48.dp,
            )
        }
    }
}

/** QR viewfinder: four corner brackets around a small square (no extended icon set needed). */
@Composable
private fun QrScanIcon(color: Color, contentDescription: String) {
    Canvas(Modifier.size(20.dp).semantics { this.contentDescription = contentDescription }) {
        val s = size.minDimension
        val w = 1.6.dp.toPx()
        val arm = s * 0.28f
        val inset = w / 2f
        val far = s - inset
        listOf(
            Offset(inset, inset) to Offset(1f, 1f),
            Offset(far, inset) to Offset(-1f, 1f),
            Offset(inset, far) to Offset(1f, -1f),
            Offset(far, far) to Offset(-1f, -1f),
        ).forEach { (corner, dir) ->
            drawLine(color, corner, corner + Offset(arm * dir.x, 0f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(color, corner, corner + Offset(0f, arm * dir.y), strokeWidth = w, cap = StrokeCap.Round)
        }
        val box = s * 0.3f
        drawRoundRect(color, topLeft = Offset((s - box) / 2f, (s - box) / 2f), size = Size(box, box), cornerRadius = CornerRadius(1.5.dp.toPx()))
    }
}
