package org.hibernate.orm.test.jpa.broken;

import java.io.Serializable;

public interface BaseEntity extends Serializable {

    EntityType getEntityType();

}
