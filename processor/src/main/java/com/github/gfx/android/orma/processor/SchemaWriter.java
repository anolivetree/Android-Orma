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

import com.github.gfx.android.orma.annotation.OnConflict;
import com.github.gfx.android.orma.annotation.Setter;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

/**
 * {@code Schema<T>} represents how a model is connected to an SQLite table.
 */
public class SchemaWriter extends BaseWriter {

    static final String TABLE_NAME = "$TABLE_NAME";

    static final String COLUMNS = "$COLUMNS";

    static final String ESCAPED_COLUMN_NAMES = "$ESCAPED_COLUMN_NAMES";

    static final Modifier[] publicStaticFinal = {
            Modifier.PUBLIC,
            Modifier.STATIC,
            Modifier.FINAL,
    };

    private final SchemaDefinition schema;

    private final SqlGenerator sql = new SqlGenerator();

    FieldSpec primaryKey;

    public SchemaWriter(ProcessingContext context, SchemaDefinition schema) {
        super(context);
        this.schema = schema;
    }

    @Override
    public TypeSpec buildTypeSpec() {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(schema.getSchemaClassName().simpleName());
        classBuilder.addModifiers(Modifier.PUBLIC);
        classBuilder.addSuperinterface(Types.getSchema(schema.getModelClassName()));

        classBuilder.addFields(buildFieldSpecs());
        classBuilder.addMethods(buildMethodSpecs());

        return classBuilder.build();
    }

    public List<FieldSpec> buildFieldSpecs() {
        List<FieldSpec> fieldSpecs = new ArrayList<>();

        List<FieldSpec> columns = new ArrayList<>();

        schema.getColumns().forEach(columnDef -> {
            FieldSpec fieldSpec = buildColumnFieldSpec(columnDef);
            columns.add(fieldSpec);

            if (columnDef.primaryKey) {
                primaryKey = fieldSpec;
            }
        });

        if (primaryKey == null) {
            // Even if primary key is omitted, "_rowid_" is always available.
            // (WITHOUT ROWID is not supported by Orma)
            primaryKey = buildRowIdColumnFieldSpec();
            fieldSpecs.add(primaryKey);
        }

        fieldSpecs.addAll(columns);

        fieldSpecs.add(
                FieldSpec.builder(Types.String, TABLE_NAME)
                        .addModifiers(publicStaticFinal)
                        .initializer("$S", schema.tableName)
                        .build()
        );

        fieldSpecs.add(
                FieldSpec.builder(Types.ColumnList, COLUMNS)
                        .addModifiers(publicStaticFinal)
                        .initializer(buildColumnsInitializer(columns))
                        .build()
        );

        fieldSpecs.add(
                FieldSpec.builder(Types.StringArray, ESCAPED_COLUMN_NAMES)
                        .addModifiers(publicStaticFinal)
                        .initializer(buildEscapedColumnNamesInitializer())
                        .build()
        );

        return fieldSpecs;
    }

    public FieldSpec buildColumnFieldSpec(ColumnDefinition c) {
        TypeName type = c.getType();

        CodeBlock initializer;
        if (type instanceof ParameterizedTypeName) {
            initializer = CodeBlock.builder()
                    .add("new $T($S, $T.getType(new $T<$T>(){}), $L, $L, $L, $L, $L, $L)",
                            c.getColumnDefType(), c.columnName,
                            Types.ParameterizedTypes, Types.TypeHolder, type,
                            c.nullable, c.primaryKey, c.autoincrement, c.autoId, c.indexed, c.unique)
                    .build();

        } else {
            initializer = CodeBlock.builder()
                    .add("new $T($S, $T.class, $L, $L, $L, $L, $L, $L)",
                            c.getColumnDefType(), c.columnName,
                            type,
                            c.nullable, c.primaryKey, c.autoincrement, c.autoId, c.indexed, c.unique)
                    .build();
        }

        return FieldSpec.builder(c.getColumnDefType(), c.name)
                .addModifiers(publicStaticFinal)
                .initializer(initializer)
                .build();
    }

