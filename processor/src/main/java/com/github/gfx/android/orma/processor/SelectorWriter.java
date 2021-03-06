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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;

public class SelectorWriter extends BaseWriter {

    private final SchemaDefinition schema;

    private final ConditionQueryHelpers conditionQueryHelpers;

    public SelectorWriter(ProcessingContext context, SchemaDefinition schema) {
        super(context);
        this.schema = schema;
        this.conditionQueryHelpers = new ConditionQueryHelpers(context, schema, getTargetClassName());
    }

    ClassName getTargetClassName() {
        return schema.getSelectorClassName();
    }

    @Override
    public TypeSpec buildTypeSpec() {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(getTargetClassName().simpleName());
        classBuilder.addModifiers(Modifier.PUBLIC);
        classBuilder.superclass(Types.getSelector(schema.getModelClassName(), getTargetClassName()));

        classBuilder.addMethods(buildMethodSpecs());

        return classBuilder.build();
    }

    public List<MethodSpec> buildMethodSpecs() {
        List<MethodSpec> methodSpecs = new ArrayList<>();

        methodSpecs.add(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Types.OrmaConnection, "conn")
                .addParameter(Types.getSchema(schema.getModelClassName()), "schema")
                .addCode("super(conn, schema);\n")
                .build());

        methodSpecs.add(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Types.getOrmaConditionBase(schema.getModelClassName()), "condition")
                .addCode("super(condition);\n")
                .build());

        methodSpecs.add(MethodSpec.methodBuilder("clone")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(getTargetClassName())
                .addStatement("return new $T(this)", getTargetClassName())
                .build());

        methodSpecs.addAll(conditionQueryHelpers.buildConditionHelpers());

        schema.getColumns()
                .stream()
                .filter(this::needsOrderByHelpers)
                .flatMap(this::buildOrderByHelpers)
                .forEach(methodSpecs::add);

        return methodSpecs;
    }

    boolean needsOrderByHelpers(ColumnDefinition column) {
        return (column.indexed || (column.primaryKey && (column.autoincrement || !column.autoId)));
    }

    Stream<MethodSpec> buildOrderByHelpers(ColumnDefinition column) {
        return Stream.of(
                MethodSpec.methodBuilder("orderBy" + Strings.toUpperFirst(column.name) + "Asc")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(getTargetClassName())
                        .addStatement("return orderBy($S)", sql.quoteIdentifier(column.columnName) + " ASC")
                        .build(),
                MethodSpec.methodBuilder("orderBy" + Strings.toUpperFirst(column.name) + "Desc")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(getTargetClassName())
                        .addStatement("return orderBy($S)", sql.quoteIdentifier(column.columnName) + " DESC")
                        .build()
        );
    }
}
