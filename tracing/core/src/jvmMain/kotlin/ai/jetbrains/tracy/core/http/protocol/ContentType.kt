/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.http.protocol

import okhttp3.MediaType
import java.nio.charset.Charset

/**
 * Defines the structure and behavior for representing content types.
 *
 * A content type is typically used to describe the media type of data, consisting of a `type` and `subtype` component.
 * Implementations of this interface encapsulate these components and provide a derived `mimeType` property that
 * combines the type and subtype into a standard MIME type format.
 *
 * Example usages include specifying content types for HTTP requests and responses, file handling, and data serialization formats.
 *
 * @property type Represents the primary type of the content, such as `text`, `application`, or `image`.
 * @property subtype Represents the specific subtype of the content within its primary type, such as `plain` or `json`.
 * @property mimeType Concatenates the `type` and `subtype` properties into a single MIME type string, formatted as `type/subtype`.
 *                   This provides a standardized representation of the content type.
 */
interface ContentType {
    val type: String
    val subtype: String

    /**
     * Represents the MIME type of the content, combining the type and subtype properties.
     *
     * The `mimeType` property generates a string in the format of `type/subtype`.
     * It is derived by concatenating the `type` and `subtype` properties.
     */
    val mimeType: String
        get() = "$type/$subtype"

    /**
     * Converts the current instance into its string representation.
     * It's expected to include not only the mime type but also the parameters.
     *
     * @return A string representing the current object, typically formatted based on its fields and properties.
     */
    fun asString(): String

    /**
     * Extracts a parameter value based on the given name.
     *
     * @param name The name of the parameter to be extracted.
     * @return The value associated with the given key, or `null` if no such parameter exists.
     */
    fun parameter(name: String): String?

    /**
     * Retrieves the charset associated with the content type.
     *
     * This method returns the character encoding specified as part of the
     * content type's parameters. If no charset is explicitly defined,
     * a default charset may be returned based on the implementation.
     *
     * @return The character set used to encode the content.
     */
    fun charset(): Charset?

    /**
     * Provides constants and nested objects representing specific application-related content types.
     *
     * The `Application` object serves as a namespace for defining and organizing content types
     * within the `application` primary type. This includes constant values and subtypes commonly
     * used in scenarios such as HTTP headers, request bodies, and other media type handling.
     *
     * @constructor This class cannot be instantiated.
     *              It is implemented as an object to provide a singleton-like structure.
     *
     * @property TYPE Represents the primary MIME type "application".
     *                This is a constant value used as the base type in nested content type definitions.
     */
    object Application {
        const val TYPE = "application"

        /**
         * Represents the JSON content type used in HTTP communications.
         *
         * This object implements the [ContentType] interface and defines the MIME type
         * for JSON data. It overrides the `type`, `subtype`, and `asString` methods
         * to provide specific information about the JSON content type.
         *
         * - `type`: Specifies the primary type component of the MIME type (e.g., `application`).
         * - `subtype`: Specifies the subtype component of the MIME type, which is `json`.
         * - `asString()`: Returns the full MIME type as a string (e.g., `application/json`).
         */
        object Json : ContentType {
            override val type = TYPE
            override val subtype = "json"

            override fun asString(): String = mimeType
            override fun parameter(name: String) = null
            override fun charset() = null
        }
    }

    /**
     * Represents a collection of constants and nested types related to multipart content types.
     *
     * This object provides functionality and structure for working with multipart content,
     * particularly the `multipart/form-data` content type, which is often used for transmitting
     * large binary files or structured form data in HTTP requests.
     *
     * @property TYPE Represents the primary MIME type "multipart".
     */
    object MultiPart {
        const val TYPE = "multipart"

        /**
         * Represents a specific `multipart/form-data` content type.
         *
         * This object is a concrete implementation of the [ContentType] interface and is used
         * to encapsulate the `multipart/form-data` MIME type. It can be utilized in scenarios
         * where HTTP requests or responses involve submitting form data in a multipart format.
         *
         * Key characteristics:
         * - The `type` is defined by the constant value `TYPE`.
         * - The `subtype` is set to `"form-data"`.
         * - Implements a string representation of the MIME type via the `asString` method.
         */
        object FormData : ContentType {
            override val type = TYPE
            override val subtype = "form-data"

            override fun asString(): String = mimeType
            override fun parameter(name: String) = null
            override fun charset() = null
        }
    }

    /**
     * Represents a collection of text-specific constants and nested objects for content-type definitions.
     *
     * The `Text` object primarily defines constants and object structures related to handling
     * text-based content types. It includes a nested object for the event-stream content type.
     *
     * @property TYPE Represents the primary MIME type "text".
     */
    object Text {
        const val TYPE = "text"

        /**
         * Represents a specific implementation of the [ContentType] for handling server-sent events.
         *
         * This object defines the `event-stream` subtype.
         *
         * The `EventStream` object is intended to be used wherever content types need to represent
         * event streams in HTTP protocols.
         *
         * @property type Specifies the overarching type of the content. In this case, it corresponds
         *                to the constant `TYPE` from the [ContentType] class.
         * @property subtype Defines the specific subtype `event-stream`, which identifies the content type as
         *                   a server-sent event stream.
         */
        object EventStream : ContentType {
            override val type = TYPE
            override val subtype = "event-stream"

            override fun asString(): String = mimeType
            override fun parameter(name: String) = null
            override fun charset() = null
        }
    }
}

/**
 * Converts the current [MediaType] instance into a corresponding [ContentType] instance.
 *
 * This method maps the `type` and `subtype` components of the [MediaType] to a new [ContentType] object
 * and provides functionality to represent the content type as a string using the `asString` method.
 *
 * @return A [ContentType] object representing the current [MediaType].
 */
fun MediaType.toContentType(): ContentType {
    val mediaType = this
    return object : ContentType {
        override val type = mediaType.type
        override val subtype = mediaType.subtype
        override fun asString() = mediaType.toString()
        override fun parameter(name: String) = mediaType.parameter(name)
        override fun charset() = mediaType.charset()
    }
}
