package tech.sioseforge.auth.service;

import org.springframework.stereotype.Service;
import tech.sioseforge.auth.domain.view.PermissionVO;

import java.util.Collections;
import java.util.List;

@Service
public class DefaultPermissionService implements PermissionService {

    @Override
    public List<PermissionVO> listPermissionsByUserId(Long userId) {
        // Return empty list as a placeholder implementation
        return Collections.emptyList();
    }
}
