package postgres.gen;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.postgresql.util.PSQLException;

import lama.IgnoreMeException;
import lama.Query;
import lama.QueryAdapter;
import lama.Randomly;
import postgres.PostgresSchema.PostgresColumn;
import postgres.PostgresSchema.PostgresDataType;
import postgres.PostgresSchema.PostgresTable;
import postgres.PostgresVisitor;

public class PostgresAlterTableGenerator {

	private PostgresTable randomTable;
	private Randomly r;
	private boolean changesSchema;
	private static PostgresColumn randomColumn;

	private enum Action {
//		ALTER_TABLE_ADD_COLUMN, // [ COLUMN ] column data_type [ COLLATE collation ] [
		// column_constraint [ ... ] ]
		ALTER_TABLE_DROP_COLUMN, // DROP [ COLUMN ] [ IF EXISTS ] column [ RESTRICT | CASCADE ]
		ALTER_COLUMN_TYPE, // ALTER [ COLUMN ] column [ SET DATA ] TYPE data_type [ COLLATE collation ] [
							// USING expression ]
		ALTER_COLUMN_SET_DROP_DEFAULT, // ALTER [ COLUMN ] column SET DEFAULT expression and ALTER [ COLUMN ] column
										// DROP DEFAULT
		ALTER_COLUMN_SET_DROP_NULL, // ALTER [ COLUMN ] column { SET | DROP } NOT NULL
		ALTER_COLUMN_SET_STATISTICS, // ALTER [ COLUMN ] column SET STATISTICS integer
		ALTER_COLUMN_SET_ATTRIBUTE_OPTION, // ALTER [ COLUMN ] column SET ( attribute_option = value [, ... ] )
		ALTER_COLUMN_RESET_ATTRIBUTE_OPTION, // ALTER [ COLUMN ] column RESET ( attribute_option [, ... ] )
		ALTER_COLUMN_SET_STORAGE, // ALTER [ COLUMN ] column SET STORAGE { PLAIN | EXTERNAL | EXTENDED | MAIN }
		ADD_TABLE_CONSTRAINT, // ADD table_constraint [ NOT VALID ]
		ADD_TABLE_CONSTRAINT_USING_INDEX, // ADD table_constraint_using_index
		VALIDATE_CONSTRAINT, // VALIDATE CONSTRAINT constraint_name
		DISABLE_ROW_LEVEL_SECURITY, // DISABLE ROW LEVEL SECURITY
		ENABLE_ROW_LEVEL_SECURITY, // ENABLE ROW LEVEL SECURITY
		FORCE_ROW_LEVEL_SECURITY, // FORCE ROW LEVEL SECURITY
		NO_FORCE_ROW_LEVEL_SECURITY, // NO FORCE ROW LEVEL SECURITY
		CLUSTER_ON, // CLUSTER ON index_name
		SET_WITHOUT_CLUSTER, //
		SET_WITH_OIDS, //
		SET_WITHOUT_OIDS, //
		SET_LOGGED_UNLOGGED, //
		NOT_OF, //
		OWNER_TO, //
		REPLICA_IDENTITY
	}

	public PostgresAlterTableGenerator(PostgresTable randomTable, Randomly r) {
		this.randomTable = randomTable;
		this.r = r;
	}

	public static Query create(PostgresTable randomTable, Randomly r) {
		return new PostgresAlterTableGenerator(randomTable, r).generate();
	}

	private enum Attribute {
		N_DISTINCT_INHERITED("n_distinct_inherited"), N_DISTINCT("n_distinct");

		private String val;

		private Attribute(String val) {
			this.val = val;
		}
	};

