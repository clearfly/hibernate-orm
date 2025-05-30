/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.boot.models;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.internal.BootstrapContextImpl;
import org.hibernate.boot.internal.MetadataBuilderImpl;
import org.hibernate.boot.internal.RootMappingDefaults;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmDiscriminatorSubclassEntityType;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmHibernateMapping;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmJoinedSubclassEntityType;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmUnionSubclassEntityType;
import org.hibernate.boot.jaxb.hbm.transform.HbmXmlTransformer;
import org.hibernate.boot.jaxb.hbm.transform.UnsupportedFeatureHandling;
import org.hibernate.boot.jaxb.mapping.spi.JaxbEntityMappingsImpl;
import org.hibernate.boot.jaxb.spi.Binding;
import org.hibernate.boot.model.process.internal.ManagedResourcesImpl;
import org.hibernate.boot.model.process.spi.ManagedResources;
import org.hibernate.boot.model.process.spi.MetadataBuildingProcess;
import org.hibernate.boot.models.internal.ClassLoaderServiceLoading;
import org.hibernate.boot.models.internal.DomainModelCategorizationCollector;
import org.hibernate.boot.models.internal.GlobalRegistrationsImpl;
import org.hibernate.boot.models.internal.ModelsHelper;
import org.hibernate.boot.models.xml.internal.PersistenceUnitMetadataImpl;
import org.hibernate.boot.models.xml.spi.XmlPreProcessingResult;
import org.hibernate.boot.models.xml.spi.XmlPreProcessor;
import org.hibernate.boot.models.xml.spi.XmlProcessingResult;
import org.hibernate.boot.models.xml.spi.XmlProcessor;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.boot.registry.classloading.spi.ClassLoadingException;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.boot.spi.MetadataBuildingOptions;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.jandex.internal.JandexIndexerHelper;
import org.hibernate.models.jandex.internal.JandexModelsContextImpl;
import org.hibernate.models.spi.ClassDetailsRegistry;
import org.hibernate.models.spi.ClassLoading;
import org.hibernate.models.spi.ModelsContext;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.hibernate.boot.jaxb.hbm.transform.TransformationHelper.determineEntityName;
import static org.hibernate.boot.models.internal.OrmAnnotationHelper.forEachOrmAnnotation;
import static org.hibernate.cfg.MappingSettings.TRANSFORM_HBM_XML;
import static org.hibernate.cfg.MappingSettings.TRANSFORM_HBM_XML_FEATURE_HANDLING;
import static org.hibernate.engine.config.spi.StandardConverters.BOOLEAN;
import static org.hibernate.internal.util.collections.CollectionHelper.mutableJoin;
import static org.hibernate.models.internal.BaseLineJavaTypes.forEachJavaType;
import static org.hibernate.models.internal.SimpleClassLoading.SIMPLE_CLASS_LOADING;

/**
 * @author Steve Ebersole
 */
public class SourceModelTestHelper {

	public static ModelsContext createBuildingContext(Class<?>... modelClasses) {
		return createBuildingContext( SIMPLE_CLASS_LOADING, modelClasses );
	}

	public static ModelsContext createBuildingContext(ClassLoading classLoadingAccess, Class<?>... modelClasses) {
		final Index jandexIndex = buildJandexIndex( classLoadingAccess, modelClasses );
		return createBuildingContext( jandexIndex, modelClasses );
	}

	public static ModelsContext createBuildingContext(Index jandexIndex, Class<?>... modelClasses) {
		return createBuildingContext( jandexIndex, SIMPLE_CLASS_LOADING, modelClasses );
	}

	public static ModelsContext createBuildingContext(
			Index jandexIndex,
			ClassLoading classLoadingAccess,
			Class<?>... modelClasses) {
		final ModelsContext ctx;

		if ( jandexIndex == null ) {
			ctx = new BasicModelsContextImpl(
					classLoadingAccess,
					false,
					(contributions, buildingContext1) -> forEachOrmAnnotation( contributions::registerAnnotation )
			);
		}
		else {
			ctx = new JandexModelsContextImpl(
					jandexIndex,
					false,
					classLoadingAccess,
					(contributions, buildingContext1) -> forEachOrmAnnotation( contributions::registerAnnotation )
			);

			for ( ClassInfo knownClass : jandexIndex.getKnownClasses() ) {
				ctx.getClassDetailsRegistry().resolveClassDetails( knownClass.name().toString() );

				if ( knownClass.isAnnotation() ) {
					final Class<? extends Annotation> annotationClass = classLoadingAccess.classForName( knownClass.name().toString() );
					ctx.getAnnotationDescriptorRegistry().getDescriptor( annotationClass );
				}
			}
		}

		for ( Class<?> modelClass : modelClasses ) {
			ctx.getClassDetailsRegistry().resolveClassDetails( modelClass.getName() );
		}

		return ctx;
	}

