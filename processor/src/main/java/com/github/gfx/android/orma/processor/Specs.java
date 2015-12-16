/*
 * Copyright (c) 2015 FUJI Goro (gfx).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.gfx.android.orma.processor;

import com.squareup.javapoet.AnnotationSpec;

public class Specs {

    public static AnnotationSpec buildOverrideAnnotationSpec() {
        return AnnotationSpec.builder(Override.class)
                .build();
    }

    public static AnnotationSpec buildNonNullAnnotationSpec() {
        return AnnotationSpec.builder(Types.NonNull)
                .build();
    }

    public static AnnotationSpec buildNullableAnnotationSpec() {
        return AnnotationSpec.builder(Types.Nullable)
                .build();
    }

    public static AnnotationSpec buildWorkerThreadSpec() {
        return AnnotationSpec.builder(Types.WorkerThread)
                .build();
    }
}