	public Query generate() {
		List<String> errors = new ArrayList<>();
		/* workaround for */
		errors.add("could not open relation with OID");

		errors.add("invalid input syntax for");
		StringBuilder sb = new StringBuilder();
		sb.append("ALTER TABLE ");
		if (Randomly.getBoolean()) {
			sb.append(" ONLY");
		}
		sb.append(" ");
		sb.append(randomTable.getName());
		sb.append(" ");
		int i = 0;
		List<Action> action = Randomly.nonEmptySubset(Action.values());
		if (randomTable.getColumns().size() == 1) {
			action.remove(Action.ALTER_TABLE_DROP_COLUMN);
		}
		if (randomTable.getIndexes().isEmpty()) {
			action.remove(Action.ADD_TABLE_CONSTRAINT_USING_INDEX);
			action.remove(Action.CLUSTER_ON);
		}
		if (action.isEmpty()) {
			throw new IgnoreMeException();
		}
		for (Action a : action) {
			if (i++ != 0) {
				sb.append(", ");
			}
			switch (a) {
			case ALTER_TABLE_DROP_COLUMN:
				sb.append("DROP ");
				if (Randomly.getBoolean()) {
					sb.append(" IF EXISTS ");
				}
				sb.append(randomTable.getRandomColumn().getName());
				if (Randomly.getBoolean()) {
					sb.append(" ");
					sb.append(Randomly.fromOptions("RESTRICT", "CASCADE"));
				}
				changesSchema = true;
				errors.add("does not exist");
				errors.add("cannot drop column referenced in partition key expression");
				errors.add("cannot drop column named in partition key");
				break;
			case ALTER_COLUMN_TYPE:
				alterColumn(randomTable, sb);
				if (Randomly.getBoolean()) {
					sb.append(" SET DATA");
				}
				sb.append(" TYPE ");
				PostgresDataType randomType = PostgresDataType.getRandomType();
				PostgresCommon.appendDataType(randomType, sb, false);
				// TODO [ COLLATE collation ] [ USING expression ]
				errors.add("cannot be cast automatically");
				errors.add("is an identity column");
				errors.add("identity column type must be smallint, integer, or bigint");
				errors.add("out of range");
				errors.add("cannot alter type of column named in partition key");
				errors.add("cannot alter type of column referenced in partition key expression");
				errors.add("argument of CHECK must be type boolean");
				errors.add("operator does not exist");
				errors.add("must be type");
				errors.add("You might need to add explicit type casts");
				errors.add("cannot cast type");
				changesSchema = true;
				break;
			case ALTER_COLUMN_SET_DROP_DEFAULT:
				alterColumn(randomTable, sb);
				if (Randomly.getBoolean()) {
					sb.append("DROP DEFAULT");
				} else {
					sb.append("SET DEFAULT ");
					sb.append(PostgresVisitor
							.asString(PostgresExpressionGenerator.generateExpression(r, randomColumn.getColumnType())));
					errors.add("is out of range");
					errors.add("but default expression is of type");
				}
				errors.add("is an identity column");
				break;
			case ALTER_COLUMN_SET_DROP_NULL:
				alterColumn(randomTable, sb);
				if (Randomly.getBoolean()) {
					sb.append("SET NOT NULL");
					errors.add("contains null values");
				} else {
					sb.append("DROP NOT NULL");
					errors.add("is in a primary key");
					errors.add("is an identity column");
				}
				break;
			case ALTER_COLUMN_SET_STATISTICS:
				alterColumn(randomTable, sb);
				sb.append("SET STATISTICS ");
				sb.append(r.getInteger(0, 10000));
				break;
			case ALTER_COLUMN_SET_ATTRIBUTE_OPTION:
				alterColumn(randomTable, sb);
				sb.append(" SET(");
				List<Attribute> subset = Randomly.nonEmptySubset(Attribute.values());
				int j = 0;
				for (Attribute attr : subset) {
					if (j++ != 0) {
						sb.append(", ");
					}
					sb.append(attr.val);
					sb.append("=");
					sb.append(Randomly.fromOptions(-1, -0.8, -0.5, -0.2, -0.1, -0.00001, -0.0000000001, 0, 0.000000001,
							0.0001, 0.1, 1));
				}
				sb.append(")");
				break;
			case ALTER_COLUMN_RESET_ATTRIBUTE_OPTION:
				alterColumn(randomTable, sb);
				sb.append(" RESET(");
				subset = Randomly.nonEmptySubset(Attribute.values());
				j = 0;
				for (Attribute attr : subset) {
					if (j++ != 0) {
						sb.append(", ");
					}
					sb.append(attr.val);
				}
				sb.append(")");
				break;
			case ALTER_COLUMN_SET_STORAGE:
				alterColumn(randomTable, sb);
				sb.append("SET STORAGE ");
				sb.append(Randomly.fromOptions("PLAIN", "EXTERNAL", "EXTENDED", "MAIN"));
				errors.add("can only have storage");
				errors.add("is an identity column");
				break;
			case ADD_TABLE_CONSTRAINT:
				sb.append("ADD ");
				PostgresCommon.addTableConstraint(sb, randomTable, r);
				errors.add("multiple primary keys for table");
				errors.add("could not create unique index");
				errors.add("contains null values");
				errors.add("cannot cast type");
				errors.add("unsupported PRIMARY KEY constraint with partition key definition");
				errors.add("unsupported UNIQUE constraint with partition key definition");
				errors.add("insufficient columns in UNIQUE constraint definition");
				errors.add("which is part of the partition key");
				if (Randomly.getBoolean()) {
					sb.append(" NOT VALID");
					errors.add("cannot be marked NOT VALID");
				} else {
					errors.add("is violated by some row");
				}
				break;
			case ADD_TABLE_CONSTRAINT_USING_INDEX:
				sb.append("ADD ");
//				sb.append("CONSTRAINT 'asdf' ");
				sb.append(Randomly.fromOptions("UNIQUE", "PRIMARY KEY"));
				sb.append(" USING INDEX ");
				sb.append(randomTable.getRandomIndex().getIndexName());
				errors.add("is not a unique index");
				errors.add("is already associated with a constraint");
				errors.add("Cannot create a primary key or unique constraint using such an index");
				errors.add("multiple primary keys for table");
				errors.add("appears twice in unique constraint");
				errors.add("appears twice in primary key constraint");
				errors.add("contains null values");
				errors.add("insufficient columns in PRIMARY KEY constraint definition");
				errors.add("which is part of the partition key");
				break;
			case VALIDATE_CONSTRAINT:
				sb.append("VALIDATE CONSTRAINT asdf");
				errors.add("does not exist");
				// FIXME select constraint
				break;
			case DISABLE_ROW_LEVEL_SECURITY:
				sb.append("DISABLE ROW LEVEL SECURITY");
				break;
			case ENABLE_ROW_LEVEL_SECURITY:
				sb.append("ENABLE ROW LEVEL SECURITY");
				break;
			case FORCE_ROW_LEVEL_SECURITY:
				sb.append("FORCE ROW LEVEL SECURITY");
				break;
			case NO_FORCE_ROW_LEVEL_SECURITY:
				sb.append("NO FORCE ROW LEVEL SECURITY");
				break;
			case CLUSTER_ON:
				sb.append("CLUSTER ON ");
				sb.append(randomTable.getRandomIndex().getIndexName());
				errors.add("cannot cluster on partial index");
				break;
			case SET_WITHOUT_CLUSTER:
				sb.append("SET WITHOUT CLUSTER");
				errors.add("cannot mark index clustered in partitioned table");
				break;
			case SET_WITH_OIDS:
				errors.add("is an identity column");
				sb.append("SET WITH OIDS");
				break;
			case SET_WITHOUT_OIDS:
				sb.append("SET WITHOUT OIDS");
				break;
			case SET_LOGGED_UNLOGGED:
				sb.append("SET ");
				sb.append(Randomly.fromOptions("LOGGED", "UNLOGGED"));
				errors.add("because it is temporary");
				break;
			case NOT_OF:
				errors.add("is not a typed table");
				sb.append("NOT OF");
				break;
			case OWNER_TO:
				sb.append("OWNER TO ");
				// TODO: new_owner
				sb.append(Randomly.fromOptions("CURRENT_USER", "SESSION_USER"));
				break;
			case REPLICA_IDENTITY:
				sb.append("REPLICA IDENTITY ");
				if (Randomly.getBoolean() || randomTable.getIndexes().isEmpty()) {
					sb.append(Randomly.fromOptions("DEFAULT", "FULL", "NOTHING"));
				} else {
					sb.append("USING INDEX " + randomTable.getRandomIndex().getIndexName());
					errors.add("cannot be used as replica identity");
					errors.add("cannot use non-unique index");
					errors.add("cannot use expression index");
					errors.add("cannot use partial index");
				}
				break;
			default:
				throw new AssertionError(a);
			}
		}

		return new QueryAdapter(sb.toString()) {

			@Override
			public void execute(Connection con) throws SQLException {
				try {
					super.execute(con);
				} catch (PSQLException e) {
					boolean found = false;
					for (String error : errors) {
						if (e.getMessage().contains(error)) {
							found = true;
						}
					}
					if (!found) {
						throw e;
					}
				}
			}

			@Override
			public boolean couldAffectSchema() {
				return changesSchema;
			}
		};
	}

	private static void alterColumn(PostgresTable randomTable, StringBuilder sb) {
		sb.append("ALTER ");
		randomColumn = randomTable.getRandomColumn();
		sb.append(randomColumn.getName());
		sb.append(" ");
	}

}