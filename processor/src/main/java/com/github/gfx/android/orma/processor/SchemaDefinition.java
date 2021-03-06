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

import com.github.gfx.android.orma.annotation.Column;
import com.github.gfx.android.orma.annotation.Getter;
import com.github.gfx.android.orma.annotation.PrimaryKey;
import com.github.gfx.android.orma.annotation.Setter;
import com.github.gfx.android.orma.annotation.Table;
import com.squareup.javapoet.ClassName;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

public class SchemaDefinition {

    final ProcessingContext context;

    final TypeElement typeElement;

    final ClassName modelClassName;

    final ClassName schemaClassName;

    final ClassName relationClassName;

    final ClassName selectorClassName;

    final ClassName updaterClassName;

    final ClassName deleterClassName;

    final String tableName;

    final String[] constraints;

    final List<ColumnDefinition> columns;

    final ColumnDefinition primaryKey;

    final ExecutableElement constructorElement; // null if it has a default constructor

    String createTableStatement = null;

    List<String> createIndexStatements = null;

    public SchemaDefinition(ProcessingContext context, TypeElement typeElement) {
        this.context = context;
        this.typeElement = typeElement;
        this.modelClassName = ClassName.get(typeElement);

        Table table = typeElement.getAnnotation(Table.class);
        this.constraints = table.constraints();
        this.schemaClassName = helperClassName(table.schemaClassName(), modelClassName, "_Schema");
        this.relationClassName = helperClassName(table.relationClassName(), modelClassName, "_Relation");
        this.selectorClassName = helperClassName(table.selectorClassName(), modelClassName, "_Selector");
        this.updaterClassName = helperClassName(table.updaterClassName(), modelClassName, "_Updater");
        this.deleterClassName = helperClassName(table.deleterClassName(), modelClassName, "_Deleter");
        this.tableName = firstNonEmptyName(table.value(), modelClassName.simpleName());

        this.columns = collectColumns(typeElement);
        this.primaryKey = findPrimaryKey(columns);
        this.constructorElement = findConstructor(context, typeElement);
    }

    /**
     * @param typeElement the target class element
     * @return null if it has the default constructor
     */
    @Nullable
    static ExecutableElement findConstructor(ProcessingContext context, TypeElement typeElement) {
        List<ExecutableElement> constructors = collectConstructors(typeElement);

        List<ExecutableElement> setterConstructors = collectSetterConstructors(constructors);

        if (setterConstructors.isEmpty()) {
            // use the default constructor
            return null;
        } else if (setterConstructors.size() != 1) {
            context.addError("Too many @Setter constructors", typeElement);
            return null;
        } else {
            return setterConstructors.get(0);
        }
    }

    static List<ExecutableElement> collectConstructors(TypeElement typeElement) {
        return typeElement.getEnclosedElements()
                .stream()
                .filter(SchemaDefinition::isConstructor)
                .map(element -> (ExecutableElement) element)
                .collect(Collectors.toList());
    }

    static List<ExecutableElement> collectSetterConstructors(List<ExecutableElement> constructors) {
        return constructors.stream()
                .filter(constructor -> constructor.getAnnotation(Setter.class) != null || constructor.getParameters()
                        .stream()
                        .anyMatch(param -> param.getAnnotation(Setter.class) != null))
                .collect(Collectors.toList());
    }

    static boolean isConstructor(Element element) {
        if (element instanceof ExecutableElement) {
            ExecutableElement method = (ExecutableElement) element;
            return method.getSimpleName().contentEquals("<init>");
        } else {
            return false;
        }
    }

    private static ClassName helperClassName(String specifiedName, ClassName modelClassName, String helperSuffix) {
        String simpleName = firstNonEmptyName(specifiedName, modelClassName.simpleName() + helperSuffix);
        return ClassName.get(modelClassName.packageName(), simpleName);
    }

    static String firstNonEmptyName(String... names) {
        for (String name : names) {
            if (name != null && !name.equals("")) {
                return name;
            }
        }
        throw new AssertionError("No non-empty string found");
    }

    static ColumnDefinition findPrimaryKey(List<ColumnDefinition> columns) {
        for (ColumnDefinition c : columns) {
            if (c.primaryKey) {
                return c;
            }
        }
        return null;
    }

