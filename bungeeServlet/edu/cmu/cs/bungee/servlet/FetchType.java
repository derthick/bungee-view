package edu.cmu.cs.bungee.servlet;

import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.MyResultSet.ColumnType;
import edu.cmu.cs.bungee.javaExtensions.MyResultSetColumnTypeList;

public enum FetchType {
	@NonNull PREFETCH_FACET_WITH_NAME(true, false),

	@NonNull PREFETCH_FACET(false, false),

	@NonNull PREFETCH_FACET_TYPE_WITH_NAME(true, false),

	@NonNull PREFETCH_FACET_TYPE(false, false),

	@NonNull PREFETCH_FACET_WITH_NAME_RESTRICTED_DATA(true, true),

	@NonNull PREFETCH_FACET_RESTRICTED_DATA(false, true);

	private FetchType(final boolean _isName, final boolean _isRestrictedData) {
		isName = _isName;
		isRestrictedData = _isRestrictedData;
		fields = _isName ? "n_child_facets, first_child_offset, name" : "n_child_facets, first_child_offset";
		columnTypes = _isName ? MyResultSetColumnTypeList.INT_INT_STRING : MyResultSetColumnTypeList.INT_INT;
	}

	private final boolean isName;
	private final boolean isRestrictedData;
	private final @NonNull String fields;
	private final @NonNull List<ColumnType> columnTypes;

	public boolean isName() {
		return isName;
	}

	public boolean isRestrictedData() {
		return isRestrictedData;
	}

	public @NonNull String getFields() {
		return fields;
	}

	public @NonNull List<ColumnType> getColumnTypes() {
		return columnTypes;
	}

}