    public FieldSpec buildRowIdColumnFieldSpec() {
        String name = "_rowid_";
        TypeName columnDefType = ParameterizedTypeName.get(Types.ColumnDef, TypeName.LONG.box());

        CodeBlock initializer;
        initializer = CodeBlock.builder()
                .add("new $T($S, $T.class, $L, $L, $L, $L, $L, $L)",
                        columnDefType, name,
                        TypeName.LONG,
                        false /* nullable */, true /* primary key */, false /* autoincrement */, true /* autoId */, false,
                        false)
                .build();

        return FieldSpec.builder(columnDefType, name)
                .addModifiers(publicStaticFinal)
                .initializer(initializer)
                .build();
    }


    public CodeBlock buildColumnsInitializer(List<FieldSpec> columns) {
        CodeBlock.Builder builder = CodeBlock.builder();

        builder.add("$T.<$T>asList(\n", Types.Arrays, Types.WildcardColumnDef).indent();

        for (int i = 0; i < columns.size(); i++) {
            builder.add("$N", columns.get(i));
            if ((i + 1) != columns.size()) {
                builder.add(",\n");
            } else {
                builder.add("\n");
            }
        }

        builder.unindent().add(")");

        return builder.build();
    }

    public CodeBlock buildEscapedColumnNamesInitializer() {
        CodeBlock.Builder builder = CodeBlock.builder();

        builder.add("{\n").indent();

        List<ColumnDefinition> columns = schema.getColumns();

        for (int i = 0; i < columns.size(); i++) {
            builder.add("$S", '"' + columns.get(i).columnName + '"');
            if ((i + 1) != columns.size()) {
                builder.add(",\n");
            } else {
                builder.add("\n");
            }
        }

        builder.unindent().add("}");

        return builder.build();
    }

    public List<MethodSpec> buildMethodSpecs() {
        List<MethodSpec> methodSpecs = new ArrayList<>();

        List<AnnotationSpec> overrideAndNonNull = Arrays.asList(
                Specs.buildNonNullAnnotationSpec(),
                Specs.buildOverrideAnnotationSpec()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("getModelClass")
                        .addAnnotations(overrideAndNonNull)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(ParameterizedTypeName.get(ClassName.get(Class.class), schema.getModelClassName()))
                        .addStatement("return $T.class", schema.getModelClassName())
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("getTableName")
                        .addAnnotations(overrideAndNonNull)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(Types.String)
                        .addStatement("return $L", TABLE_NAME)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("getEscapedTableName")
                        .addAnnotations(overrideAndNonNull)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(Types.String)
                        .addStatement("return '\"' + $L + '\"'", TABLE_NAME)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("getPrimaryKey")
                        .addAnnotations(overrideAndNonNull)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(Types.WildcardColumnDef)
                        .addStatement("return $N", primaryKey)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("getColumns")
                        .addAnnotations(overrideAndNonNull)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(Types.ColumnList)
                        .addStatement("return $L", COLUMNS)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("getEscapedColumnNames")
                        .addAnnotations(overrideAndNonNull)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(Types.StringArray)
                        .addStatement("return $L", ESCAPED_COLUMN_NAMES)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("getCreateTableStatement")
                        .addAnnotations(overrideAndNonNull)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(Types.String)
                        .addStatement("return $S", sql.buildCreateTableStatement(schema))
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("getCreateIndexStatements")
                        .addAnnotations(overrideAndNonNull)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(Types.getList(Types.String))
                        .addCode(sql.buildCreateIndexStatements(schema))
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("getDropTableStatement")
                        .addAnnotations(overrideAndNonNull)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(Types.String)
                        .addStatement("return $S", sql.buildDropTableStatement(schema))
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("getInsertStatement")
                        .addAnnotations(overrideAndNonNull)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ParameterSpec.builder(int.class, "onConflictAlgorithm")
                                .addAnnotation(OnConflict.class)
                                .build())
                        .returns(Types.String)
                        .addCode(sql.buildInsertStatementCode(schema, "onConflictAlgorithm"))
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("convertToArgs")
                        .addJavadoc("Provided for debugging\n")
                        .addAnnotations(overrideAndNonNull)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(ArrayTypeName.of(TypeName.OBJECT))
                        .addParameter(
                                ParameterSpec.builder(Types.OrmaConnection, "conn")
                                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                        .build())
                        .addParameter(
                                ParameterSpec.builder(schema.getModelClassName(), "model")
                                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                        .build())
                        .addCode(buildConvertTOArgs())
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("bindArgs")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(TypeName.VOID)
                        .addParameter(
                                ParameterSpec.builder(Types.OrmaConnection, "conn")
                                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                        .build())
                        .addParameter(
                                ParameterSpec.builder(Types.SQLiteStatement, "statement")
                                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                        .build())
                        .addParameter(
                                ParameterSpec.builder(schema.getModelClassName(), "model")
                                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                        .build())
                        .addCode(buildBindArgs())
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("newModelFromCursor")
                        .addAnnotations(overrideAndNonNull)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(schema.getModelClassName())
                        .addParameter(
                                ParameterSpec.builder(Types.OrmaConnection, "conn")
                                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                        .build())
                        .addParameter(
                                ParameterSpec.builder(Types.Cursor, "cursor")
                                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                        .build())
                        .addCode(buildNewModelFromCursor())
                        .build()
        );