    List<ColumnDefinition> collectColumns(TypeElement typeElement) {
        Map<String, ExecutableElement> getters = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, ExecutableElement> setters = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        List<VariableElement> primaryKeyElements = new ArrayList<>(); // 0 or 1 items
        List<VariableElement> columnElements = new ArrayList<>();

        typeElement.getEnclosedElements()
                .forEach(element -> {
                    if (element instanceof VariableElement) {
                        if (element.getAnnotation(PrimaryKey.class) != null) {
                            primaryKeyElements.add((VariableElement) element);
                        } else if (element.getAnnotation(Column.class) != null) {
                            columnElements.add((VariableElement) element);
                        }
                        return;
                    }

                    if (!(element instanceof ExecutableElement)) {
                        return;
                    }
                    if (isConstructor(element)) {
                        return;
                    }

                    ExecutableElement executableElement = (ExecutableElement) element;

                    Getter getter = element.getAnnotation(Getter.class);
                    Setter setter = element.getAnnotation(Setter.class);

                    getters.put(extractNameFromGetter(getter, executableElement), executableElement);
                    setters.put(extractNameFromSetter(setter, executableElement), executableElement);
                });

        // insert primaryKey as the last item in columns (see the bindArgs() generator in SchemaWriter)
        columnElements.addAll(primaryKeyElements);

        return columnElements.stream()
                .map((element) -> {
                    ColumnDefinition column = new ColumnDefinition(this, element);
                    column.initGetterAndSetter(getters.get(column.columnName), setters.get(column.columnName));
                    return column;
                })
                .collect(Collectors.toList());
    }

    private String extractNameFromGetter(Getter getter, ExecutableElement getterElement) {
        if (getter != null && !Strings.isEmpty(getter.value())) {
            return getter.value();
        } else {
            String name = getterElement.getSimpleName().toString();
            if (isBooleanType(getterElement.getReturnType())) {
                if (name.startsWith("is")) {
                    return name.substring("is".length());
                }
                // fallback
            }
            if (name.startsWith("get")) {
                return name.substring("get".length());
            } else {
                return name;
            }
        }
    }

    private String extractNameFromSetter(Setter setter, ExecutableElement setterElement) {
        if (setter != null && !Strings.isEmpty(setter.value())) {
            return setter.value();
        } else {
            String name = setterElement.getSimpleName().toString();
            if (name.startsWith("set")) {
                return name.substring("set".length());
            } else {
                return name;
            }
        }
    }

    private boolean isBooleanType(TypeMirror type) {
        return type.getKind() == TypeKind.BOOLEAN
                || context.isSameType(type, context.getTypeMirrorOf(Boolean.class));
    }

    public boolean hasDefaultConstructor() {
        return constructorElement == null;
    }

    public TypeElement getElement() {
        return typeElement;
    }

    public String getPackageName() {
        return schemaClassName.packageName();
    }

    public String getTableName() {
        return tableName;
    }

    public ClassName getModelClassName() {
        return modelClassName;
    }

    public ClassName getSchemaClassName() {
        return schemaClassName;
    }

    public ClassName getRelationClassName() {
        return relationClassName;
    }

    public ClassName getSelectorClassName() {
        return selectorClassName;
    }

    public ClassName getUpdaterClassName() {
        return updaterClassName;
    }

    public ClassName getDeleterClassName() {
        return deleterClassName;
    }

    public List<ColumnDefinition> getColumns() {
        return columns;
    }

    public List<ColumnDefinition> getColumnsWithoutAutoId() {
        return columns.stream().filter(c -> !c.autoId).collect(Collectors.toList());
    }

    public Optional<ColumnDefinition> findColumnByColumnName(String name) {
        return columns.stream().filter(column -> column.columnName.contentEquals(name)).findFirst();
    }

    public ColumnDefinition getPrimaryKey() {
        return primaryKey;
    }

    public String getPrimaryKeyName() {
        return primaryKey != null ? primaryKey.columnName : ColumnDefinition.kDefaultPrimaryKeyName;
    }

    private void buildStatements() {
        SqlGenerator sql = new SqlGenerator(context);
        createTableStatement = sql.buildCreateTableStatement(this);
        createIndexStatements = sql.buildCreateIndexStatements(this);
    }

    @NonNull
    public synchronized String getCreateTableStatement() {
        if (createIndexStatements == null) {
            buildStatements();
        }
        return createTableStatement;
    }

    @NonNull
    public List<String> getCreateIndexStatements() {
        if (createIndexStatements == null) {
            buildStatements();
        }
        return createIndexStatements;
    }

    @Override
    public String toString() {
        return getModelClassName().simpleName();
    }
}
