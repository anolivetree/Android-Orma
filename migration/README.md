# Orma Migration Module

`orma-migration` is a library which provides migration functionality
for `SQLiteDatabase`.

This is independent from `orma` module and available for
any Android `SQLiteDatabase` tools.

## MigrationEngine

`MigrationEngine` is an interface to provide migration.

## SchemaDiffMigration

`SchemaDiffMigration` can make SQL statements from two different schemas.

For example, given there is a table:

```sql
CREATE TABLE Book (name TEXT NOT NULL, author TEXT NOT NULL)
```

and there is another table:

```sql
CREATE TABLE Book (name TEXT NOT NULL, author TEXT NOT NULL, published_date DATE)
```

Then, `SchemaDiffMigration` generates the following statements:

```sql
CREATE __temp_Book (name TEXT NOT NULL, author TEXT NOT NULL, published_date DATE);
INSERT INTO __temp_Book (name, author) SELECT name, author FROM Book;
DROP TABLE Book;
ALTER TABLE __temp_Book RENAME TO Book;
```

Because [SQLite's ALTER TABLE](https://www.sqlite.org/lang_altertable.html)
is limited, `SchemaDiffMigration` always re-creates tables if two tables differs.

### Schema Version Control

`SchemaDiffMigration` uses a string key, or `schemaHash`, to invoke migrations.

The typical key is `OrmaDatabase.SCHEMA_HASH`.

## ManualStepMigration

`SchemaDiffMigration` can't handle **renaming**. If you want rename tables
or columns, you have to define migration steps with `ManualStepMigration`.

`ManualStepMigration` provides a way to handle hand-written migration steps,
typically used via `OrmaMigration`.

[ManualStepMigrationTest.java](src/test/java/com/github/gfx/android/orma/migration/test/ManualStepMigrationTest.java)
is an example.

### Schema Version Control

`ManualStepMigration` uses host application's `VERSION_CODE` by default.

## OrmaMigration

This is a composite class with `ManualStepMigration` and `SchemaDiffMigration`.

It invokes `ManualStepMigration` at first, and then invokes `SchemaDiffMigration`.

### How To Define Migration Steps

* Use `OrmaMigration` which has both `ManualStepMigration` and `SchemaDiffMigration` functionalities.
* `ManualStepMigration` writes steps in `ManualStepMigration.MIGRATION_STEPS_TABLE`
* `SchemaDiffMigration` writes steps in `SchemaDiffMigration.MIGRATION_STEPS_TABLE`

Here is an example to use `OrmaMigration`:

```java
int VERSION_2;
int VERSION_3;

OrmaMigration migration = OrmaMigration.builder(context)
    .schemaHashForSchemaDiffMigration(OrmaDatabase.SCHEMA_HASH)
    // register up() / down() steps
    .step(VERSION_2, new ManualStepMigration.Step() {
        @Override
        public void up(@NonNull ManualStepMigration.Helper helper) {
            helper.execSQL("... upgrading ...");
        }

        @Override
        public void down(@NonNull ManualStepMigration.Helper helper) {
            helper.execSQL("... downgrading ...");
        }
    })
    // register change(), which is used both in upgrade and downgrade
    .step(VERSION_3, new ManualStepMigration.ChangeStep() {
        @Override
        public void change(@NonNull ManualStepMigration.Helper helper) {
            Log.(TAG, helper.upgrade ? "upgrade" : "downgrade");
            helper.execSQL("DROP TABLE foo");
            helper.execSQL("DROP TABLE bar");
        }
    })
    .build();

// pass migration to OrmaDatabase.Builder#migrationEngine()
```

You can see migration logs in debug build, which are disabled in release build.

## See Also

* `SQLite.g4` is originated from [bkiers/sqlite-parser](https://github.com/bkiers/sqlite-parser)
* [CREATE TABLE - SQLite](https://www.sqlite.org/lang_createtable.html)
* [SQL::Translator::Diff in Perl](https://metacpan.org/pod/SQL::Translator::Diff)
