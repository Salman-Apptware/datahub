package com.linkedin.metadata.entity;

import com.linkedin.common.urn.Urn;
import com.linkedin.common.urn.UrnUtils;
import com.linkedin.data.template.DataTemplateUtil;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.identity.CorpUserInfo;
import com.linkedin.metadata.AspectGenerationUtils;
import com.linkedin.metadata.EbeanTestUtils;
import com.linkedin.metadata.config.PreProcessHooks;
import com.linkedin.metadata.entity.ebean.EbeanAspectDao;
import com.linkedin.metadata.entity.ebean.EbeanRetentionService;
import com.linkedin.metadata.entity.ebean.transactions.AspectsBatch;
import com.linkedin.metadata.entity.ebean.transactions.AspectsBatchItem;
import com.linkedin.metadata.event.EventProducer;
import com.linkedin.metadata.key.CorpUserKey;
import com.linkedin.metadata.models.registry.EntityRegistryException;
import com.linkedin.metadata.query.ListUrnsResult;
import com.linkedin.metadata.service.UpdateIndicesService;
import com.linkedin.metadata.utils.PegasusUtils;
import com.linkedin.mxe.SystemMetadata;
import io.ebean.EbeanServer;
import io.ebean.Transaction;
import io.ebean.TxScope;
import io.ebean.annotation.TxIsolation;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;


/**
 * A class that knows how to configure {@link EntityServiceTest} to run integration tests against a relational database.
 *
 * This class also contains all the test methods where realities of an underlying storage leak into the
 * {@link EntityService} in the form of subtle behavior differences. Ideally that should never happen, and it'd be
 * great to address captured differences.
 */
public class EbeanEntityServiceTest extends EntityServiceTest<EbeanAspectDao, EbeanRetentionService> {

  public EbeanEntityServiceTest() throws EntityRegistryException {
  }

  @BeforeMethod
  public void setupTest() {
    EbeanServer server = EbeanTestUtils.createTestServer();
    _mockProducer = mock(EventProducer.class);
    _aspectDao = new EbeanAspectDao(server);
    _aspectDao.setConnectionValidated(true);
    _mockUpdateIndicesService = mock(UpdateIndicesService.class);
    PreProcessHooks preProcessHooks = new PreProcessHooks();
    preProcessHooks.setUiEnabled(true);
    _entityService = new EntityService(_aspectDao, _mockProducer, _testEntityRegistry, true,
        _mockUpdateIndicesService, preProcessHooks);
    _retentionService = new EbeanRetentionService(_entityService, server, 1000);
    _entityService.setRetentionService(_retentionService);
  }

  /**
   * Ideally, all tests would be in the base class, so they're reused between all implementations.
   * When that's the case - test runner will ignore this class (and its base!) so we keep this dummy test
   * to make sure this class will always be discovered.
   */
  @Test
  public void obligatoryTest() throws AssertionError {
    Assert.assertTrue(true);
  }

  @Override
  @Test
  public void testIngestListLatestAspects() throws AssertionError {

    // TODO: If you're modifying this test - match your changes in sibling implementations.

    // TODO: Move this test into the base class,
    //  If you can find a way for Cassandra and relational databases to share result ordering rules.

    Urn entityUrn1 = UrnUtils.getUrn("urn:li:corpuser:test1");
    Urn entityUrn2 = UrnUtils.getUrn("urn:li:corpuser:test2");
    Urn entityUrn3 = UrnUtils.getUrn("urn:li:corpuser:test3");

    SystemMetadata metadata1 = AspectGenerationUtils.createSystemMetadata();

    String aspectName = PegasusUtils.getAspectNameFromSchema(new CorpUserInfo().schema());

    // Ingest CorpUserInfo Aspect #1
    CorpUserInfo writeAspect1 = AspectGenerationUtils.createCorpUserInfo("email@test.com");

    // Ingest CorpUserInfo Aspect #2
    CorpUserInfo writeAspect2 = AspectGenerationUtils.createCorpUserInfo("email2@test.com");

    // Ingest CorpUserInfo Aspect #3
    CorpUserInfo writeAspect3 = AspectGenerationUtils.createCorpUserInfo("email3@test.com");

    List<AspectsBatchItem> items = List.of(
            AspectsBatchItem.builder()
                    .urn(entityUrn1)
                    .aspectName(aspectName)
                    .value(writeAspect1)
                    .systemMetadata(metadata1)
                    .build(_testEntityRegistry),
            AspectsBatchItem.builder()
                    .urn(entityUrn2)
                    .aspectName(aspectName)
                    .value(writeAspect2)
                    .systemMetadata(metadata1)
                    .build(_testEntityRegistry),
            AspectsBatchItem.builder()
                    .urn(entityUrn3)
                    .aspectName(aspectName)
                    .value(writeAspect3)
                    .systemMetadata(metadata1)
                    .build(_testEntityRegistry)
    );
    _entityService.ingestAspects(AspectsBatch.builder().items(items).build(), TEST_AUDIT_STAMP, true, true);

    // List aspects
    ListResult<RecordTemplate> batch1 = _entityService.listLatestAspects(entityUrn1.getEntityType(), aspectName, 0, 2);

    assertEquals(batch1.getNextStart(), 2);
    assertEquals(batch1.getPageSize(), 2);
    assertEquals(batch1.getTotalCount(), 3);
    assertEquals(batch1.getTotalPageCount(), 2);
    assertEquals(batch1.getValues().size(), 2);
    assertTrue(DataTemplateUtil.areEqual(writeAspect1, batch1.getValues().get(0)));
    assertTrue(DataTemplateUtil.areEqual(writeAspect2, batch1.getValues().get(1)));

    ListResult<RecordTemplate> batch2 = _entityService.listLatestAspects(entityUrn1.getEntityType(), aspectName, 2, 2);
    assertEquals(batch2.getValues().size(), 1);
    assertTrue(DataTemplateUtil.areEqual(writeAspect3, batch2.getValues().get(0)));
  }

