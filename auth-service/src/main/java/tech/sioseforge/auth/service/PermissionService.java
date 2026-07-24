package tech.sioseforge.auth.service;

import tech.sioseforge.auth.domain.view.PermissionVO;
import java.util.List;

public interface PermissionService {
    List<PermissionVO> listPermissionsByUserId(Long userId);
}
