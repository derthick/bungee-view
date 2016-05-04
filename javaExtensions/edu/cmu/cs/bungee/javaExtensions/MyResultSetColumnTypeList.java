package edu.cmu.cs.bungee.javaExtensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.MyResultSet.ColumnType;

public class MyResultSetColumnTypeList {

	private static @NonNull List<ColumnType> makeColumnTypeList(final ColumnType col1, final ColumnType col2,
			final ColumnType col3, final ColumnType col4, final ColumnType col5, final ColumnType col6,
			final ColumnType col7, final ColumnType col8, final ColumnType col9, final ColumnType col10) {
		final List<ColumnType> temp = new ArrayList<>();
		addCol(col1, temp);
		addCol(col2, temp);
		addCol(col3, temp);
		addCol(col4, temp);
		addCol(col5, temp);
		addCol(col6, temp);
		addCol(col7, temp);
		addCol(col8, temp);
		addCol(col9, temp);
		addCol(col10, temp);
		final List<ColumnType> result = Collections.unmodifiableList(temp);
		assert result != null;
		return result;
	}

	private static void addCol(final ColumnType type, final List<ColumnType> temp) {
		if (type != null) {
			temp.add(type);
		}
	}

	public static final @NonNull List<ColumnType> INT = makeColumnTypeList(ColumnType.INTEGER, null, null, null, null,
			null, null, null, null, null);

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull List<ColumnType> INT_DOUBLE =
	// makeColumnTypeList(
	// ColumnType.INTEGER, ColumnType.DOUBLE, null, null, null, null,
	// null, null, null, null);

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull List<ColumnType> INT_DOUBLE_DOUBLE =
	// makeColumnTypeList(
	// ColumnType.INTEGER, ColumnType.DOUBLE, ColumnType.DOUBLE, null,
	// null, null, null, null, null, null);

	public static final @NonNull List<ColumnType> INT_INT = makeColumnTypeList(ColumnType.INTEGER, ColumnType.INTEGER,
			null, null, null, null, null, null, null, null);

	public static final @NonNull List<ColumnType> INT_INT_INT = makeColumnTypeList(ColumnType.INTEGER,
			ColumnType.INTEGER, ColumnType.INTEGER, null, null, null, null, null, null, null);

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull List<ColumnType> INT_INT_INT_INT =
	// makeColumnTypeList(
	// ColumnType.INTEGER, ColumnType.INTEGER, ColumnType.INTEGER,
	// ColumnType.INTEGER, null, null, null, null, null, null);

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull List<ColumnType>
	// INT_INT_INT_INT_INT_DOUBLE_DOUBLE =
	// makeColumnTypeList(
	// ColumnType.INTEGER, ColumnType.INTEGER, ColumnType.INTEGER,
	// ColumnType.INTEGER, ColumnType.INTEGER, ColumnType.DOUBLE,
	// ColumnType.DOUBLE, null, null, null);

	public static final @NonNull List<ColumnType> INT_INT_INT_STRING = makeColumnTypeList(ColumnType.INTEGER,
			ColumnType.INTEGER, ColumnType.INTEGER, ColumnType.STRING, null, null, null, null, null, null);

	public static final @NonNull List<ColumnType> INT_INT_STRING = makeColumnTypeList(ColumnType.INTEGER,
			ColumnType.INTEGER, ColumnType.STRING, null, null, null, null, null, null, null);

	public static final @NonNull List<ColumnType> INT_PINT = makeColumnTypeList(ColumnType.INTEGER,
			ColumnType.POSITIVE_INTEGER, null, null, null, null, null, null, null, null);

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull List<ColumnType>
	// INT_PINT_STRING_INT_INT_INT_INT_DOUBLE_PINT_PINT = makeColumnTypeList(
	// ColumnType.INTEGER, ColumnType.POSITIVE_INTEGER, ColumnType.STRING,
	// ColumnType.INTEGER, ColumnType.INTEGER, ColumnType.INTEGER,
	// ColumnType.INTEGER, ColumnType.DOUBLE, ColumnType.POSITIVE_INTEGER,
	// ColumnType.POSITIVE_INTEGER);

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull List<ColumnType> INT_SMINT =
	// makeColumnTypeList(
	// ColumnType.INTEGER, ColumnType.SORTED_MONOTONIC_INTEGER, null,
	// null, null, null, null, null, null, null);

	public static final @NonNull List<ColumnType> INT_STRING = makeColumnTypeList(ColumnType.INTEGER, ColumnType.STRING,
			null, null, null, null, null, null, null, null);

	public static final @NonNull List<ColumnType> PINT = makeColumnTypeList(ColumnType.POSITIVE_INTEGER, null, null,
			null, null, null, null, null, null, null);

	// public static final @NonNull List<ColumnType> PINT_INT_INT =
	// makeColumnTypeList(
	// ColumnType.POSITIVE_INTEGER, ColumnType.INTEGER,
	// ColumnType.INTEGER, null, null, null, null, null, null, null);

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull List<ColumnType>
	// PINT_SMINT_STRING_INT_INT_INT_INT =
	// makeColumnTypeList(
	// ColumnType.POSITIVE_INTEGER, ColumnType.SORTED_MONOTONIC_INTEGER,
	// ColumnType.STRING, ColumnType.INTEGER, ColumnType.INTEGER,
	// ColumnType.INTEGER, ColumnType.INTEGER, null, null, null);

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull List<ColumnType>
	// PINT_SMINT_STRING_INT_INT_INT_INT =
	// makeColumnTypeList(
	// ColumnType.POSITIVE_INTEGER, ColumnType.SORTED_MONOTONIC_INTEGER,
	// ColumnType.STRING, ColumnType.INTEGER, ColumnType.INTEGER,
	// ColumnType.INTEGER, ColumnType.INTEGER, null, null, null);

