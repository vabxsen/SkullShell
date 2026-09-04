package dev.aicli.app.ui.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/** Material icons, with filled variants for the selected navigation destination. */
object Glyphs {
    val Home = Icons.Outlined.Home
    val Folder = Icons.Outlined.Folder
    val FolderExternal = Icons.Rounded.CreateNewFolder
    val Terminal = Icons.Rounded.Terminal
    val Grid = Icons.Outlined.AutoAwesome
    val Sliders = Icons.Outlined.Settings
    val Spark = Icons.Rounded.AutoAwesome
    val Code = Icons.Rounded.Code
    val Globe = Icons.Rounded.Language
    val Keyboard = Icons.Rounded.Keyboard
    val Plus = Icons.Rounded.Add
    val Close = Icons.Rounded.Close
    val Trash = Icons.Outlined.Delete
    val Refresh = Icons.Rounded.Refresh
    val ChevronDown = Icons.Rounded.ExpandMore
    val ChevronUp = Icons.Rounded.ExpandLess
    val ChevronLeft = Icons.AutoMirrored.Rounded.ArrowBack
    val ArrowRight = Icons.AutoMirrored.Rounded.ArrowForward
    val Check = Icons.Rounded.Check
    val CheckCircle = Icons.Outlined.CheckCircle
    val Alert = Icons.Outlined.WarningAmber
    val ErrorCircle = Icons.Outlined.ErrorOutline
    val Info = Icons.Outlined.Info
    val Clock = Icons.Outlined.Schedule
    val NoSignal = Icons.Rounded.WifiOff
    val Dots = Icons.Rounded.MoreVert
    val Copy = Icons.Rounded.ContentCopy
    val Search = Icons.Rounded.Search
    val Palette = Icons.Outlined.Palette
    val Download = Icons.Rounded.Download
    val Tune = Icons.Rounded.Tune
    fun selected(icon: ImageVector): ImageVector = when (icon) {
        Home -> Icons.Rounded.Home
        Folder -> Icons.Rounded.Folder
        Grid -> Icons.Rounded.AutoAwesome
        Sliders -> Icons.Rounded.Settings
        else -> icon
    }
}
