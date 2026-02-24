/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

pluginManagement {
    includeBuild("../publishing")
}

include("gradle-tracy-plugin")
includeBuild("tracy-compiler-plugin-2.0.0")
includeBuild("tracy-compiler-plugin-2.0.20")
includeBuild("tracy-compiler-plugin-2.1.0")
includeBuild("tracy-compiler-plugin-2.1.20")
includeBuild("tracy-compiler-plugin-2.2.0")
includeBuild("tracy-compiler-plugin-2.2.20")
includeBuild("tracy-compiler-plugin-2.3.0")
