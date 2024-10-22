package org.hibernate.orm.test.jpa.broken;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

@Entity
public class RateCenter implements Serializable {

	private Integer id;
	private int version;
	private String name;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	@Version
	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public boolean equals(Object o) {
		if ( o instanceof RateCenter rateCenter ) {
			return this == o || getId().equals( rateCenter.getId() );
		}
		else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return Objects.hashCode( id );
	}
}
