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
package com.github.gfx.android.orma.internal;

import com.github.gfx.android.orma.OrmaConnection;
import com.github.gfx.android.orma.Schema;
import com.github.gfx.android.orma.adapter.TypeAdapter;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class OrmaConditionBase<Model, C extends OrmaConditionBase<Model, ?>> {

    protected final OrmaConnection conn;

    protected final Schema<Model> schema;

    protected String whereConjunction = " AND ";

    @Nullable
    protected StringBuilder whereClause;

    @Nullable
    protected ArrayList<String> bindArgs;

    public OrmaConditionBase(@NonNull OrmaConnection conn, @NonNull Schema<Model> schema) {
        this.conn = conn;
        this.schema = schema;
    }

    public OrmaConditionBase(@NonNull OrmaConditionBase<Model, ?> condition) {
        this(condition.conn, condition.schema);
        where(condition);
    }

    public OrmaConnection getConnection() {
        return conn;
    }

    public Schema<Model> getSchema() {
        return schema;
    }

    protected void appendBindArgs(@NonNull Object... args) {
        if (bindArgs == null) {
            bindArgs = new ArrayList<>(args.length);
        }

        for (Object arg : args) {
            if (arg == null) {
                bindArgs.add(null);
            } else if (arg instanceof Boolean) {
                bindArgs.add((Boolean) arg ? "1" : "0");
            } else {
                bindArgs.add(arg.toString());
            }
        }
    }

    /**
     * Builds general `where` clause with arguments.
     * e.g. {@code where("title = ? OR title = ?", a, b)}
     *
     * @param conditions SQLite's WHERE conditions.
     * @param args       Arguments bound to the {@code conditions}.
     * @return The receiver itself.
     */
    @SuppressWarnings("unchecked")
    public C where(@NonNull CharSequence conditions, @NonNull Object... args) {
        if (whereClause == null) {
            whereClause = new StringBuilder(conditions.length() + 2);
        } else {
            whereClause.append(whereConjunction);
        }

        whereClause.append('(');
        whereClause.append(conditions);
        whereClause.append(')');
        appendBindArgs(args);
        return (C) this;
    }

    @SuppressWarnings("unchecked")
    public C where(@NonNull CharSequence conditions, @NonNull Collection<?> args) {
        return where(conditions, args.toArray());
    }

    @SuppressWarnings("unchecked")
    protected <ColumnType> C in(boolean not, @NonNull String columnName, @NonNull Collection<ColumnType> values) {
        StringBuilder clause = new StringBuilder();

        clause.append(columnName);
        if (not) {
            clause.append(" NOT");
        }
        clause.append(" IN (");
        for (int i = 0, size = values.size(); i < size; i++) {
            clause.append('?');

            if ((i + 1) != size) {
                clause.append(", ");
            }
        }
        clause.append(')');

        return where(clause, values);
    }

    @SuppressWarnings("unchecked")
    protected <ColumnType> C in(boolean not, @NonNull String columnName, @NonNull Collection<ColumnType> values,
            TypeAdapter<ColumnType> typeAdapter) {
        List<String> serializedValues = new ArrayList<>(values.size());
        for (ColumnType value : values) {
            serializedValues.add(typeAdapter.serializeNullable(value));
        }
        return in(not, columnName, serializedValues);
    }


    @SuppressWarnings("unchecked")
    public C and() {
        whereConjunction = " AND ";
        return (C) this;
    }

    @SuppressWarnings("unchecked")
    public C or() {
        whereConjunction = " OR ";
        return (C) this;
    }

    @SuppressWarnings("unchecked")
    public C where(@NonNull OrmaConditionBase<Model, ?> condition) {
        if (condition.whereClause != null && condition.bindArgs != null) {
            this.where(condition.whereClause, condition.bindArgs);
        }
        return (C) this;
    }

    @Nullable
    protected String getWhereClause() {
        return whereClause != null ? whereClause.toString() : null;
    }

    @Nullable
    protected String[] getBindArgs() {
        if (bindArgs != null) {
            String[] array = new String[bindArgs.size()];
            return bindArgs.toArray(array);
        } else {
            return null;
        }
    }
}
