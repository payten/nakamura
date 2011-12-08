This migration iterates over all of the sakai content items and renames the a property to another property.

Migration is performed by configuring ContentUUIDMigrator, with the old ID field and enabling the ContenMigrationComponent
from the SparseMapBundle, this bundle will not do anything until its used by the ContentMigrationComponent.


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