        return methodSpecs;
    }

    private CodeBlock buildConvertTOArgs() {
        CodeBlock.Builder builder = CodeBlock.builder();

        List<ColumnDefinition> columns = schema.getColumnsWithoutAutoId();
        builder.addStatement("$T args = new $T[$L]", ArrayTypeName.of(TypeName.OBJECT), TypeName.OBJECT, columns.size());

        for (int i = 0; i < columns.size(); i++) {
            ColumnDefinition c = columns.get(i);
            TypeName type = c.getUnboxType();
            AssociationDefinition r = c.getAssociation();

            if (type.equals(TypeName.BOOLEAN)) {
                builder.addStatement("args[$L] = model.$L ? 1 : 0", i, c.buildGetColumnExpr());
            } else if (Types.looksLikeIntegerType(type)) {
                builder.addStatement("args[$L] = model.$L", i, c.buildGetColumnExpr());
            } else if (Types.looksLikeFloatType(type)) {
                builder.addStatement("args[$L] = model.$L", i, c.buildGetColumnExpr());
            } else if (type.equals(Types.ByteArray)) {
                builder.addStatement("args[$L] = model.$L", i, c.buildGetColumnExpr());
            } else if (type.equals(Types.String)) {
                builder.addStatement("args[$L] = model.$L", i, c.buildGetColumnExpr());
            } else if (r != null && r.relationType.equals(Types.SingleAssociation)) {
                builder.addStatement("args[$L] = model.$L.getId()", i, c.buildGetColumnExpr());
            } else {
                builder.addStatement("args[$L] = $L",
                        i, c.buildSerializeExpr("conn", "model." + c.buildGetColumnExpr()));
            }
        }

        builder.addStatement("return args");
        return builder.build();
    }

    // http://developer.android.com/intl/ja/reference/android/database/sqlite/SQLiteStatement.html
    private CodeBlock buildBindArgs() {
        CodeBlock.Builder builder = CodeBlock.builder();

        List<ColumnDefinition> columns = schema.getColumnsWithoutAutoId();
        for (int i = 0; i < columns.size(); i++) {
            int n = i + 1; // bind index starts 1
            ColumnDefinition c = columns.get(i);
            TypeName type = c.getUnboxType();
            AssociationDefinition r = c.getAssociation();
            boolean nullable = !c.getType().isPrimitive() && c.nullable;

            if (nullable) {
                builder.beginControlFlow("if (model.$L != null)", c.buildGetColumnExpr());
            }

            if (type.equals(TypeName.BOOLEAN)) {
                builder.addStatement("statement.bindLong($L, model.$L ? 1 : 0)", n, c.buildGetColumnExpr());
            } else if (Types.looksLikeIntegerType(type)) {
                builder.addStatement("statement.bindLong($L, model.$L)", n, c.buildGetColumnExpr());
            } else if (Types.looksLikeFloatType(type)) {
                builder.addStatement("statement.bindDouble($L, model.$L)", n, c.buildGetColumnExpr());
            } else if (type.equals(Types.ByteArray)) {
                builder.addStatement("statement.bindBlob($L, model.$L)", n, c.buildGetColumnExpr());
            } else if (type.equals(Types.String)) {
                builder.addStatement("statement.bindString($L, model.$L)", n, c.buildGetColumnExpr());
            } else if (r != null && r.relationType.equals(Types.SingleAssociation)) {
                builder.addStatement("statement.bindLong($L, model.$L.getId())", n, c.buildGetColumnExpr());
            } else {
                builder.addStatement("statement.bindString($L, $L)",
                        n, c.buildSerializeExpr("conn", "model." + c.buildGetColumnExpr()));
            }

            if (nullable) {
                builder.endControlFlow();
                builder.beginControlFlow("else");
                builder.addStatement("statement.bindNull($L)", n);
                builder.endControlFlow();
            }
        }

        return builder.build();
    }

    private CodeBlock buildPopulateValuesIntoCursor(Function<ColumnDefinition, CodeBlock> lhsBaseGen) {
        CodeBlock.Builder builder = CodeBlock.builder();

        List<ColumnDefinition> columns = schema.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            ColumnDefinition c = columns.get(i);
            TypeName type = c.getUnboxType();

            if (Types.isDirectAssociation(context, type)) {
                ClassName className = (ClassName) type;
                String singleAssocType = "SingleAssociation<" + className.simpleName() + ">";
                context.addError("Direct association is not yet supported. Use " + singleAssocType + " instead.", c.element);
            } else if (Types.isSingleAssociation(type)) {
                AssociationDefinition r = c.getAssociation();
                CodeBlock.Builder getRhsExpr = CodeBlock.builder()
                        .add("new $T<>(conn, OrmaDatabase.schema$T, cursor.getLong($L))",
                                r.relationType, r.modelType, i);
                builder.addStatement("$L$L", lhsBaseGen.apply(c), c.buildSetColumnExpr(getRhsExpr.build()));
            } else {
                CodeBlock.Builder getRhsExpr = CodeBlock.builder();
                if (Types.needsTypeAdapter(type)) {
                    getRhsExpr.add("$L.$L($L)",
                            c.buildGetTypeAdapter("conn"),
                            c.nullable ? "deserializeNullable" : "deserialize",
                            cursorGetter(c, i));
                } else {
                    getRhsExpr.add("$L",
                            cursorGetter(c, i));
                }

                builder.addStatement("$L$L", lhsBaseGen.apply(c), c.buildSetColumnExpr(getRhsExpr.build()));
            }
        }
        return builder.build();
    }

    private CodeBlock buildNewModelFromCursor() {
        CodeBlock.Builder builder = CodeBlock.builder();
        if (schema.hasDefaultConstructor()) {
            builder.addStatement("$T model = new $T()", schema.getModelClassName(), schema.getModelClassName());
            builder.add(buildPopulateValuesIntoCursor(column -> CodeBlock.builder().add("model.").build()));
            builder.addStatement("return model");
        } else {
            if (schema.getColumns().size() != schema.constructorElement.getParameters().size()) {
                // FIXME: check the parameters more strictly
                context.addError("The @Setter constructor parameters must satisfy @Column fields", schema.constructorElement);
            }

            builder.add(buildPopulateValuesIntoCursor(
                    column -> CodeBlock.builder().add("$T ", column.getType()).build()));

            builder.addStatement("return new $T($L)", schema.getModelClassName(),
                    schema.constructorElement.getParameters()
                            .stream()
                            .map(this::extractColumnNameFromParameterElement)
                            .collect(Collectors.joining(", ")));
        }
        return builder.build();
    }

    private String extractColumnNameFromParameterElement(VariableElement parameterElement) {
        Setter setter = parameterElement.getAnnotation(Setter.class);
        if (setter != null && Strings.isEmpty(setter.value())) {
            return setter.value();
        }
        return parameterElement.getSimpleName().toString();
    }

    private String cursorGetter(ColumnDefinition column, int position) {
        TypeName type = column.getUnboxType();
        if (type.equals(TypeName.BOOLEAN)) {
            return "cursor.getLong(" + position + ") != 0";
        } else if (type.equals(TypeName.BYTE)) {
            return "(byte)cursor.getShort(" + position + ")";
        } else if (type.isPrimitive()) {
            String s = type.toString();
            return "cursor.get" + s.substring(0, 1).toUpperCase() + s.substring(1) + "(" + position + ")";
        } else if (type.equals(Types.String)) {
            return "cursor.getString(" + position + ")";
        } else if (type.equals(Types.ByteArray)) {
            return "cursor.getBlob(" + position + ")";
        } else {
            return "cursor.getString(" + position + ")"; // handled by type adapters
        }
    }
}
