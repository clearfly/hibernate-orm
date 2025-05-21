/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.jpa.broken;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Objects;
import java.util.stream.LongStream;
import org.hibernate.orm.test.jpa.BaseEntityManagerFunctionalTestCase;
import org.hibernate.testing.bytecode.enhancement.CustomEnhancementContext;
import org.hibernate.testing.bytecode.enhancement.extension.BytecodeEnhanced;
import static org.hibernate.testing.transaction.TransactionUtil.doInJPA;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JPAUnitTestCase extends BaseEntityManagerFunctionalTestCase {

    @Override
    public Class<?>[] getAnnotatedClasses() {
        return new Class[] { RateCenter.class, ThirdParty.class, Provider.class, VoiceGroup.class, TelephoneNumber.class };
    }

    @Before
    public void setup() {
        doInJPA(this::entityManagerFactory, em -> {

            RateCenter rateCenter = new RateCenter();
            rateCenter.setName("Springfield");
            em.persist(rateCenter);

            ThirdParty thirdParty = new ThirdParty();
            thirdParty.setName("Globex Corporation");
            em.persist(thirdParty);

            Provider provider = new Provider();
            provider.setThirdParty(thirdParty);
            em.persist(provider);
            thirdParty.setProvider(provider);

            VoiceGroup voiceGroup = new VoiceGroup();
            em.persist(voiceGroup);

            TelephoneNumber primaryNumber = new TelephoneNumber();
            primaryNumber.setNumber("4065551234");
            primaryNumber.setProvider(provider);
            primaryNumber.setVoiceGroup(voiceGroup);
            primaryNumber.setRateCenter(rateCenter);
            em.persist(primaryNumber);

            voiceGroup.setPrimaryNumber(primaryNumber);

            LongStream.rangeClosed(4065551235L, 4065551256L).forEach(value -> {
                TelephoneNumber telephoneNumber = new TelephoneNumber();
                telephoneNumber.setNumber(String.valueOf(value));
                telephoneNumber.setProvider(provider);
                telephoneNumber.setVoiceGroup(voiceGroup);
                telephoneNumber.setRateCenter(rateCenter);
                em.persist(telephoneNumber);
            });
        });
    }

    @Test
    public void testThatPasses() {
        EntityManager em = getOrCreateEntityManager();
        em.getTransaction().begin();
        VoiceGroup voiceGroup = em.find(VoiceGroup.class, 1);
        lazyLoadCheck(voiceGroup.getPrimaryNumber().getRateCenter());
        List<TelephoneNumber> numbers = currentNumbers(em, voiceGroup);
        Assert.assertEquals(23, numbers.size());
        em.getTransaction().commit();
        em.close();
    }

    private List<TelephoneNumber> currentNumbers(EntityManager em, VoiceGroup voiceGroup) {
        CriteriaQuery<TelephoneNumber> query = em.getCriteriaBuilder().createQuery(TelephoneNumber.class);
        Root<TelephoneNumber> root = query.from(TelephoneNumber.class);
        root.fetch(TelephoneNumber_.rateCenter);
        root.fetch(TelephoneNumber_.provider);
        query.where(em.getCriteriaBuilder().equal(root.get(TelephoneNumber_.voiceGroup), voiceGroup));
        return em.createQuery(query).getResultList();
    }

    private void lazyLoadCheck(BaseEntity baseEntity) {
        if (Objects.nonNull(baseEntity)) {
            baseEntity.getEntityType();
        }
    }
}