	public static Index buildJandexIndex(Class<?>... modelClasses) {
		return buildJandexIndex( SIMPLE_CLASS_LOADING, modelClasses );
	}

	public static Index buildJandexIndex(ClassLoading classLoadingAccess, Class<?>... modelClasses) {
		final Indexer indexer = new Indexer();
		forEachJavaType( (javaType) -> JandexIndexerHelper.apply( javaType, indexer, classLoadingAccess ) );
		forEachOrmAnnotation( (descriptor) -> JandexIndexerHelper.apply( descriptor.getAnnotationType(), indexer, classLoadingAccess ) );

		for ( Class<?> modelClass : modelClasses ) {
			try {
				indexer.indexClass( modelClass );
			}
			catch (IOException e) {
				throw new RuntimeException( e );
			}
		}

		return indexer.complete();
	}

	public static ModelsContext createBuildingContext(
			ManagedResources managedResources,
			StandardServiceRegistry serviceRegistry) {
		final MetadataBuilderImpl.MetadataBuildingOptionsImpl metadataBuildingOptions =
				new MetadataBuilderImpl.MetadataBuildingOptionsImpl( serviceRegistry );
		final BootstrapContextImpl bootstrapContext =
				new BootstrapContextImpl( serviceRegistry, metadataBuildingOptions );
		metadataBuildingOptions.setBootstrapContext( bootstrapContext );
		return createBuildingContext(
				managedResources,
				false,
				metadataBuildingOptions,
				bootstrapContext
		);
	}

	public static ModelsContext createBuildingContext(
			ManagedResources managedResources,
			Index jandexIndex,
			StandardServiceRegistry serviceRegistry) {
		final MetadataBuilderImpl.MetadataBuildingOptionsImpl metadataBuildingOptions =
				new MetadataBuilderImpl.MetadataBuildingOptionsImpl( serviceRegistry );
		final BootstrapContextTesting bootstrapContext =
				new BootstrapContextTesting( jandexIndex, serviceRegistry, metadataBuildingOptions );
		metadataBuildingOptions.setBootstrapContext( bootstrapContext );
		return createBuildingContext(
				managedResources,
				false,
				metadataBuildingOptions,
				bootstrapContext
		);
	}

