This migration iterates over all of the sakai content items and renames the a property to another property.

## The .cfg file should be in place before you install the migratesparseuuid bundle.

These property are configurable with a .cfg file in:

load/org.sakaiproject.nakamura.migratesparseuuid.ContentUUIDMigrator.cfg

    migrateuuid.original.field = _id
    migrateuuid.destination.field = _sparseId
    migrateuuid.dryrun = true

To install the bundle

    mvn -f contrib/migratesparseuuid/pom.xml -Predeploy install

Original rationale for the id change the subsequent migration:

> InternalContent defines a constant for the content object's uuid field.
> Originally that field was "_id". This clashed with MongoDB since every Mongo object has 
> an _id field. Sparse tries to decouple the object id from the id that the underlying 
> storage mechanism uses. 
> 
> See {@link http://www.mongodb.org/display/DOCS/Object+IDs}
> 
> This is meant for systems running sparsemapcontent with a JDBC or Cassandra driver.
> If you don't run this after upgrading sparsemapcontent you won't be able to see any
> content in the system.