package app.ui.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.audio.DmpAvailability
import app.audio.DmpTrackKind
import app.audio.LocalOst
import app.audio.MusicMode
import app.audio.MusicPack
import app.audio.MusicSettings
import app.audio.OstTracks
import app.ui.theme.DuelColors

/**
 * Music popover. Sections:
 *   1. Mode chips (Auto / Manual / Off)
 *   2. Pack chips (six, each tinted with that pack's signature color)
 *   3. Track chips (Manual mode only — picks the looping kind)
 *   4. One-shots row (Tribute Summon for current pack + Exodia). Tap to
 *      play; tap again while playing to stop. Highlighted while active —
 *      driven by the [LocalOst] currentOverlay StateFlow.
 */
@Composable
fun MusicSettingsDialog(
    settings: MusicSettings,
    onSettingsChange: (MusicSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val ost = LocalOst.current
    val activeOverlay by ost.currentOverlay.collectAsState()

    val tributeTrack = OstTracks.dmp(settings.pack, DmpTrackKind.TributeSummon)
    val exodiaTrack = OstTracks.Exodia

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1140), RoundedCornerShape(14.dp))
                .border(1.dp, DuelColors.DuelGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Music",
                    style = MaterialTheme.typography.titleLarge,
                    color = DuelColors.DuelGoldGlow,
                    fontWeight = FontWeight.Bold,
                )

                SectionLabel("Mode")
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MusicMode.entries.forEach { mode ->
                        Chip(
                            text = mode.displayName,
                            selected = settings.mode == mode,
                            onClick = { onSettingsChange(settings.copy(mode = mode)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                SectionLabel("Pack")
                ChipGrid(
                    items = MusicPack.entries,
                    isSelected = { it == settings.pack },
                    label = { it.displayName },
                    tint = { it.color },
                    onClick = { onSettingsChange(settings.copy(pack = it)) },
                )

                if (settings.mode == MusicMode.Manual) {
                    SectionLabel("Track")
                    val loopingKinds = DmpTrackKind.entries.filter { !it.isOneShot }
                    ChipGrid(
                        items = loopingKinds,
                        isSelected = { it == settings.manualTrack },
                        label = { it.displayName },
                        tint = { DuelColors.DuelGold },
                        onClick = { onSettingsChange(settings.copy(manualTrack = it)) },
                    )
                }

                SectionLabel("One-shots")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val packHasTribute = DmpAvailability.has(settings.pack, DmpTrackKind.TributeSummon)
                    val tributeLabel = if (packHasTribute) "Tribute summon" else "Tribute (Joey)"
                    OneShotButton(
                        label = tributeLabel,
                        active = activeOverlay?.cacheKey == tributeTrack.cacheKey,
                        onClick = {
                            if (activeOverlay?.cacheKey == tributeTrack.cacheKey) ost.stopOverlay()
                            else ost.playOverlay(tributeTrack)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    OneShotButton(
                        label = "Exodia",
                        active = activeOverlay?.cacheKey == exodiaTrack.cacheKey,
                        onClick = {
                            if (activeOverlay?.cacheKey == exodiaTrack.cacheKey) ost.stopOverlay()
                            else ost.playOverlay(exodiaTrack)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = DuelColors.DuelGoldGlow)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = DuelColors.DuelGoldGlow.copy(alpha = 0.75f),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun <T> ChipGrid(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    tint: (T) -> Color,
    onClick: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { item ->
                    TintedChip(
                        text = label(item),
                        selected = isSelected(item),
                        tint = tint(item),
                        onClick = { onClick(item) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TintedChip(
    text: String,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .background(
                color = if (selected) tint else tint.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) DuelColors.DuelGoldGlow else tint,
                shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.matchParentSize(),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) {
            Text(
                text,
                color = if (selected) Color.White else Color(0xFFEDE7FF),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(
                color = if (selected) DuelColors.DuelGold else Color(0xFF120A33),
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = 1.dp,
                color = DuelColors.DuelGold.copy(alpha = if (selected) 1f else 0.45f),
                shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick, modifier = Modifier.matchParentSize(), contentPadding = PaddingValues(0.dp)) {
            Text(
                text,
                color = if (selected) Color.Black else DuelColors.DuelGoldGlow,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun OneShotButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .background(
                color = if (active) DuelColors.DuelGold else Color(0xFF120A33),
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = if (active) 2.dp else 1.dp,
                color = DuelColors.DuelGold,
                shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.matchParentSize(),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) {
            Text(
                label,
                color = if (active) Color.Black else DuelColors.DuelGoldGlow,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
