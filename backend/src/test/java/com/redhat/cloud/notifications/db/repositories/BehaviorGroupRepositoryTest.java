package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeBehavior;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class BehaviorGroupRepositoryTest extends DbIsolatedTest {

    @Inject
    EntityManager entityManager;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @Inject
    FeatureFlipper featureFlipper;

    @Test
    void shouldThrowExceptionWhenCreatingWithExistingDisplayNameAndSameAccount() {
        if (!featureFlipper.isEnforceBehaviorGroupNameUnicity()) {
            // The check is disabled from configuration.
            return;
        }

        Bundle bundle = resourceHelpers.createBundle();
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", bundle.getId());

        BehaviorGroup behaviorGroup2 = new BehaviorGroup();
        behaviorGroup2.setAccountId(behaviorGroup1.getAccountId());
        behaviorGroup2.setDisplayName(behaviorGroup1.getDisplayName());
        behaviorGroup2.setBundleId(bundle.getId());

        BadRequestException e = assertThrows(BadRequestException.class, () -> {
            behaviorGroupRepository.create(behaviorGroup2.getAccountId(), behaviorGroup2);
        });
        assertEquals("A behavior group with display name [" + behaviorGroup1.getDisplayName() + "] already exists", e.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenCreatingDefaultWithExistingDisplayName() {
        if (!featureFlipper.isEnforceBehaviorGroupNameUnicity()) {
            // The check is disabled from configuration.
            return;
        }

        Bundle bundle = resourceHelpers.createBundle();
        BehaviorGroup behaviorGroup1 = resourceHelpers.createDefaultBehaviorGroup("displayName", bundle.getId());

        BehaviorGroup behaviorGroup2 = new BehaviorGroup();
        behaviorGroup2.setDisplayName(behaviorGroup1.getDisplayName());
        behaviorGroup2.setBundleId(bundle.getId());

        BadRequestException e = assertThrows(BadRequestException.class, () -> {
            behaviorGroupRepository.createDefault(behaviorGroup2);
        });
        assertEquals("A behavior group with display name [" + behaviorGroup1.getDisplayName() + "] already exists", e.getMessage());
    }

    @Test
    void shouldNotThrowExceptionWhenCreatingWithExistingDisplayNameButDifferentAccount() {
        Bundle bundle = resourceHelpers.createBundle();
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", bundle.getId());

        BehaviorGroup behaviorGroup2 = new BehaviorGroup();
        behaviorGroup2.setAccountId("other-account-id");
        behaviorGroup2.setDisplayName(behaviorGroup1.getDisplayName());
        behaviorGroup2.setBundleId(bundle.getId());

        behaviorGroupRepository.create(behaviorGroup2.getAccountId(), behaviorGroup2);
    }

    @Test
    void shouldThrowExceptionWhenUpdatingToExistingDisplayNameAndSameAccount() {
        if (!featureFlipper.isEnforceBehaviorGroupNameUnicity()) {
            // The check is disabled from configuration.
            return;
        }

        Bundle bundle = resourceHelpers.createBundle("name", "displayName");
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName1", bundle.getId());
        BehaviorGroup behaviorGroup2 = resourceHelpers.createBehaviorGroup(behaviorGroup1.getAccountId(), "displayName2", bundle.getId());
        behaviorGroup2.setDisplayName(behaviorGroup1.getDisplayName());

        BadRequestException e = assertThrows(BadRequestException.class, () -> {
            behaviorGroupRepository.update(behaviorGroup2.getAccountId(), behaviorGroup2);
        });
        assertEquals("A behavior group with display name [" + behaviorGroup1.getDisplayName() + "] already exists", e.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenUpdatingDefaultToExistingDisplayName() {
        if (!featureFlipper.isEnforceBehaviorGroupNameUnicity()) {
            // The check is disabled from configuration.
            return;
        }

        Bundle bundle = resourceHelpers.createBundle("name", "displayName");
        BehaviorGroup behaviorGroup1 = resourceHelpers.createDefaultBehaviorGroup("displayName1", bundle.getId());
        BehaviorGroup behaviorGroup2 = resourceHelpers.createDefaultBehaviorGroup("displayName2", bundle.getId());
        behaviorGroup2.setDisplayName(behaviorGroup1.getDisplayName());

        BadRequestException e = assertThrows(BadRequestException.class, () -> {
            behaviorGroupRepository.updateDefault(behaviorGroup2);
        });
        assertEquals("A behavior group with display name [" + behaviorGroup1.getDisplayName() + "] already exists", e.getMessage());
    }

    @Test
    void shouldNotThrowExceptionWhenUpdatingToExistingDisplayNameButDifferentAccount() {
        Bundle bundle = resourceHelpers.createBundle("name", "displayName");
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName1", bundle.getId());
        BehaviorGroup behaviorGroup2 = resourceHelpers.createBehaviorGroup("other-account-id", "displayName2", bundle.getId());
        behaviorGroup2.setDisplayName(behaviorGroup1.getDisplayName());
        behaviorGroupRepository.update(behaviorGroup2.getAccountId(), behaviorGroup2);
    }

    @Test
    void shouldNotThrowExceptionWhenSelfUpdatingWithSameDisplayName() {
        Bundle bundle = resourceHelpers.createBundle("name", "displayName");
        BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName1", bundle.getId());
        behaviorGroupRepository.update(behaviorGroup.getAccountId(), behaviorGroup);
    }

    @Test
    void shouldNotThrowExceptionWhenSelfUpdatingDefaultWithSameDisplayName() {
        Bundle bundle = resourceHelpers.createBundle("name", "displayName");
        BehaviorGroup behaviorGroup = resourceHelpers.createDefaultBehaviorGroup("displayName1", bundle.getId());
        behaviorGroupRepository.updateDefault(behaviorGroup);
    }

    @Test
    void testCreateAndUpdateAndDeleteBehaviorGroup() {
        String newDisplayName = "newDisplayName";

        Bundle bundle = resourceHelpers.createBundle();

        // Create behavior group.
        BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", bundle.getId());
        List<BehaviorGroup> behaviorGroups = behaviorGroupRepository.findByBundleId(DEFAULT_ACCOUNT_ID, bundle.getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(behaviorGroup, behaviorGroups.get(0));
        assertEquals(behaviorGroup.getDisplayName(), behaviorGroups.get(0).getDisplayName());
        assertEquals(bundle.getId(), behaviorGroups.get(0).getBundle().getId());
        assertNotNull(bundle.getCreated());

        // Update behavior group.
        assertTrue(updateBehaviorGroup(behaviorGroup.getId(), newDisplayName));
        entityManager.clear(); // We need to clear the session L1 cache before checking the update result.

        behaviorGroups = behaviorGroupRepository.findByBundleId(DEFAULT_ACCOUNT_ID, bundle.getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(behaviorGroup.getId(), behaviorGroups.get(0).getId());
        assertEquals(newDisplayName, behaviorGroups.get(0).getDisplayName());
        assertEquals(bundle.getId(), behaviorGroups.get(0).getBundle().getId());

        // Delete behavior group.
        assertTrue(resourceHelpers.deleteBehaviorGroup(behaviorGroup.getId()));

        behaviorGroups = behaviorGroupRepository.findByBundleId(DEFAULT_ACCOUNT_ID, bundle.getId());
        assertTrue(behaviorGroups.isEmpty());
    }

    @Test
    void testCreateAndUpdateAndDeleteDefaultBehaviorGroup() {
        String newDisplayName = "newDisplayName";

        Bundle bundle = resourceHelpers.createBundle();

        // Create behavior group.
        BehaviorGroup behaviorGroup = resourceHelpers.createDefaultBehaviorGroup("displayName", bundle.getId());

        List<BehaviorGroup> behaviorGroups = behaviorGroupRepository.findByBundleId(DEFAULT_ACCOUNT_ID, bundle.getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(behaviorGroup, behaviorGroups.get(0));
        assertEquals(behaviorGroup.getDisplayName(), behaviorGroups.get(0).getDisplayName());
        assertEquals(bundle.getId(), behaviorGroups.get(0).getBundle().getId());
        assertNotNull(bundle.getCreated());

        // Update behavior group.
        assertTrue(updateDefaultBehaviorGroup(behaviorGroup.getId(), newDisplayName));
        entityManager.clear(); // We need to clear the session L1 cache before checking the update result.

        behaviorGroups = behaviorGroupRepository.findByBundleId(DEFAULT_ACCOUNT_ID, bundle.getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(behaviorGroup.getId(), behaviorGroups.get(0).getId());
        assertEquals(newDisplayName, behaviorGroups.get(0).getDisplayName());
        assertEquals(bundle.getId(), behaviorGroups.get(0).getBundle().getId());

        // Delete behavior group.
        assertTrue(resourceHelpers.deleteDefaultBehaviorGroup(behaviorGroup.getId()));

        behaviorGroups = behaviorGroupRepository.findByBundleId(DEFAULT_ACCOUNT_ID, bundle.getId());
        assertTrue(behaviorGroups.isEmpty());
    }

    @Test
    void testCreateBehaviorGroupWithNullDisplayName() {
        createBehaviorGroupWithIllegalDisplayName(null);
    }

    @Test
    void testCreateBehaviorGroupWithEmptyDisplayName() {
        createBehaviorGroupWithIllegalDisplayName("");
    }

    @Test
    void testCreateBehaviorGroupWithBlankDisplayName() {
        createBehaviorGroupWithIllegalDisplayName(" ");
    }

    @Test
    void testCreateBehaviorGroupWithNullBundleId() {
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", null);
        });
        assertTrue(Pattern.compile("property path: [a-zA-Z0-9.]+bundleId").matcher(e.getMessage()).find());
    }

    @Test
    void testCreateBehaviorGroupWithUnknownBundleId() {
        NotFoundException e = assertThrows(NotFoundException.class, () -> {
            resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", UUID.randomUUID());
        });
        assertEquals("bundle_id not found", e.getMessage());
    }

    @Test
    void testfindByBundleIdOrdering() {
        Bundle bundle = resourceHelpers.createBundle();
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName1", bundle.getId());
        BehaviorGroup behaviorGroup2 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName2", bundle.getId());
        BehaviorGroup behaviorGroup3 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName3", bundle.getId());
        List<BehaviorGroup> behaviorGroups = behaviorGroupRepository.findByBundleId(DEFAULT_ACCOUNT_ID, bundle.getId());
        assertEquals(3, behaviorGroups.size());
        // Behavior groups should be sorted on descending creation date.
        assertEquals(behaviorGroup3, behaviorGroups.get(0));
        assertEquals(behaviorGroup2, behaviorGroups.get(1));
        assertEquals(behaviorGroup1, behaviorGroups.get(2));
    }

    @Test
    void testAddAndDeleteEventTypeBehavior() {
        Bundle bundle = resourceHelpers.createBundle();
        Application app = resourceHelpers.createApplication(bundle.getId());
        EventType eventType = createEventType(app);
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "Behavior group 1", bundle.getId());
        BehaviorGroup behaviorGroup2 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "Behavior group 2", bundle.getId());
        BehaviorGroup behaviorGroup3 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "Behavior group 3", bundle.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, eventType.getId(), behaviorGroup1.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, eventType.getId(), behaviorGroup1.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, eventType.getId(), behaviorGroup1.getId(), behaviorGroup2.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, eventType.getId(), behaviorGroup2.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, eventType.getId(), behaviorGroup1.getId(), behaviorGroup2.getId(), behaviorGroup3.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, eventType.getId());
    }

    @Test
    void testAddEventTypeBehaviorWithBundleIntegrityCheckFailure() {
        /*
         * Bundle 1 objects hierarchy.
         */
        Bundle bundle1 = resourceHelpers.createBundle("bundle-1", "Bundle 1");
        Application app = resourceHelpers.createApplication(bundle1.getId());
        // 'eventType' is a child of 'bundle1'.
        EventType eventType = createEventType(app);
        // 'behaviorGroup1' is a child of 'bundle1'.
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "Behavior group 1", bundle1.getId());

        /*
         * Bundle 2 objects hierarchy.
         */
        Bundle bundle2 = resourceHelpers.createBundle("bundle-2", "Bundle 2");
        // 'behaviorGroup2' is a child of 'bundle2'.
        BehaviorGroup behaviorGroup2 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "Behavior group 2", bundle2.getId());

        // 'behaviorGroup2' should not be added to 'eventType' behaviors because it comes from a different bundle.
        BadRequestException e = assertThrows(BadRequestException.class, () -> {
            behaviorGroupRepository.updateEventTypeBehaviors(DEFAULT_ACCOUNT_ID, eventType.getId(), Set.of(behaviorGroup1.getId(), behaviorGroup2.getId()));
        });
        assertTrue(e.getMessage().startsWith("Some behavior groups can't be linked"));
    }

    @Test
    void testFindEventTypesByBehaviorGroupId() {
        Bundle bundle = resourceHelpers.createBundle();
        Application app = resourceHelpers.createApplication(bundle.getId());
        EventType eventType = createEventType(app);
        BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", bundle.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, eventType.getId(), behaviorGroup.getId());
        List<EventType> eventTypes = resourceHelpers.findEventTypesByBehaviorGroupId(behaviorGroup.getId());
        assertEquals(1, eventTypes.size());
        assertEquals(eventType.getId(), eventTypes.get(0).getId());
    }

    @Test
    void testFindBehaviorGroupsByEventTypeId() {
        Bundle bundle = resourceHelpers.createBundle();
        Application app = resourceHelpers.createApplication(bundle.getId());
        EventType eventType = createEventType(app);
        BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", bundle.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, eventType.getId(), behaviorGroup.getId());
        List<BehaviorGroup> behaviorGroups = resourceHelpers.findBehaviorGroupsByEventTypeId(eventType.getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(behaviorGroup.getId(), behaviorGroups.get(0).getId());
    }

    @Test
    void testAddAndDeleteBehaviorGroupAction() {
        Bundle bundle = resourceHelpers.createBundle();
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "Behavior group 1", bundle.getId());
        BehaviorGroup behaviorGroup2 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "Behavior group 2", bundle.getId());
        Endpoint endpoint1 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, WEBHOOK);
        Endpoint endpoint2 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, WEBHOOK);
        Endpoint endpoint3 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, WEBHOOK);

        // At the beginning of the test, endpoint1 shouldn't be linked with any behavior group.
        findBehaviorGroupsByEndpointId(endpoint1.getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, bundle.getId(), behaviorGroup1.getId(), endpoint1.getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, bundle.getId(), behaviorGroup1.getId(), endpoint1.getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, bundle.getId(), behaviorGroup1.getId(), endpoint1.getId(), endpoint2.getId());

        // Now, endpoint1 should be linked with behaviorGroup1.
        findBehaviorGroupsByEndpointId(endpoint1.getId(), behaviorGroup1.getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, bundle.getId(), behaviorGroup2.getId(), endpoint1.getId());

        // Then, endpoint1 should be linked with both behavior groups.
        findBehaviorGroupsByEndpointId(endpoint1.getId(), behaviorGroup1.getId(), behaviorGroup2.getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, bundle.getId(), behaviorGroup1.getId(), endpoint2.getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, bundle.getId(), behaviorGroup1.getId(), endpoint3.getId(), endpoint2.getId(), endpoint1.getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, bundle.getId(), behaviorGroup1.getId());

        // The link between endpoint1 and behaviorGroup1 was removed. Let's check it is still linked with behaviorGroup2.
        findBehaviorGroupsByEndpointId(endpoint1.getId(), behaviorGroup2.getId());
    }

    @Test
    void testAddMultipleEmailSubscriptionBehaviorGroupActions() {
        Bundle bundle = resourceHelpers.createBundle();
        BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", bundle.getId());
        Endpoint endpoint1 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, EMAIL_SUBSCRIPTION);
        Endpoint endpoint2 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, EMAIL_SUBSCRIPTION);
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, bundle.getId(), behaviorGroup.getId(), endpoint1.getId(), endpoint2.getId());
    }

    @Test
    void testUpdateBehaviorGroupActionsWithWrongAccountId() {
        Bundle bundle = resourceHelpers.createBundle();
        BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", bundle.getId());
        Endpoint endpoint = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, WEBHOOK);
        assertThrows(NotFoundException.class, () -> {
            updateAndCheckBehaviorGroupActions("unknownAccountId", bundle.getId(), behaviorGroup.getId(), endpoint.getId());
        });
    }

    @Transactional
    EventType createEventType(Application app) {
        EventType eventType = new EventType();
        eventType.setApplication(app);
        eventType.setApplicationId(app.getId());
        eventType.setName("name");
        eventType.setDisplayName("displayName");
        entityManager.persist(eventType);
        return eventType;
    }

    private void createBehaviorGroupWithIllegalDisplayName(String displayName) {
        Bundle bundle = resourceHelpers.createBundle();
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, displayName, bundle.getId());
        });
        assertTrue(Pattern.compile("property path: [a-zA-Z0-9.]+displayName").matcher(e.getMessage()).find());
    }

    private Boolean updateBehaviorGroup(UUID behaviorGroupId, String displayName) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setId(behaviorGroupId);
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(UUID.randomUUID()); // This should not have any effect, the bundle is not updatable.
        return resourceHelpers.updateBehaviorGroup(behaviorGroup);
    }

    private Boolean updateDefaultBehaviorGroup(UUID behaviorGroupId, String displayName) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setId(behaviorGroupId);
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(UUID.randomUUID()); // This should not have any effect, the bundle is not updatable.
        return resourceHelpers.updateDefaultBehaviorGroup(behaviorGroup);
    }

    @Transactional
    void updateAndCheckEventTypeBehaviors(String accountId, UUID eventTypeId, UUID... behaviorGroupIds) {
        behaviorGroupRepository.updateEventTypeBehaviors(accountId, eventTypeId, Set.of(behaviorGroupIds));
        entityManager.clear(); // We need to clear the session L1 cache before checking the update result.
        // If we expected a success, the event type behaviors should match in any order the given behavior groups IDs.
        List<EventTypeBehavior> behaviors = findEventTypeBehaviorByEventTypeId(eventTypeId);
        assertEquals(behaviorGroupIds.length, behaviors.size());
        for (UUID behaviorGroupId : behaviorGroupIds) {
            assertEquals(1L, behaviors.stream().filter(behavior -> behavior.getBehaviorGroup().getId().equals(behaviorGroupId)).count());
        }
    }

    private List<EventTypeBehavior> findEventTypeBehaviorByEventTypeId(UUID eventTypeId) {
        String query = "FROM EventTypeBehavior WHERE eventType.id = :eventTypeId";
        return entityManager.createQuery(query, EventTypeBehavior.class)
                .setParameter("eventTypeId", eventTypeId)
                .getResultList();
    }

    @Transactional
    void updateAndCheckBehaviorGroupActions(String accountId, UUID bundleId, UUID behaviorGroupId, UUID... endpointIds) {
        behaviorGroupRepository.updateBehaviorGroupActions(accountId, behaviorGroupId, Arrays.asList(endpointIds));
        entityManager.clear(); // We need to clear the session L1 cache before checking the update result.
        // If we expected a success, the behavior group actions should match exactly the given endpoint IDs.
        List<BehaviorGroupAction> actions = findBehaviorGroupActions(accountId, bundleId, behaviorGroupId);
        assertEquals(endpointIds.length, actions.size());
        for (int i = 0; i < endpointIds.length; i++) {
            assertEquals(endpointIds[i], actions.get(i).getEndpoint().getId());
        }
    }

    private List<BehaviorGroupAction> findBehaviorGroupActions(String accountId, UUID bundleId, UUID behaviorGroupId) {
        return behaviorGroupRepository.findByBundleId(accountId, bundleId)
                .stream().filter(behaviorGroup -> behaviorGroup.getId().equals(behaviorGroupId))
                .findFirst().get().getActions();
    }

    private void findBehaviorGroupsByEndpointId(UUID endpointId, UUID... expectedBehaviorGroupIds) {
        List<BehaviorGroup> behaviorGroups = resourceHelpers.findBehaviorGroupsByEndpointId(endpointId);
        List<UUID> actualBehaviorGroupIds = behaviorGroups.stream().map(BehaviorGroup::getId).collect(Collectors.toList());
        assertEquals(expectedBehaviorGroupIds.length, actualBehaviorGroupIds.size());
        assertTrue(actualBehaviorGroupIds.containsAll(Arrays.asList(expectedBehaviorGroupIds)));
    }
}
