package fr.vriege.anilib.platform.android

import android.content.Context
import android.content.Intent
import fr.vriege.anilib.platform.compose.ShareController

internal class AndroidShareController(private val context: Context) : ShareController {
    override fun share(title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