	public static ModelsContext createBuildingContext(
			ManagedResources managedResources,
			boolean buildJandexIndex,
			MetadataBuildingOptions metadataBuildingOptions,
			BootstrapContext bootstrapContext) {
		MetadataImplementor domainModel = MetadataBuildingProcess.complete(
				managedResources,
				bootstrapContext,
				metadataBuildingOptions
		);

		final ConfigurationService configurationService = bootstrapContext.getConfigurationService();
		final boolean doTransformation =
				configurationService.getSetting( TRANSFORM_HBM_XML, BOOLEAN, false );
		if ( doTransformation ) {
			final var xmlMappingBindings = managedResources.getXmlMappingBindings();

			final List<Binding<JaxbEntityMappingsImpl>> mappingXmlBindings = new ArrayList<>();
			final List<Binding<JaxbHbmHibernateMapping>> hbmXmlBindings = new ArrayList<>();

			xmlMappingBindings.forEach( (binding) -> {
				if ( binding.getRoot() instanceof JaxbEntityMappingsImpl ) {
					//noinspection unchecked
					mappingXmlBindings.add( (Binding<JaxbEntityMappingsImpl>) binding );
				}
				else {
					//noinspection unchecked
					hbmXmlBindings.add( (Binding<JaxbHbmHibernateMapping>) binding );
				}
			} );

			final List<Binding<JaxbEntityMappingsImpl>> transformed = HbmXmlTransformer.transform(
					hbmXmlBindings,
					domainModel,
					UnsupportedFeatureHandling.fromSetting(
							configurationService.getSettings().get( TRANSFORM_HBM_XML_FEATURE_HANDLING ),
							UnsupportedFeatureHandling.ERROR
					)
			);
			mappingXmlBindings.addAll( transformed );

			final MetadataSources newSources = new MetadataSources( bootstrapContext.getServiceRegistry() );
			if ( managedResources.getAnnotatedClassReferences() != null ) {
				managedResources.getAnnotatedClassReferences().forEach( newSources::addAnnotatedClass );
			}
			if ( managedResources.getAnnotatedClassNames() != null ) {
				managedResources.getAnnotatedClassNames().forEach( newSources::addAnnotatedClassName );
			}
			if ( managedResources.getAnnotatedPackageNames() != null ) {
				managedResources.getAnnotatedPackageNames().forEach( newSources::addPackage );
			}
			if ( managedResources.getExtraQueryImports() != null ) {
				managedResources.getExtraQueryImports().forEach( newSources::addQueryImport );
			}
			for ( Binding<JaxbEntityMappingsImpl> mappingXmlBinding : mappingXmlBindings ) {
				newSources.addMappingXmlBinding( mappingXmlBinding );
			}

			managedResources = ManagedResourcesImpl.baseline( newSources, bootstrapContext );
		}

		final ClassLoaderService classLoaderService =
				bootstrapContext.getServiceRegistry().getService( ClassLoaderService.class );
		final ClassLoaderServiceLoading classLoading = new ClassLoaderServiceLoading( classLoaderService );

		final PersistenceUnitMetadataImpl persistenceUnitMetadata = new PersistenceUnitMetadataImpl();

		final XmlPreProcessingResult xmlPreProcessingResult =
				XmlPreProcessor.preProcessXmlResources( managedResources, persistenceUnitMetadata );

		final List<String> allKnownClassNames = mutableJoin(
				managedResources.getAnnotatedClassReferences().stream().map( Class::getName ).toList(),
				managedResources.getAnnotatedClassNames(),
				xmlPreProcessingResult.getMappedClasses()
		);

		managedResources.getAnnotatedPackageNames().forEach( (packageName) -> {
			try {
				final Class<?> packageInfoClass = classLoading.classForName( packageName + ".package-info" );
				allKnownClassNames.add( packageInfoClass.getName() );
			}
			catch (ClassLoadingException classLoadingException) {
				// no package-info, so there can be no annotations... just skip it
			}
		} );
		managedResources.getAnnotatedClassReferences().forEach( (clazz) -> allKnownClassNames.add( clazz.getName() ) );

		final IndexView jandexIndex = buildJandexIndex
				? buildJandexIndex( classLoading, allKnownClassNames )
				: null;

		final ModelsContext ModelsContext =
				createModelsContext( jandexIndex, classLoading );

		final RootMappingDefaults rootMappingDefaults =
				new RootMappingDefaults( metadataBuildingOptions.getMappingDefaults(), persistenceUnitMetadata );

		final GlobalRegistrationsImpl globalRegistrations =
				new GlobalRegistrationsImpl( ModelsContext, bootstrapContext );
		final DomainModelCategorizationCollector modelCategorizationCollector = new DomainModelCategorizationCollector(
				globalRegistrations,
				ModelsContext
		);

		final XmlProcessingResult xmlProcessingResult = XmlProcessor.processXml(
				xmlPreProcessingResult,
				persistenceUnitMetadata,
				modelCategorizationCollector::apply,
				ModelsContext,
				bootstrapContext,
				rootMappingDefaults
		);

		final ClassDetailsRegistry classDetailsRegistry = ModelsContext.getClassDetailsRegistry();
		allKnownClassNames.forEach( (className) ->
				modelCategorizationCollector.apply( classDetailsRegistry.resolveClassDetails( className ) ) );
		xmlPreProcessingResult.getMappedNames().forEach( (className) ->
				modelCategorizationCollector.apply( classDetailsRegistry.resolveClassDetails( className ) ) );

		// `XmlPreProcessor#preProcessXmlResources` skips hbm.xml files.
		// we want to look at them here to collect known managed-types
		managedResources.getXmlMappingBindings().forEach( (binding) -> {
			if ( binding.getRoot() instanceof JaxbHbmHibernateMapping hbmRoot ) {
				collectHbmClasses( hbmRoot, classDetailsRegistry::resolveClassDetails );
			}
		} );

		xmlProcessingResult.apply();

		return ModelsContext;
	}

