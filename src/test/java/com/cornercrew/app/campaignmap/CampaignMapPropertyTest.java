package com.cornercrew.app.campaignmap;

import com.cornercrew.app.campaign.*;
import com.cornercrew.app.intersection.Intersection;
import com.cornercrew.app.intersection.IntersectionRepository;
import com.cornercrew.app.intersection.IntersectionStatus;
import com.cornercrew.app.intersection.IntersectionType;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Property tests for the CampaignMap module.
 *
 * <p><b>Validates: Requirements 7.2, 7.4, 7.5, 7.6</b></p>
 */
class CampaignMapPropertyTest {

    private static final double EARTH_RADIUS_KM = 6371.0;

    // ---- Helpers ----

    private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private Campaign makeCampaign(long id, String title, BigDecimal targetAmount, BigDecimal currentAmount) {
        Campaign c = new Campaign();
        c.setId(id);
        c.setTitle(title);
        c.setDescription("Test campaign");
        c.setTargetAmount(targetAmount);
        c.setCurrentAmount(currentAmount);
        c.setStatus(CampaignStatus.OPEN);
        c.setWindowStart(LocalDate.now().plusDays(1));
        c.setWindowEnd(LocalDate.now().plusDays(30));
        c.setCreatedByAdminId(1L);
        c.setCreatedAt(OffsetDateTime.now());
        return c;
    }

    private Intersection makeIntersection(long id, String label, double lat, double lng) {
        Intersection i = new Intersection();
        i.setId(id);
        i.setLabel(label);
        i.setLatitude(lat);
        i.setLongitude(lng);
        i.setType(IntersectionType.FOUR_WAY_STOP);
        i.setStatus(IntersectionStatus.CONFIRMED);
        i.setCongestionScore(0.5);
        return i;
    }

    private CampaignIntersection makeCampaignIntersection(long id, long campaignId, long intersectionId) {
        CampaignIntersection ci = new CampaignIntersection();
        ci.setId(id);
        ci.setCampaignId(campaignId);
        ci.setIntersectionId(intersectionId);
        return ci;
    }

    // ---- Property 17: Campaign Map Proximity Filter ----

    /**
     * Property 17: Campaign Map Proximity Filter
     *
     * For any center point (latitude, longitude) and radius, getCampaignsForMap
     * should return only campaigns that have at least one associated intersection
     * within the specified radius. Every campaign in the result should have at least
     * one intersection whose Haversine distance from the center is <= radius.
     * Campaigns with no intersections within radius are excluded.
     *
     * <p><b>Validates: Requirements 7.2, 7.6</b></p>
     */
    @Property(tries = 10)
    void proximityFilter_onlyReturnsCampaignsWithIntersectionsWithinRadius(
            @ForAll("centerPoints") double[] center,
            @ForAll("radii") double radiusKm,
            @ForAll("intersectionCoordLists") List<double[]> intersectionCoords
    ) {
        double centerLat = center[0];
        double centerLng = center[1];

        // Build campaigns: one campaign per intersection coordinate
        AtomicLong idGen = new AtomicLong(1);
        List<Campaign> campaigns = new ArrayList<>();
        Map<Long, List<CampaignIntersection>> ciMap = new HashMap<>();
        Map<Long, Intersection> intersectionMap = new HashMap<>();

        for (double[] coord : intersectionCoords) {
            long campaignId = idGen.getAndIncrement();
            long intersectionId = idGen.getAndIncrement();
            long ciId = idGen.getAndIncrement();

            Campaign campaign = makeCampaign(campaignId, "Campaign " + campaignId,
                    new BigDecimal("1000.00"), new BigDecimal("500.00"));
            campaigns.add(campaign);

            Intersection intersection = makeIntersection(intersectionId,
                    "Intersection " + intersectionId, coord[0], coord[1]);
            intersectionMap.put(intersectionId, intersection);

            CampaignIntersection ci = makeCampaignIntersection(ciId, campaignId, intersectionId);
            ciMap.put(campaignId, List.of(ci));
        }

        // Mock repositories
        CampaignRepository campaignRepo = mock(CampaignRepository.class);
        CampaignIntersectionRepository ciRepo = mock(CampaignIntersectionRepository.class);
        IntersectionRepository intersectionRepo = mock(IntersectionRepository.class);

        when(campaignRepo.findByStatusIn(any())).thenReturn(campaigns);
        for (var entry : ciMap.entrySet()) {
            when(ciRepo.findByCampaignId(entry.getKey())).thenReturn(entry.getValue());
        }
        for (var entry : intersectionMap.entrySet()) {
            when(intersectionRepo.findById(entry.getKey())).thenReturn(Optional.of(entry.getValue()));
        }

        CampaignMapServiceImpl service = new CampaignMapServiceImpl(campaignRepo, ciRepo, intersectionRepo);

        List<CampaignMapDto> result = service.getCampaignsForMap(
                List.of(CampaignStatus.OPEN), centerLat, centerLng, radiusKm);

        // Verify: every returned campaign has at least one intersection within radius
        for (CampaignMapDto dto : result) {
            boolean hasIntersectionWithinRadius = dto.intersections().stream()
                    .anyMatch(i -> haversineKm(centerLat, centerLng, i.latitude(), i.longitude()) <= radiusKm);
            assertThat(hasIntersectionWithinRadius)
                    .as("Campaign %d should have at least one intersection within %.2f km",
                            dto.campaignId(), radiusKm)
                    .isTrue();
        }

        // Verify: campaigns with no intersections within radius are excluded
        Set<Long> returnedIds = new HashSet<>();
        for (CampaignMapDto dto : result) {
            returnedIds.add(dto.campaignId());
        }

        for (Campaign campaign : campaigns) {
            List<CampaignIntersection> cis = ciMap.getOrDefault(campaign.getId(), List.of());
            boolean anyWithinRadius = cis.stream().anyMatch(ci -> {
                Intersection inter = intersectionMap.get(ci.getIntersectionId());
                return inter != null && haversineKm(centerLat, centerLng,
                        inter.getLatitude(), inter.getLongitude()) <= radiusKm;
            });
            if (!anyWithinRadius) {
                assertThat(returnedIds)
                        .as("Campaign %d has no intersections within radius and should be excluded",
                                campaign.getId())
                        .doesNotContain(campaign.getId());
            }
        }
    }

