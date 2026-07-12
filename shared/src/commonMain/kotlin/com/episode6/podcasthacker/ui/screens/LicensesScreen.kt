package com.episode6.podcasthacker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.episode6.podcasthacker.LicenseNotices
import com.episode6.podcasthacker.ui.util.MarkdownBlock
import com.episode6.podcasthacker.ui.util.basicMarkdownToBlocks

/** Renders THIRD_PARTY_LICENSES.md (embedded at build time as [LicenseNotices]). */
@Composable
internal fun LicensesScreen(navController: NavController) {
    ScreenScaffold(title = "License notices", navController = navController) {
        val linkColor = MaterialTheme.colorScheme.primary
        val blocks = remember(linkColor) {
            basicMarkdownToBlocks(LicenseNotices.MARKDOWN, linkColor = linkColor)
                // the document's own h1 would just repeat the scaffold title
                .filterNot { it is MarkdownBlock.Heading && it.level == 1 }
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> Text(
                        block.text,
                        style = if (block.level <= 2) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    is MarkdownBlock.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
                    is MarkdownBlock.Bullet -> Row {
                        Text("•", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(block.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
