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
package com.github.gfx.android.orma;

import com.github.gfx.android.orma.annotation.OnConflict;
import com.github.gfx.android.orma.internal.OrmaConditionBase;

import android.support.annotation.IntRange;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Representation of a relation, or a {@code SELECT} query.
 *
 * @param <Model> An Orma model
 * @param <R>     The derived class itself. e.g {@code class Foo_Schema extends Relation<Foo, Foo_Schema>}
 */
public abstract class Relation<Model, R extends Relation<Model, ?>> extends OrmaConditionBase<Model, R>
        implements Cloneable, Iterable<Model> {

    final protected ArrayList<OrderSpec<Model>> orderSpecs = new ArrayList<>();

    public Relation(@NonNull OrmaConnection connection, @NonNull Schema<Model> schema) {
        super(connection, schema);
    }

    public Relation(@NonNull Relation<Model, ?> relation) {
        super(relation);
    }

    @SuppressWarnings("unchecked")
    public R orderBy(@NonNull OrderSpec<Model> orderSpec) {
        orderSpecs.add(0, orderSpec);
        return (R) this;
    }

    /**
     * Finds the index of the item, assuming an order specified by a set of {@code orderBy*()} methods.
     *
     * @param item The item to find
     * @return The position of the item
     */
    @SuppressWarnings("unchecked")
    public int indexOf(@NonNull Model item) {
        Selector<Model, ?> selector = selector();
        for (OrderSpec<Model> orderSpec : orderSpecs) {
            if (orderSpec.ordering.equals(OrderSpec.ASC)) {
                selector.where(orderSpec.column.getQuotedName() + " < ?", schema.getField(item, orderSpec.column));
            } else {
                selector.where(orderSpec.column.getQuotedName() + " > ?", schema.getField(item, orderSpec.column));
            }
        }
        return selector.count();
    }

    @NonNull
    public Model get(int position) {
        return selector().get(position);
    }

    @Override
    public abstract R clone();

    @SuppressWarnings("unchecked")
    public abstract Selector<Model, ?> groupBy(@NonNull String groupBy);

    @SuppressWarnings("unchecked")
    public abstract Selector<Model, ?> having(@NonNull String having, @NonNull Object... args);

    @SuppressWarnings("unchecked")
    public abstract Selector<Model, ?> limit(@IntRange(from = 1, to = Integer.MAX_VALUE) long limit);

    @SuppressWarnings("unchecked")
    public abstract Selector<Model, ?> offset(@IntRange(from = 0) long offset);

    @SuppressWarnings("unchecked")
    public abstract Selector<Model, ?> page(@IntRange(from = 1) long page);

    @SuppressWarnings("unchecked")
    public abstract Selector<Model, ?> per(@IntRange(from = 1, to = Integer.MAX_VALUE) long per);

    // Operation helpers

    @NonNull
    public abstract Selector<Model, ?> selector();

    @NonNull
    public abstract Updater<Model, ?> updater();

    @NonNull
    public abstract Deleter<Model, ?> deleter();

    @NonNull
    public Inserter<Model> inserter() {
        return inserter(OnConflict.NONE);
    }

    @NonNull
    public Inserter<Model> inserter(@OnConflict int onConflictAlgorithm) {
        return new Inserter<>(conn, schema, schema.getInsertStatement(onConflictAlgorithm));
    }

    // Iterator<Model>

    @Override
    public Iterator<Model> iterator() {
        return selector().iterator();
    }
}