    // ---- Property 18: Funding Percentage Computation ----

    /**
     * Property 18: Funding Percentage Computation
     *
     * For any campaign with targetAmount > 0 and currentAmount in [0, targetAmount],
     * the fundingPercentage should equal round((currentAmount / targetAmount) * 100, 1 decimal).
     * The result should be in [0.0, 100.0].
     *
     * <p><b>Validates: Requirements 7.5</b></p>
     */
    @Property(tries = 10)
    void fundingPercentage_matchesExpectedComputation(
            @ForAll("targetAmounts") BigDecimal targetAmount,
            @ForAll("fractions") double fraction
    ) {
        BigDecimal currentAmount = targetAmount.multiply(BigDecimal.valueOf(fraction))
                .setScale(2, RoundingMode.HALF_UP);

        // Ensure currentAmount does not exceed targetAmount
        if (currentAmount.compareTo(targetAmount) > 0) {
            currentAmount = targetAmount;
        }

        long campaignId = 1L;
        long intersectionId = 2L;
        long ciId = 3L;

        Campaign campaign = makeCampaign(campaignId, "Funding Test", targetAmount, currentAmount);
        Intersection intersection = makeIntersection(intersectionId, "Test Intersection", 40.0, -74.0);
        CampaignIntersection ci = makeCampaignIntersection(ciId, campaignId, intersectionId);

        // Mock repositories
        CampaignRepository campaignRepo = mock(CampaignRepository.class);
        CampaignIntersectionRepository ciRepo = mock(CampaignIntersectionRepository.class);
        IntersectionRepository intersectionRepo = mock(IntersectionRepository.class);

        when(campaignRepo.findByStatusIn(any())).thenReturn(List.of(campaign));
        when(ciRepo.findByCampaignId(campaignId)).thenReturn(List.of(ci));
        when(intersectionRepo.findById(intersectionId)).thenReturn(Optional.of(intersection));

        CampaignMapServiceImpl service = new CampaignMapServiceImpl(campaignRepo, ciRepo, intersectionRepo);

        // Call without proximity filter so the campaign is always returned
        List<CampaignMapDto> result = service.getCampaignsForMap(
                List.of(CampaignStatus.OPEN), null, null, null);

        assertThat(result).hasSize(1);
        CampaignMapDto dto = result.get(0);

        // Expected: round((currentAmount / targetAmount) * 100, 1 decimal)
        double expected = currentAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(targetAmount, 1, RoundingMode.HALF_UP)
                .doubleValue();

        assertThat(dto.fundingPercentage())
                .as("fundingPercentage should equal round((currentAmount/targetAmount)*100, 1)")
                .isEqualTo(expected);

        assertThat(dto.fundingPercentage())
                .as("fundingPercentage should be in [0.0, 100.0]")
                .isBetween(0.0, 100.0);
    }

    // ---- Property 21: Campaign Map Response Completeness ----

