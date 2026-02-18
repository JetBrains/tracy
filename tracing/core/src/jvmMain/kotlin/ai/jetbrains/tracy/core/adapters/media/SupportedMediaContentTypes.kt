/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.adapters.media

/**
 * Supported media content types for uploadable media attributes.
 *
 * Defines how media content (images, audio, documents) is encoded when traced in spans.
 * Used by [UploadableMediaContentAttributeKeys] to specify the media encoding format.
 *
 * @property type The string representation of the encoding type
 */
enum class SupportedMediaContentTypes(val type: String) {
    /** Base64-encoded data URL format (e.g., `data:image/png;base64,iVBORw0KGg...`) */
    BASE64("base64"),

    /** External URL reference (e.g., `https://example.com/image.png`) */
    URL("url"),
}
