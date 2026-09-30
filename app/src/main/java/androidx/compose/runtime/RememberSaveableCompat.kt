@file:Suppress("PackageDirectoryMismatch")

package androidx.compose.runtime

import androidx.compose.runtime.saveable.rememberSaveable as composeRememberSaveable

/**
 * Compatibility import kept local to the app so MainActivity can use the concise runtime import.
 */
@Composable
inline fun <reified T : Any> rememberSaveable(noinline init: () -> T): T =
    composeRememberSaveable(init = init)
