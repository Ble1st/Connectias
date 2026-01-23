// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

// Root build file for build-logic composite build
// This file ensures that the clean task is available when build-logic is included as a composite build

// Add clean task for build-logic (required when build-logic is included as composite build)
tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
