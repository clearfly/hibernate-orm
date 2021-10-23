/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.tree.from;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.NavigablePath;
import org.hibernate.sql.ast.spi.SqlAliasBase;

/**
 * @author Steve Ebersole
 */
public class StandardTableGroup extends AbstractTableGroup {
	private final TableReference primaryTableReference;
	private final Predicate<String> tableReferenceJoinNameChecker;
	private final BiFunction<String,TableGroup,TableReferenceJoin> tableReferenceJoinCreator;
	private final boolean realTableGroup;
	private final boolean fetched;

	private List<TableReferenceJoin> tableJoins;

	public StandardTableGroup(
			boolean canUseInnerJoins,
			NavigablePath navigablePath,
			TableGroupProducer tableGroupProducer,
			String sourceAlias,
			TableReference primaryTableReference,
			SqlAliasBase sqlAliasBase,
			SessionFactoryImplementor sessionFactory) {
		super( canUseInnerJoins, navigablePath, tableGroupProducer, sourceAlias, sqlAliasBase, sessionFactory );
		this.primaryTableReference = primaryTableReference;
		this.realTableGroup = false;
		this.fetched = false;
		this.tableJoins = Collections.emptyList();
		this.tableReferenceJoinCreator = null;
		this.tableReferenceJoinNameChecker = s -> {
			for ( int i = 0; i < tableJoins.size(); i++ ) {
				if ( tableJoins.get( i ).getJoinedTableReference().getTableExpression().equals( s ) ) {
					return true;
				}
			}
			return false;
		};
	}

	public StandardTableGroup(
			boolean canUseInnerJoins,
			NavigablePath navigablePath,
			TableGroupProducer tableGroupProducer,
			String sourceAlias,
			TableReference primaryTableReference,
			boolean realTableGroup,
			SqlAliasBase sqlAliasBase,
			Predicate<String> tableReferenceJoinNameChecker,
			BiFunction<String, TableGroup, TableReferenceJoin> tableReferenceJoinCreator,
			SessionFactoryImplementor sessionFactory) {
		super( canUseInnerJoins, navigablePath, tableGroupProducer, sourceAlias, sqlAliasBase, sessionFactory );
		this.primaryTableReference = primaryTableReference;
		this.realTableGroup = realTableGroup;
		this.fetched = false;
		this.tableJoins = null;
		this.tableReferenceJoinNameChecker = tableReferenceJoinNameChecker;
		this.tableReferenceJoinCreator = tableReferenceJoinCreator;
	}

	public StandardTableGroup(
			boolean canUseInnerJoins,
			NavigablePath navigablePath,
			TableGroupProducer tableGroupProducer,
			boolean fetched,
			String sourceAlias,
			TableReference primaryTableReference,
			boolean realTableGroup,
			SqlAliasBase sqlAliasBase,
			Predicate<String> tableReferenceJoinNameChecker,
			BiFunction<String, TableGroup, TableReferenceJoin> tableReferenceJoinCreator,
			SessionFactoryImplementor sessionFactory) {
		super( canUseInnerJoins, navigablePath, tableGroupProducer, sourceAlias, sqlAliasBase, sessionFactory );
		this.primaryTableReference = primaryTableReference;
		this.realTableGroup = realTableGroup;
		this.fetched = fetched;
		this.tableJoins = null;
		this.tableReferenceJoinNameChecker = tableReferenceJoinNameChecker;
		this.tableReferenceJoinCreator = tableReferenceJoinCreator;
	}

	@Override
	public void applyAffectedTableNames(Consumer<String> nameCollector) {
		// todo (6.0) : if we implement dynamic TableReference creation, this still needs to return the expressions for all mapped tables not just the ones with a TableReference at this time
		nameCollector.accept( getPrimaryTableReference().getTableExpression() );
		for ( TableReferenceJoin tableReferenceJoin : tableJoins ) {
			nameCollector.accept( tableReferenceJoin.getJoinedTableReference().getTableExpression() );
		}
	}

	@Override
	public TableReference getPrimaryTableReference() {
		return primaryTableReference;
	}

	@Override
	public List<TableReferenceJoin> getTableReferenceJoins() {
		return tableJoins == null ? Collections.emptyList() : tableJoins;
	}

	@Override
	public boolean isRealTableGroup() {
		return realTableGroup || super.isRealTableGroup();
	}

	@Override
	public boolean isFetched() {
		return fetched;
	}

	public void addTableReferenceJoin(TableReferenceJoin join) {
		if ( tableJoins == null ) {
			tableJoins = new ArrayList<>();
		}
		tableJoins.add( join );
	}

	@Override
	public TableReference getTableReferenceInternal(
			NavigablePath navigablePath,
			String tableExpression, boolean allowFkOptimization) {
		final TableReference tableReference = primaryTableReference.getTableReference(
				navigablePath,
				tableExpression,
				allowFkOptimization
		);
		if ( tableReference != null ) {
			return tableReference;
		}

		if ( tableReferenceJoinNameChecker.test( tableExpression ) ) {
			if ( tableJoins != null ) {
				for ( int i = 0; i < tableJoins.size(); i++ ) {
					final TableReferenceJoin join = tableJoins.get( i );
					assert join != null;
					final TableReference resolveTableReference = join.getJoinedTableReference()
							.getTableReference( navigablePath, tableExpression, allowFkOptimization );
					if ( resolveTableReference != null ) {
						return resolveTableReference;
					}
				}
			}

			return potentiallyCreateTableReference( tableExpression );
		}

		for ( TableGroupJoin tableGroupJoin : getNestedTableGroupJoins() ) {
			final TableReference primaryTableReference = tableGroupJoin.getJoinedGroup().getPrimaryTableReference();
			if ( primaryTableReference.getTableReference( navigablePath, tableExpression, allowFkOptimization ) != null ) {
				return primaryTableReference;
			}
		}
		for ( TableGroupJoin tableGroupJoin : getTableGroupJoins() ) {
			final TableReference primaryTableReference = tableGroupJoin.getJoinedGroup().getPrimaryTableReference();
			if ( primaryTableReference.getTableReference( navigablePath, tableExpression, allowFkOptimization ) != null ) {
				return primaryTableReference;
			}
		}

		return null;
	}

	@SuppressWarnings("WeakerAccess")
	protected TableReference potentiallyCreateTableReference(String tableExpression) {
		final TableReferenceJoin join = tableReferenceJoinCreator.apply( tableExpression, this );
		if ( join != null ) {
			addTableReferenceJoin( join );
			return join.getJoinedTableReference();
		}
		return null;
	}
}
