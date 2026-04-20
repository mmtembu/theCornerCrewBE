package com.cornercrew.app.assignment;

import java.util.List;

public interface ApplicationService {

    ApplicationDto apply(Long campaignId, Long controllerId, ApplyRequest req);

    List<ApplicationDto> listApplications(Long campaignId);

    ApplicationDto updateStatus(Long applicationId, ApplicationStatus status);
}