	private static ModelsContext createModelsContext(
			IndexView jandexIndex, ClassLoaderServiceLoading classLoading) {
		return jandexIndex == null
				? new BasicModelsContextImpl( classLoading, false, ModelsHelper::preFillRegistries )
				: new JandexModelsContextImpl( jandexIndex, false, classLoading, ModelsHelper::preFillRegistries );
	}

	private static void collectHbmClasses(JaxbHbmHibernateMapping hbmRoot, Consumer<String> classNameConsumer) {
		// NOTE : at the moment does not collect embeddable names...

		hbmRoot.getClazz().forEach( (hbmRootEntity) -> {
			final String entityName = determineEntityName( hbmRootEntity, hbmRoot );
			classNameConsumer.accept( entityName );

			hbmRootEntity.getSubclass().forEach( (hbmSubclass) -> visitDiscriminatedSubclass(
					hbmRoot,
					hbmSubclass,
					classNameConsumer
			) );

			hbmRootEntity.getJoinedSubclass().forEach( (hbmSubclass) -> visitJoinedSubclass(
					hbmRoot,
					hbmSubclass,
					classNameConsumer
			) );

			hbmRootEntity.getUnionSubclass().forEach( (hbmSubclass) -> visitUnionSubclass(
					hbmRoot,
					hbmSubclass,
					classNameConsumer
			) );
		} );

		hbmRoot.getSubclass().forEach( (hbmSubclass) -> visitDiscriminatedSubclass(
				hbmRoot,
				hbmSubclass,
				classNameConsumer
		) );

		hbmRoot.getJoinedSubclass().forEach( (hbmSubclass) -> visitJoinedSubclass(
				hbmRoot,
				hbmSubclass,
				classNameConsumer
		) );

		hbmRoot.getUnionSubclass().forEach( (hbmSubclass) -> visitUnionSubclass(
				hbmRoot,
				hbmSubclass,
				classNameConsumer
		) );
	}

	private static void visitDiscriminatedSubclass(
			JaxbHbmHibernateMapping hbmRoot,
			JaxbHbmDiscriminatorSubclassEntityType hbmEntity,
			Consumer<String> classNameConsumer) {
		classNameConsumer.accept( determineEntityName( hbmEntity, hbmRoot ) );
		hbmEntity.getSubclass().forEach( (hbmSubclass) -> visitDiscriminatedSubclass(
				hbmRoot,
				hbmSubclass,
				classNameConsumer
		) );
	}

	private static void visitJoinedSubclass(
			JaxbHbmHibernateMapping hbmRoot,
			JaxbHbmJoinedSubclassEntityType hbmEntity,
			Consumer<String> classNameConsumer) {
		classNameConsumer.accept( determineEntityName( hbmEntity, hbmRoot ) );
		hbmEntity.getJoinedSubclass().forEach( (hbmSubclass) -> visitJoinedSubclass(
				hbmRoot,
				hbmSubclass,
				classNameConsumer
		) );
	}

	private static void visitUnionSubclass(
			JaxbHbmHibernateMapping hbmRoot,
			JaxbHbmUnionSubclassEntityType hbmEntity,
			Consumer<String> classNameConsumer) {
		classNameConsumer.accept( determineEntityName( hbmEntity, hbmRoot ) );
		hbmEntity.getUnionSubclass().forEach( (hbmSubclass) -> visitUnionSubclass(
				hbmRoot,
				hbmSubclass,
				classNameConsumer
		) );
	}

	private static IndexView buildJandexIndex(ClassLoaderServiceLoading classLoading, List<String> classNames) {
		final Indexer indexer = new Indexer();
		forEachJavaType( (javaType) -> JandexIndexerHelper.apply( javaType, indexer, classLoading ) );
		forEachOrmAnnotation( (descriptor) -> JandexIndexerHelper.apply( descriptor.getAnnotationType(), indexer, classLoading ) );

		classNames.forEach( (className) -> {
			final URL classUrl = classLoading.locateResource( className.replace( '.', '/' ) + ".class" );
			if ( classUrl == null ) {
				throw new RuntimeException( "Could not locate class file : " + className );
			}
			try (final InputStream classFileStream = classUrl.openStream() ) {
				indexer.index( classFileStream );
			}
			catch (IOException e) {
				throw new RuntimeException( e );
			}
		} );

		return indexer.complete();
	}
}