  @Override
  @Test
  public void testIngestListUrns() throws AssertionError {

    // TODO: If you're modifying this test - match your changes in sibling implementations.

    // TODO: Move this test into the base class,
    //  If you can find a way for Cassandra and relational databases to share result ordering rules.

    Urn entityUrn1 = UrnUtils.getUrn("urn:li:corpuser:test1");
    Urn entityUrn2 = UrnUtils.getUrn("urn:li:corpuser:test2");
    Urn entityUrn3 = UrnUtils.getUrn("urn:li:corpuser:test3");

    SystemMetadata metadata1 = AspectGenerationUtils.createSystemMetadata();

    String aspectName = PegasusUtils.getAspectNameFromSchema(new CorpUserKey().schema());

    // Ingest CorpUserInfo Aspect #1
    RecordTemplate writeAspect1 = AspectGenerationUtils.createCorpUserKey(entityUrn1);

    // Ingest CorpUserInfo Aspect #2
    RecordTemplate writeAspect2 = AspectGenerationUtils.createCorpUserKey(entityUrn2);

    // Ingest CorpUserInfo Aspect #3
    RecordTemplate writeAspect3 = AspectGenerationUtils.createCorpUserKey(entityUrn3);

    List<AspectsBatchItem> items = List.of(
            AspectsBatchItem.builder()
                    .urn(entityUrn1)
                    .aspectName(aspectName)
                    .value(writeAspect1)
                    .systemMetadata(metadata1)
                    .build(_testEntityRegistry),
            AspectsBatchItem.builder()
                    .urn(entityUrn2)
                    .aspectName(aspectName)
                    .value(writeAspect2)
                    .systemMetadata(metadata1)
                    .build(_testEntityRegistry),
            AspectsBatchItem.builder()
                    .urn(entityUrn3)
                    .aspectName(aspectName)
                    .value(writeAspect3)
                    .systemMetadata(metadata1)
                    .build(_testEntityRegistry)
    );
    _entityService.ingestAspects(AspectsBatch.builder().items(items).build(), TEST_AUDIT_STAMP, true, true);

    // List aspects urns
    ListUrnsResult batch1 = _entityService.listUrns(entityUrn1.getEntityType(), 0, 2);

    assertEquals(batch1.getStart().intValue(), 0);
    assertEquals(batch1.getCount().intValue(), 2);
    assertEquals(batch1.getTotal().intValue(), 3);
    assertEquals(batch1.getEntities().size(), 2);
    assertEquals(entityUrn1.toString(), batch1.getEntities().get(0).toString());
    assertEquals(entityUrn2.toString(), batch1.getEntities().get(1).toString());

    ListUrnsResult batch2 = _entityService.listUrns(entityUrn1.getEntityType(), 2, 2);

    assertEquals(batch2.getStart().intValue(), 2);
    assertEquals(batch2.getCount().intValue(), 1);
    assertEquals(batch2.getTotal().intValue(), 3);
    assertEquals(batch2.getEntities().size(), 1);
    assertEquals(entityUrn3.toString(), batch2.getEntities().get(0).toString());
  }

  @Override
  @Test
  public void testNestedTransactions() throws AssertionError {
    EbeanServer server = _aspectDao.getServer();

    try (Transaction transaction = server.beginTransaction(TxScope.requiresNew()
        .setIsolation(TxIsolation.REPEATABLE_READ))) {
      transaction.setBatchMode(true);
      // Work 1
      try (Transaction transaction2 = server.beginTransaction(TxScope.requiresNew()
          .setIsolation(TxIsolation.REPEATABLE_READ))) {
        transaction2.setBatchMode(true);
        // Work 2
        transaction2.commit();
      }
      transaction.commit();
    } catch (Exception e) {
      System.out.printf("Top level catch %s%n", e);
      e.printStackTrace();
      throw e;
    }
    System.out.println("done");
  }
}
