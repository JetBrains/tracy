package ai.jetbrains.tracy.okhttp.interceptors

/**
 * Retrieves the value of a specified field from the given instance (usually, **HTTP clients**),
 * including fields declared in superclasses.
 *
 * @param instance The object instance from which to retrieve the field value (**an HTTP client**).
 * @param fieldName The name of the field to retrieve.
 * @return The value of the specified field.
 * @throws NoSuchFieldException If the field with the specified name is not found.
 * @throws IllegalStateException If the specified field is found but its value is null.
 *
 * @see setFieldValue
 */
fun getFieldValue(instance: Any, fieldName: String): Any {
    var cls: Class<*>? = instance.javaClass
    while (cls != null) {
        try {
            val field = cls.getDeclaredField(fieldName)
            field.isAccessible = true
            return field.get(instance) ?: throw IllegalStateException("Field '$fieldName' is null")
        } catch (_: NoSuchFieldException) {
            cls = cls.superclass
        }
    }
    throw NoSuchFieldException("Field '$fieldName' not found in ${instance.javaClass.name}")
}

/**
 * Sets the value of a specified field on a given object instance (usually, **HTTP clients**).
 *
 * This method traverses the class hierarchy of the provided instance to find the
 * specified field and sets its value. If the field is not found in the class or
 * its superclasses, or if the value is `null` while the field type is primitive,
 * an exception will be thrown.
 *
 * @param instance The object instance whose field value is to be modified (**an HTTP client**).
 * @param fieldName The name of the field to be updated.
 * @param value The new value to assign to the field. Can be `null` if the field type allows it.
 * @throws IllegalArgumentException If an attempt is made to set a null value on a primitive field.
 * @throws NoSuchFieldException If the specified field is not found in the class hierarchy of the instance.
 *
 * @see getFieldValue
 */
fun setFieldValue(instance: Any, fieldName: String, value: Any?) {
    var cls: Class<*>? = instance.javaClass
    while (cls != null) {
        try {
            val field = cls.getDeclaredField(fieldName)
            field.isAccessible = true

            if (value == null && field.type.isPrimitive) {
                throw IllegalArgumentException("Cannot set primitive field '$fieldName' to null")
            }

            field.set(instance, value)
            return
        } catch (_: NoSuchFieldException) {
            cls = cls.superclass
        }
    }
    throw NoSuchFieldException("Field '$fieldName' not found in ${instance.javaClass.name}")
}
