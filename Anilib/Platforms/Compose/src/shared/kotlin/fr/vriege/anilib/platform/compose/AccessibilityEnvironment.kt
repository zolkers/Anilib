package fr.vriege.anilib.platform.compose

import androidx.compose.runtime.staticCompositionLocalOf
import fr.vriege.anilib.feature.settings.LanguagePack

internal val LocalReducedMotion = staticCompositionLocalOf { false }
internal val LocalLanguagePack = staticCompositionLocalOf { LanguagePack.SYSTEM }
