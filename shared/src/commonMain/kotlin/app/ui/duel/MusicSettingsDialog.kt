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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.audio.DmpAvailability
import app.audio.DmpTrackKind
import app.audio.MusicMode
import app.audio.MusicPack
import app.audio.MusicSettings
import app.ui.theme.DuelColors

/**
 * Music settings popover. Three sections:
 *  1. Mode (Auto / Manual / Off)
 *  2. Pack chips (each tinted with the pack's signature color)
 *  3. One-shot stingers (Tribute Summon for active pack, plus Exodia)
 */
@Composable
fun MusicSettingsDialog(
    settings: MusicSettings,
    onSettingsChange: (MusicSettings) -> Unit,
    onPlayTributeSummon: () -> Unit,
    onPlayExodia: () -> Unit,
    onDismiss: () -> Unit,
) {
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
                PackChipGrid(
                    selected = settings.pack,
                    onSelect = { onSettingsChange(settings.copy(pack = it)) },
                )

                if (settings.mode == MusicMode.Manual) {
                    SectionLabel("Track")
                    val loopingKinds = DmpTrackKind.entries.filter { !it.isOneShot }
                    Row(
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        loopingKinds.forEach { kind ->
                            Chip(
                                text = kind.displayName,
                                selected = settings.manualTrack == kind,
                                onClick = { onSettingsChange(settings.copy(manualTrack = kind)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                SectionLabel("One-shots")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val packHasTribute = DmpAvailability.has(settings.pack, DmpTrackKind.TributeSummon)
                    val tributeLabel = if (packHasTribute) "Tribute summon" else "Tribute summon (Joey)"
                    OneShotButton(
                        label = tributeLabel,
                        onClick = onPlayTributeSummon,
                        modifier = Modifier.weight(1f),
                    )
                    OneShotButton(
                        label = "Exodia",
                        onClick = onPlayExodia,
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
private fun PackChipGrid(selected: MusicPack, onSelect: (MusicPack) -> Unit) {
    val packs = MusicPack.entries
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        packs.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { pack ->
                    PackChip(
                        pack = pack,
                        selected = selected == pack,
                        onClick = { onSelect(pack) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PackChip(
    pack: MusicPack,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(
                color = if (selected) pack.color else pack.color.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) DuelColors.DuelGoldGlow else pack.color,
                shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick, modifier = Modifier.matchParentSize(), contentPadding = PaddingValues(0.dp)) {
            Text(
                pack.displayName,
                color = if (selected) Color.White else Color(0xFFEDE7FF),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.labelMedium,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .background(Color(0xFF120A33), RoundedCornerShape(8.dp))
            .border(1.dp, DuelColors.DuelGold, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick, modifier = Modifier.matchParentSize(), contentPadding = PaddingValues(0.dp)) {
            Text(
                label,
                color = DuelColors.DuelGoldGlow,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