    /**
     * Property 21: Campaign Map Response Completeness
     *
     * For any campaign returned by the map endpoint, the response should include
     * non-null campaignId, title, status, targetAmount, currentAmount, and a non-empty
     * list of intersections. Every IntersectionMapDto should have non-null id, label,
     * latitude, and longitude.
     *
     * <p><b>Validates: Requirements 7.4</b></p>
     */
    @Property(tries = 10)
    void responseCompleteness_allFieldsPresent(
            @ForAll("campaignData") CampaignTestData data
    ) {
        long campaignId = 1L;
        Campaign campaign = makeCampaign(campaignId, data.title, data.targetAmount, data.currentAmount);

        List<CampaignIntersection> ciList = new ArrayList<>();
        Map<Long, Intersection> intersectionMap = new HashMap<>();
        long idGen = 100L;

        for (IntersectionTestData iData : data.intersections) {
            long intersectionId = idGen++;
            long ciId = idGen++;
            Intersection intersection = makeIntersection(intersectionId, iData.label, iData.lat, iData.lng);
            intersectionMap.put(intersectionId, intersection);
            ciList.add(makeCampaignIntersection(ciId, campaignId, intersectionId));
        }

        // Mock repositories
        CampaignRepository campaignRepo = mock(CampaignRepository.class);
        CampaignIntersectionRepository ciRepo = mock(CampaignIntersectionRepository.class);
        IntersectionRepository intersectionRepo = mock(IntersectionRepository.class);

        when(campaignRepo.findByStatusIn(any())).thenReturn(List.of(campaign));
        when(ciRepo.findByCampaignId(campaignId)).thenReturn(ciList);
        for (var entry : intersectionMap.entrySet()) {
            when(intersectionRepo.findById(entry.getKey())).thenReturn(Optional.of(entry.getValue()));
        }

        CampaignMapServiceImpl service = new CampaignMapServiceImpl(campaignRepo, ciRepo, intersectionRepo);

        List<CampaignMapDto> result = service.getCampaignsForMap(
                List.of(CampaignStatus.OPEN), null, null, null);

        assertThat(result).hasSize(1);
        CampaignMapDto dto = result.get(0);

        // Verify non-null required fields
        assertThat(dto.campaignId()).as("campaignId").isNotNull();
        assertThat(dto.title()).as("title").isNotNull();
        assertThat(dto.status()).as("status").isNotNull();
        assertThat(dto.targetAmount()).as("targetAmount").isNotNull();
        assertThat(dto.currentAmount()).as("currentAmount").isNotNull();

        // Verify non-empty intersections list
        assertThat(dto.intersections())
                .as("intersections list should be non-empty")
                .isNotEmpty();

        // Verify each intersection has required fields
        for (IntersectionMapDto iDto : dto.intersections()) {
            assertThat(iDto.id()).as("intersection id").isNotNull();
            assertThat(iDto.label()).as("intersection label").isNotNull();
            // latitude and longitude are primitives (double), always non-null
            // but verify they are valid numbers
            assertThat(Double.isNaN(iDto.latitude())).as("latitude should not be NaN").isFalse();
            assertThat(Double.isNaN(iDto.longitude())).as("longitude should not be NaN").isFalse();
        }
    }

    // ---- Providers ----

    @Provide
    Arbitrary<double[]> centerPoints() {
        Arbitrary<Double> lats = Arbitraries.doubles().between(-89.0, 89.0);
        Arbitrary<Double> lngs = Arbitraries.doubles().between(-179.0, 179.0);
        return Combinators.combine(lats, lngs).as((lat, lng) -> new double[]{lat, lng});
    }

    @Provide
    Arbitrary<Double> radii() {
        return Arbitraries.doubles().between(1.0, 50.0);
    }

    @Provide
    Arbitrary<List<double[]>> intersectionCoordLists() {
        Arbitrary<double[]> coord = Combinators.combine(
                Arbitraries.doubles().between(-89.0, 89.0),
                Arbitraries.doubles().between(-179.0, 179.0)
        ).as((lat, lng) -> new double[]{lat, lng});
        return coord.list().ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<BigDecimal> targetAmounts() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("1.00"), new BigDecimal("100000.00"))
                .ofScale(2);
    }

    @Provide
    Arbitrary<Double> fractions() {
        return Arbitraries.doubles().between(0.0, 1.0);
    }

    @Provide
    Arbitrary<CampaignTestData> campaignData() {
        Arbitrary<String> titles = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20);
        Arbitrary<BigDecimal> targets = Arbitraries.bigDecimals()
                .between(new BigDecimal("1.00"), new BigDecimal("100000.00"))
                .ofScale(2);
        Arbitrary<List<IntersectionTestData>> intersections = intersectionTestDataList();

        return Combinators.combine(titles, targets, intersections).as((title, target, inters) -> {
            // currentAmount in [0, targetAmount]
            BigDecimal current = target.multiply(BigDecimal.valueOf(0.5)).setScale(2, RoundingMode.HALF_UP);
            return new CampaignTestData(title, target, current, inters);
        });
    }

    private Arbitrary<List<IntersectionTestData>> intersectionTestDataList() {
        Arbitrary<IntersectionTestData> single = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15),
                Arbitraries.doubles().between(-89.0, 89.0),
                Arbitraries.doubles().between(-179.0, 179.0)
        ).as(IntersectionTestData::new);
        return single.list().ofMinSize(1).ofMaxSize(5);
    }

    // ---- Test data records ----

    record CampaignTestData(String title, BigDecimal targetAmount, BigDecimal currentAmount,
                            List<IntersectionTestData> intersections) {}

    record IntersectionTestData(String label, double lat, double lng) {}
}
