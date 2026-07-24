package tech.sioseforge.auth.service;

import tech.sioseforge.auth.domain.view.TraceCheckResponseVO;

public interface ObservabilityService {
    TraceCheckResponseVO checkTrace();
    TraceCheckResponseVO deepTraceCheck();
}
