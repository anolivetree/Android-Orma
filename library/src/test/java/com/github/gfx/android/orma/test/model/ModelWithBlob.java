package com.github.gfx.android.orma.test.model;

import com.github.gfx.android.orma.annotation.Column;
import com.github.gfx.android.orma.annotation.PrimaryKey;
import com.github.gfx.android.orma.annotation.Table;

@Table
public class ModelWithBlob {

    @PrimaryKey
    public long id;

    @Column
    public byte[] blob;
}
