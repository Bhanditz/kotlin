/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.utils

import org.gradle.api.Project

fun Project.addExtendsFromRelation(extendingConfigurationName: String, extendsFromConfigurationName: String) {
    if (extendingConfigurationName != extendsFromConfigurationName) {
        project.dependencies.add(extendingConfigurationName, project.configurations.getByName(extendsFromConfigurationName))
    }
}