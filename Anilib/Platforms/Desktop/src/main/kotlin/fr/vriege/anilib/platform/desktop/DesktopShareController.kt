package fr.vriege.anilib.platform.desktop

import fr.vriege.anilib.platform.compose.ShareController
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

internal class DesktopShareController : ShareController {
    override fun share(title: String, text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}
