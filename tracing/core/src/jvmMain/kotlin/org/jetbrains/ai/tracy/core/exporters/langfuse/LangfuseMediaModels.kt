/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.exporters.langfuse

import kotlinx.serialization.Serializable

/**
 * Information about the media content uploaded to Langfuse.
 *
 * @see uploadMediaFileToLangfuse
 */
@Serializable
internal data class LangfuseMediaUploadResponse(
    val mediaId: String,
    val contentType: String,
    val contentLength: Long,
    val url: String,
    val urlExpiry: String,
    val uploadedAt: String,
)

/**
 * Parameters needed to upload media content to Langfuse.
 *
 * @see uploadMediaFileToLangfuse
 */
internal class LangfuseMediaUploadParams(
    val traceId: String,
    val observationId: String? = null,
    /**
     *  Possible values are `input`, `output`, and `metadata`.
     *
     * See at [Langfuse API Reference](https://api.reference.langfuse.com/#tag/media/post/api/public/media.body.field).
     *
     * @throws IllegalStateException if the value is not one of the supported values.
     */
    val field: String,
    val contentType: String,
    /**
     * media file's data **encoded in the base64 format**
     */
    val data: String,
) {
    companion object {
        private val supportedFields = listOf("input", "output", "metadata")
    }

    init {
        if (field !in supportedFields) {
            error("Wrong field value: $field, supported fields: ${supportedFields.joinToString()}")
        }
    }
}

/**
 * See the schema definition [here](https://api.reference.langfuse.com/#tag/media/post/api/public/media).
 */
@Serializable
internal data class LangfuseMediaRequest(
    val traceId: String,
    val observationId: String? = null,
    /**
     * See the allowed content types [here](https://api.reference.langfuse.com/#tag/media/post/api/public/media.body.contentType).
     */
    val contentType: String,
    val contentLength: Int,
    val sha256Hash: String,
    val field: String,
)

/**
 * The response schema of the `/api/public/media` endpoint.
 *
 * See details [here](https://api.reference.langfuse.com/#tag/media/post/api/public/media).
 */
@Serializable
internal data class LangfusePresignedUploadURL(
    /**
     * The presigned upload URL. If the asset is already uploaded, this will be `null`.
     */
    val uploadUrl: String?,
    /**
     * The unique Langfuse identifier of a media record.
     */
    val mediaId: String,
)

/**
 * The request schema of the `api/public/media/{mediaId}` endpoint.
 *
 * See details [here](https://api.reference.langfuse.com/#tag/media/patch/api/public/media/{mediaId}).
 */
@Serializable
internal data class LangfuseMediaUploadDetailsRequest(
    val uploadedAt: String,
    val uploadHttpStatus: Int,
    val uploadHttpError: String? = null,
)