	public static final @NonNull List<ColumnType> PINT_PINT = makeColumnTypeList(ColumnType.POSITIVE_INTEGER,
			ColumnType.POSITIVE_INTEGER, null, null, null, null, null, null, null, null);

	public static final @NonNull List<ColumnType> PINT_PINT_PINT = makeColumnTypeList(ColumnType.POSITIVE_INTEGER,
			ColumnType.POSITIVE_INTEGER, ColumnType.POSITIVE_INTEGER, null, null, null, null, null, null, null);

	public static final @NonNull List<ColumnType> SMINT = makeColumnTypeList(ColumnType.SORTED_MONOTONIC_INTEGER, null,
			null, null, null, null, null, null, null, null);

	public static final @NonNull List<ColumnType> SMINT_INT_INT_INT_INT = makeColumnTypeList(
			ColumnType.POSITIVE_INTEGER, ColumnType.INTEGER, ColumnType.INTEGER, ColumnType.INTEGER, ColumnType.INTEGER,
			null, null, null, null, null);

	public static final @NonNull List<ColumnType> SMINT_PINT = makeColumnTypeList(ColumnType.SORTED_MONOTONIC_INTEGER,
			ColumnType.POSITIVE_INTEGER, null, null, null, null, null, null, null, null);

	public static final @NonNull List<ColumnType> SMINT_PINT_STRING_INT_INT = makeColumnTypeList(
			ColumnType.SORTED_MONOTONIC_INTEGER, ColumnType.POSITIVE_INTEGER, ColumnType.STRING, ColumnType.INTEGER,
			ColumnType.INTEGER, null, null, null, null, null);

	public static final @NonNull List<ColumnType> SMINT_STRING_IMAGE_INT_INT = makeColumnTypeList(
			ColumnType.SORTED_MONOTONIC_INTEGER, ColumnType.STRING, ColumnType.IMAGE, ColumnType.INTEGER,
			ColumnType.INTEGER, null, null, null, null, null);

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull List<ColumnType> SMINT_INT =
	// makeColumnTypeList(
	// ColumnType.SORTED_MONOTONIC_INTEGER, ColumnType.INTEGER, null,
	// null, null, null, null, null, null, null);

	public static final @NonNull List<ColumnType> SNMINT_INT_INT = makeColumnTypeList(ColumnType.SORTED_NM_INTEGER,
			ColumnType.INTEGER, ColumnType.INTEGER, null, null, null, null, null, null, null);

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull List<ColumnType> SNMINT_PINT =
	// makeColumnTypeList(
	// ColumnType.SORTED_NM_INTEGER, ColumnType.POSITIVE_INTEGER, null,
	// null, null, null, null, null, null, null);

	public static final @NonNull List<ColumnType> STRING = makeColumnTypeList(ColumnType.STRING, null, null, null, null,
			null, null, null, null, null);

	public static final @NonNull List<ColumnType> STRING_IMAGE_INT_INT = makeColumnTypeList(ColumnType.STRING,
			ColumnType.IMAGE, ColumnType.INTEGER, ColumnType.INTEGER, null, null, null, null, null, null);

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull List<ColumnType> STRING_INT =
	// makeColumnTypeList(
	// ColumnType.STRING, ColumnType.INTEGER, null, null, null, null,
	// null, null, null, null);

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull List<ColumnType> STRING_INT_INT =
	// makeColumnTypeList(
	// ColumnType.STRING, ColumnType.INTEGER, ColumnType.INTEGER, null,
	// null, null, null, null, null, null);

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull List<ColumnType> STRING_INT_INT_INT =
	// makeColumnTypeList(
	// ColumnType.STRING, ColumnType.INTEGER, ColumnType.INTEGER,
	// ColumnType.INTEGER, null, null, null, null, null, null);

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull List<ColumnType> STRING_SMINT =
	// makeColumnTypeList(
	// ColumnType.STRING, ColumnType.SORTED_MONOTONIC_INTEGER, null, null,
	// null, null, null, null, null, null);

	public static final @NonNull List<ColumnType> STRING_SMINT_SMINT_STRING_STRING = makeColumnTypeList(
			ColumnType.STRING, ColumnType.SORTED_MONOTONIC_INTEGER, ColumnType.SORTED_MONOTONIC_INTEGER,
			ColumnType.STRING, ColumnType.STRING, null, null, null, null, null);

	public static final @NonNull List<ColumnType> STRING_STRING_STRING_INT_INT_INT = makeColumnTypeList(
			ColumnType.STRING, ColumnType.STRING, ColumnType.STRING, ColumnType.INTEGER, ColumnType.INTEGER,
			ColumnType.INTEGER, null, null, null, null);

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull List<ColumnType> STRING_STRING_STRING_STRING
	// =
	// makeColumnTypeList(
	// ColumnType.STRING, ColumnType.STRING, ColumnType.STRING,
	// ColumnType.STRING, null, null, null, null, null, null);

}
