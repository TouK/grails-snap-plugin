package pl.touk.snap

import grails.artefact.Enhanced
import grails.test.MockUtils
import grails.test.mixin.domain.DomainClassUnitTestMixin
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin
import org.codehaus.groovy.grails.plugins.web.ControllersGrailsPlugin
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.events.AutoTimestampEventListener
import org.grails.datastore.gorm.events.DomainEventListener
import org.grails.datastore.gorm.validation.constraints.UniqueConstraintFactory
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager
import org.junit.After
import org.junit.BeforeClass
import org.springframework.validation.Validator
import org.codehaus.groovy.grails.validation.*
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * <p>A mixin that can be applied to JUnit or Spock tests to add testing support
 * to a users test classes. It shares Grails application context across test suite and
 * it cleans saved domain instances after each test.</p>
 *
 * <p>It has major drawback - it has to be annotated first, before
 *  {@link grails.test.mixin.TestFor} annotation.</p>
 *
 * @author Tomasz Kalkosiński
 * @since 2.0
 */
class SharedApplicationUnitTestMixin extends DomainClassUnitTestMixin {

    static long MOCK_DOMAIN_COUNTER = 0
    static long MOCK_DOMAIN_TIMER = 0

    protected static Log log = LogFactory.getLog(SharedApplicationUnitTestMixin.class);

    @BeforeClass
    static void initializeDatastoreImplementation() {
        if (simpleDatastore != null) {
            return
        }

        ClassPropertyFetcher.clearCache()
        if (applicationContext == null) {
            super.initGrailsApplication()
        }

        simpleDatastore = new SimpleMapDatastore(applicationContext)
        transactionManager = new DatastoreTransactionManager(datastore: simpleDatastore)
        applicationContext.addApplicationListener new DomainEventListener(simpleDatastore)
        applicationContext.addApplicationListener new AutoTimestampEventListener(simpleDatastore)
        ConstrainedProperty.registerNewConstraint("unique", new UniqueConstraintFactory(simpleDatastore))

        defineBeans {
            "${ConstraintsEvaluator.BEAN_NAME}"(ConstraintsEvaluatorFactoryBean) {
                defaultConstraints = DomainClassGrailsPlugin.getDefaultConstraints(grailsApplication.config)
            }
        }
    }

    @After
    void resetGrailsApplication() {
        MockUtils.TEST_INSTANCES.clear()
    }

    /**
     * A copy-paste from DomainClassUnitTestMixin#mockDomain but static
     * and it measures total time to log.
     */
    static def staticMockDomain(Class domainClassToMock, List domains = []) {
        long start = System.currentTimeMillis()
        ConstraintEvalUtils.clearDefaultConstraints()
        grailsApplication.getArtefactHandler(DomainClassArtefactHandler.TYPE).setGrailsApplication(grailsApplication)
        GrailsDomainClass domain = grailsApplication.addArtefact(DomainClassArtefactHandler.TYPE, domainClassToMock)
        PersistentEntity entity = simpleDatastore.mappingContext.addPersistentEntity(domainClassToMock)

        final mc = GrailsClassUtils.getExpandoMetaClass(domainClassToMock)

        ControllersGrailsPlugin.enhanceDomainWithBinding(applicationContext, domain, mc)
        DomainClassGrailsPlugin.registerConstraintsProperty(mc, domain)
//        DomainClassGrailsPlugin.addRelationshipManagementMethods(domain, applicationContext)
        def validationBeanName = "${domain.fullName}Validator"
        defineBeans {
            "${domain.fullName}"(domain.clazz) { bean ->
                bean.singleton = false
                bean.autowire = "byName"
            }
            "$validationBeanName"(GrailsDomainClassValidator) { bean ->
                delegate.messageSource = ref("messageSource")
                bean.lazyInit = true
                domainClass = domain
                delegate.grailsApplication = grailsApplication
            }
        }

        def validator = applicationContext.getBean(validationBeanName, Validator.class)
        simpleDatastore.mappingContext.addEntityValidator(entity, validator)

        def enhancer = new GormEnhancer(simpleDatastore, transactionManager)
        if (domainClassToMock.getAnnotation(Enhanced) != null) {
            enhancer.enhance(entity, true)
        }
        else {
            enhancer.enhance(entity)
        }

        MOCK_DOMAIN_TIMER += System.currentTimeMillis() - start
        MOCK_DOMAIN_COUNTER += 1
        log.debug("Mocked $MOCK_DOMAIN_COUNTER class with $MOCK_DOMAIN_TIMER total miliseconds")

        if (domains) {
            for (obj in domains) {
                if (obj instanceof Map) {
                    domainClassToMock.newInstance(obj).save()
                }
                else if (entity.isInstance(obj)) {
                    obj.save()
                }
            }
        }
        else {
            return applicationContext.getBean(domain.fullName)
        }
    }
}
