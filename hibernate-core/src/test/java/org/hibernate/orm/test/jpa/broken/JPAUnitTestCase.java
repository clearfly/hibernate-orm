/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.jpa.broken;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.Jpa;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Jpa(annotatedClasses = { VoiceGroup.class, ThirdParty.class, Provider.class, RateCenter.class, TelephoneNumber.class, }, proxyComplianceEnabled = true)
public class JPAUnitTestCase {

    @BeforeAll
    public void setup(EntityManagerFactoryScope scope) {
        scope.inTransaction(em -> {
            ThirdParty thirdParty = new ThirdParty();
            thirdParty.setName("Widgets");
            em.persist(thirdParty);

            Provider provider = new Provider();
            provider.setThirdParty(thirdParty);
            em.persist(provider);
            thirdParty.setProvider(provider);

            RateCenter rateCenter = new RateCenter();
            rateCenter.setName("US");
            em.persist(rateCenter);

            VoiceGroup voiceGroup = new VoiceGroup();
            em.persist(voiceGroup);

            TelephoneNumber telephoneNumber1 = new TelephoneNumber();
            telephoneNumber1.setNumber("123-456-7890");
            telephoneNumber1.setProvider(provider);
            telephoneNumber1.setVoiceGroup(voiceGroup);
            telephoneNumber1.setRateCenter(rateCenter);
            em.persist(telephoneNumber1);
            voiceGroup.setPrimaryNumber(telephoneNumber1);

            TelephoneNumber telephoneNumber2 = new TelephoneNumber();
            telephoneNumber2.setNumber("123-456-7891");
            telephoneNumber2.setProvider(provider);
            telephoneNumber2.setVoiceGroup(voiceGroup);
            telephoneNumber2.setRateCenter(rateCenter);
            em.persist(telephoneNumber2);

            TelephoneNumber telephoneNumber3 = new TelephoneNumber();
            telephoneNumber3.setNumber("123-456-7892");
            telephoneNumber3.setProvider(provider);
            telephoneNumber3.setVoiceGroup(voiceGroup);
            telephoneNumber3.setRateCenter(rateCenter);
            em.persist(telephoneNumber3);
        });
    }

    @Test
    public void shouldAssert(EntityManagerFactoryScope scope) {
        scope.inTransaction(em -> {
            VoiceGroup voiceGroup = em.find(VoiceGroup.class, 1);
            voiceGroup.getPrimaryNumber().getEntityType();
            List<TelephoneNumber> numbers = currentNumbers(em, voiceGroup);
            Assertions.assertEquals(3, numbers.size());
        });
    }

    private List<TelephoneNumber> currentNumbers(EntityManager em, VoiceGroup voiceGroup) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TelephoneNumber> cq = cb.createQuery(TelephoneNumber.class);
        Root<TelephoneNumber> root = cq.from(TelephoneNumber.class);
        root.fetch(TelephoneNumber_.rateCenter);
        root.fetch(TelephoneNumber_.provider);
        cq.where(cb.equal(root.get(TelephoneNumber_.voiceGroup), voiceGroup));
        TypedQuery<TelephoneNumber> query = em.createQuery(cq);
        return query.getResultList();
    }
}
