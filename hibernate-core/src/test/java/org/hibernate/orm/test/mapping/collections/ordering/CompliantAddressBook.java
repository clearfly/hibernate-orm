/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.mapping.collections.ordering;

import java.util.Set;

import jakarta.persistence.Basic;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OrderBy;

/**
 * @author Steve Ebersole
 */
@Entity
public class CompliantAddressBook {
	@Id
	private Integer id;
	@Basic
	private String name;
	@OrderBy( "lastName" )
	@ElementCollection
	private Set<Contact> contacts;

	private CompliantAddressBook() {
		// for Hibernate use
	}

	public CompliantAddressBook(Integer id, String name) {
		this.id = id;
		this.name = name;
	}

	public Integer getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Set<Contact> getContacts() {
		return contacts;
	}

	public void setContacts(Set<Contact> contacts) {
		this.contacts = contacts;
	}
}
